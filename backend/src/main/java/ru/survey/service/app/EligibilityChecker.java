package ru.survey.service.app;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import ru.survey.service.client.ClientAttributesProvider;
import ru.survey.service.config.SurveyProperties;
import ru.survey.service.domain.Survey;
import ru.survey.service.domain.SurveyTrigger;
import ru.survey.service.repository.SurveyImpressionRepository;
import ru.survey.service.repository.SurveyRepository;
import ru.survey.service.repository.SurveyTriggerRepository;
import ru.survey.service.rules.ConditionEvaluator;
import ru.survey.service.rules.FrequencyRule;
import ru.survey.service.rules.Sampler;

/** Правила показа (docs/survey-service.md, п. 6). */
@Component
public class EligibilityChecker {

    private static final Comparator<Survey> BY_PRIORITY = Comparator
            .comparingInt(Survey::getPriority).reversed()
            .thenComparing(Survey::getId);

    private final SurveyProperties properties;
    private final SurveyRepository surveys;
    private final SurveyTriggerRepository triggers;
    private final SurveyImpressionRepository impressions;
    private final ClientAttributesProvider attributesProvider;
    private final ConditionEvaluator conditionEvaluator;
    private final FrequencyRule frequencyRule;
    private final Sampler sampler;
    private final SurveyConfigRegistry configRegistry;

    public EligibilityChecker(SurveyProperties properties, SurveyRepository surveys, SurveyTriggerRepository triggers,
            SurveyImpressionRepository impressions, ClientAttributesProvider attributesProvider,
            ConditionEvaluator conditionEvaluator, FrequencyRule frequencyRule, Sampler sampler,
            SurveyConfigRegistry configRegistry) {
        this.properties = properties;
        this.surveys = surveys;
        this.triggers = triggers;
        this.impressions = impressions;
        this.attributesProvider = attributesProvider;
        this.conditionEvaluator = conditionEvaluator;
        this.frequencyRule = frequencyRule;
        this.sampler = sampler;
        this.configRegistry = configRegistry;
    }

    /**
     * Выбирает опрос для шага флоу (п. 7.1): из подходящих возвращает один, с наибольшим приоритетом,
     * при равном приоритете — более старый. Доля показа разыгрывается здесь, при фиксации показа повторно не проверяется.
     */
    public Optional<Survey> findSurveyFor(String clientId, String flowStep, Instant now) {
        if (!properties.enabled() || globalLimitReached(clientId, now)) {
            return Optional.empty();
        }
        Supplier<Map<String, Object>> attributes = lazyAttributes(clientId);
        return triggers.findByFlowStepCode(flowStep).stream()
                .filter(trigger -> sampler.isSampled(trigger.getShowPercent()))
                .map(trigger -> surveyIfEligible(trigger, clientId, attributes, now))
                .flatMap(Optional::stream)
                .min(BY_PRIORITY);
    }

    /** Повторная проверка правил при фиксации показа (п. 7.2), без доли показа. */
    public boolean canShow(String clientId, Long surveyId, String flowStep, Instant now) {
        if (!properties.enabled() || globalLimitReached(clientId, now)) {
            return false;
        }
        Supplier<Map<String, Object>> attributes = lazyAttributes(clientId);
        return triggers.findBySurveyIdAndFlowStepCode(surveyId, flowStep).stream()
                .anyMatch(trigger -> surveyIfEligible(trigger, clientId, attributes, now).isPresent());
    }

    private Optional<Survey> surveyIfEligible(SurveyTrigger trigger, String clientId,
            Supplier<Map<String, Object>> attributes, Instant now) {
        return surveys.findById(trigger.getSurveyId())
                .filter(survey -> survey.isActiveAt(now))
                .filter(survey -> !configRegistry.isInvalid(survey.getId()))
                .filter(survey -> !ConditionEvaluator.hasConditions(trigger.getConditions())
                        || conditionEvaluator.matches(trigger.getConditions(), attributes.get()))
                .filter(survey -> frequencyRule.allows(
                        impressions.findLastShownAt(clientId, survey.getId()).orElse(null),
                        survey.getMinIntervalDays(), now));
    }

    private boolean globalLimitReached(String clientId, Instant now) {
        int limit = properties.globalDailyLimit();
        return limit > 0 && impressions.countByClientIdAndCreatedAtGreaterThanEqual(
                clientId, frequencyRule.startOfBusinessDay(now)) >= limit;
    }

    /** Атрибуты клиента запрашиваются один раз и только если у триггеров есть условия. */
    private Supplier<Map<String, Object>> lazyAttributes(String clientId) {
        return new Supplier<>() {
            private Map<String, Object> cached;

            @Override
            public Map<String, Object> get() {
                if (cached == null) {
                    cached = attributesProvider.getAttributes(clientId);
                }
                return cached;
            }
        };
    }
}
