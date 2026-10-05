package ru.survey.service.app;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import ru.survey.service.domain.Survey;
import ru.survey.service.domain.SurveyQuestion;
import ru.survey.service.domain.SurveyStatus;
import ru.survey.service.domain.SurveyTrigger;
import ru.survey.service.repository.SurveyQuestionRepository;
import ru.survey.service.repository.SurveyRepository;
import ru.survey.service.repository.SurveyTriggerRepository;
import ru.survey.service.rules.SurveyConfigValidator;

/**
 * Проверяет активные опросы при старте (до того, как сервис начнет принимать трафик) и затем периодически (survey.config-check-interval).
 * Опросы с ошибками конфигурации не показываются клиентам, ошибки пишутся в лог,
 * число таких опросов видно в метрике survey.config.invalid.
 */
@Component
public class SurveyConfigRegistry {

    private static final Logger log = LoggerFactory.getLogger(SurveyConfigRegistry.class);

    private final SurveyRepository surveys;
    private final SurveyQuestionRepository questions;
    private final SurveyTriggerRepository triggers;
    private final SurveyConfigValidator validator;
    private volatile Set<Long> invalidSurveyIds = Set.of();
    private volatile Set<Long> reportedSurveyIds = Set.of();

    public SurveyConfigRegistry(SurveyRepository surveys, SurveyQuestionRepository questions,
            SurveyTriggerRepository triggers, SurveyConfigValidator validator, MeterRegistry meters) {
        this.surveys = surveys;
        this.questions = questions;
        this.triggers = triggers;
        this.validator = validator;
        Gauge.builder("survey.config.invalid", () -> invalidSurveyIds.size())
                .description("Активные опросы с ошибками конфигурации, они не показываются")
                .register(meters);
    }

    public boolean isInvalid(Long surveyId) {
        return invalidSurveyIds.contains(surveyId);
    }

    @EventListener(ApplicationStartedEvent.class)
    @Scheduled(fixedDelayString = "${survey.config-check-interval:PT5M}", initialDelayString = "${survey.config-check-interval:PT5M}")
    @Transactional(readOnly = true)
    public void refresh() {
        List<Survey> active = surveys.findAll().stream()
                .filter(survey -> survey.getStatus() == SurveyStatus.ACTIVE)
                .toList();
        Map<Long, List<SurveyQuestion>> questionsBySurvey = questions.findAll().stream()
                .collect(Collectors.groupingBy(SurveyQuestion::getSurveyId));
        Map<Long, List<SurveyTrigger>> triggersBySurvey = triggers.findAll().stream()
                .collect(Collectors.groupingBy(SurveyTrigger::getSurveyId));

        Set<Long> invalid = new HashSet<>();
        for (Survey survey : active) {
            List<String> errors = validator.validate(survey,
                    questionsBySurvey.getOrDefault(survey.getId(), List.of()),
                    triggersBySurvey.getOrDefault(survey.getId(), List.of()));
            if (!errors.isEmpty()) {
                invalid.add(survey.getId());
                if (!reportedSurveyIds.contains(survey.getId())) {
                    log.error("Survey {} ({}) is not shown, configuration errors: {}",
                            survey.getCode(), survey.getId(), String.join("; ", errors));
                }
            }
        }
        invalidSurveyIds = Set.copyOf(invalid);
        reportedSurveyIds = Set.copyOf(invalid);
    }
}
