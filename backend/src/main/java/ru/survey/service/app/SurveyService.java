package ru.survey.service.app;

import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;
import ru.survey.service.api.ActiveSurveyResponse;
import ru.survey.service.api.SaveStepRequest;
import ru.survey.service.api.StepResultResponse;
import ru.survey.service.domain.AnswerValue;
import ru.survey.service.domain.ImpressionStatus;
import ru.survey.service.domain.Survey;
import ru.survey.service.domain.SurveyAnswer;
import ru.survey.service.domain.SurveyImpression;
import ru.survey.service.domain.SurveyQuestion;
import ru.survey.service.repository.ClientLockRepository;
import ru.survey.service.repository.SurveyAnswerRepository;
import ru.survey.service.repository.SurveyImpressionRepository;
import ru.survey.service.repository.SurveyQuestionRepository;
import ru.survey.service.rules.AnswerValidator;
import ru.survey.service.rules.AnswerValidator.InvalidAnswerException;
import ru.survey.service.rules.VisibilityRule;

/** Операции API (docs/survey-service.md, п. 7). В логи пишутся только ID, без текстов ответов. */
@Service
public class SurveyService {

    private static final Logger log = LoggerFactory.getLogger(SurveyService.class);

    private final EligibilityChecker eligibility;
    private final SurveyQuestionRepository questions;
    private final SurveyImpressionRepository impressions;
    private final SurveyAnswerRepository answers;
    private final ClientLockRepository clientLock;
    private final AnswerValidator answerValidator;
    private final VisibilityRule visibilityRule;
    private final MeterRegistry meters;
    private final Clock clock;

    public SurveyService(EligibilityChecker eligibility, SurveyQuestionRepository questions,
            SurveyImpressionRepository impressions, SurveyAnswerRepository answers, ClientLockRepository clientLock,
            AnswerValidator answerValidator, VisibilityRule visibilityRule, MeterRegistry meters, Clock clock) {
        this.eligibility = eligibility;
        this.questions = questions;
        this.impressions = impressions;
        this.answers = answers;
        this.clientLock = clientLock;
        this.answerValidator = answerValidator;
        this.visibilityRule = visibilityRule;
        this.meters = meters;
        this.clock = clock;
    }

    /** 7.1. Только читает данные, показ не создает. */
    @Transactional(readOnly = true)
    public Optional<ActiveSurveyResponse> findActiveSurvey(String clientId, String flowStep) {
        Optional<ActiveSurveyResponse> result = eligibility.findSurveyFor(clientId, flowStep, clock.instant())
                .map(this::toResponse);
        meters.counter("survey.active.requests", "found", String.valueOf(result.isPresent())).increment();
        return result;
    }

    /** 7.2. Повторно проверяет правила показа под блокировкой клиента. */
    @Transactional
    public UUID recordImpression(String clientId, Long surveyId, String flowStep, String eventObjectId) {
        clientLock.lockClient(clientId);
        Instant now = clock.instant();
        if (!eligibility.canShow(clientId, surveyId, flowStep, now)) {
            meters.counter("survey.impressions", "result", "rejected").increment();
            throw new ConflictException("NOT_ELIGIBLE", "Опрос сейчас нельзя показать");
        }
        SurveyImpression impression = impressions.save(
                new SurveyImpression(surveyId, clientId, flowStep, eventObjectId, now));
        meters.counter("survey.impressions", "result", "created").increment();
        log.info("Impression {} created: survey={}, client={}, flowStep={}",
                impression.getId(), surveyId, clientId, flowStep);
        return impression.getId();
    }

    /** 7.3. Повторная отправка шага перезаписывает ответы. Последний шаг завершает показ. */
    @Transactional
    public StepResultResponse saveStep(String clientId, UUID impressionId, int step, List<SaveStepRequest.Answer> input) {
        SurveyImpression impression = findOwnImpression(clientId, impressionId);
        if (!impression.isOpen()) {
            throw new ConflictException("IMPRESSION_FINISHED", "Опрос уже закрыт или завершен");
        }
        List<SurveyQuestion> surveyQuestions = questions.findBySurveyIdOrderByStepAscPositionAsc(impression.getSurveyId());
        Map<Long, SurveyQuestion> stepQuestions = surveyQuestions.stream()
                .filter(q -> q.getStep() == step)
                .collect(Collectors.toMap(SurveyQuestion::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        if (stepQuestions.isEmpty()) {
            throw new AnswersValidationException(Map.of("step", "В опросе нет шага " + step));
        }

        Map<Long, SurveyAnswer> existing = answers.findByImpressionId(impressionId).stream()
                .collect(Collectors.toMap(SurveyAnswer::getQuestionId, Function.identity()));
        Map<Long, AnswerValue> values = validateAnswers(surveyQuestions, stepQuestions, existing, input);

        Instant now = clock.instant();
        for (SurveyQuestion question : stepQuestions.values()) {
            AnswerValue value = values.get(question.getId());
            SurveyAnswer answer = existing.get(question.getId());
            if (value == null) {
                if (answer != null) {
                    answers.delete(answer);
                }
                continue;
            }
            if (answer == null) {
                answer = new SurveyAnswer(impression, question.getId(), now);
            }
            answer.setValue(value, now);
            answers.save(answer);
        }

        int lastStepOfSurvey = surveyQuestions.stream().mapToInt(SurveyQuestion::getStep).max().orElse(step);
        impression.stepAnswered(step, step == lastStepOfSurvey, now);
        meters.counter("survey.steps.answered").increment();
        if (impression.getStatus() == ImpressionStatus.COMPLETED) {
            meters.counter("survey.impressions.completed").increment();
        }
        return new StepResultResponse(impression.getStatus(), impression.getLastStep(),
                impression.getStatus() == ImpressionStatus.COMPLETED);
    }

    /** 7.4. Закрытие крестиком. Для завершенного или уже закрытого показа ничего не меняет. */
    @Transactional
    public void close(String clientId, UUID impressionId, int step) {
        SurveyImpression impression = findOwnImpression(clientId, impressionId);
        if (!impression.isOpen()) {
            return;
        }
        impression.close(step, clock.instant());
        meters.counter("survey.impressions.closed").increment();
    }

    /**
     * Проверяет ответы шага. Вопросы, скрытые условием showIf (например, комментарий при высокой оценке),
     * не сохраняются и не обязательны, даже если фронт прислал на них ответ.
     */
    private Map<Long, AnswerValue> validateAnswers(List<SurveyQuestion> surveyQuestions,
            Map<Long, SurveyQuestion> stepQuestions, Map<Long, SurveyAnswer> existing,
            List<SaveStepRequest.Answer> input) {
        Map<String, String> errors = new TreeMap<>();
        Map<Long, AnswerValue> values = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (SaveStepRequest.Answer answer : input) {
            SurveyQuestion question = stepQuestions.get(answer.questionId());
            String key = String.valueOf(answer.questionId());
            if (question == null) {
                errors.put(key, "Вопрос не относится к этому шагу опроса");
                continue;
            }
            if (!seen.add(question.getId())) {
                errors.put(key, "Повторный ответ на вопрос");
                continue;
            }
            try {
                answerValidator.validate(question, answer.value()).ifPresent(v -> values.put(question.getId(), v));
            } catch (InvalidAnswerException e) {
                errors.put(key, e.getMessage());
            }
        }

        Map<String, BigDecimal> numericAnswers = new HashMap<>();
        for (SurveyQuestion question : surveyQuestions) {
            BigDecimal number = stepQuestions.containsKey(question.getId())
                    ? Optional.ofNullable(values.get(question.getId())).map(AnswerValue::number).orElse(null)
                    : Optional.ofNullable(existing.get(question.getId())).map(SurveyAnswer::getValueNumber).orElse(null);
            if (number != null) {
                numericAnswers.put(question.getCode(), number);
            }
        }

        for (SurveyQuestion question : stepQuestions.values()) {
            String key = String.valueOf(question.getId());
            if (!visibilityRule.isVisible(question, numericAnswers)) {
                values.remove(question.getId());
                errors.remove(key);
                continue;
            }
            if (question.isRequired() && !values.containsKey(question.getId()) && !errors.containsKey(key)) {
                errors.put(key, "Обязательный вопрос");
            }
        }
        if (!errors.isEmpty()) {
            throw new AnswersValidationException(errors);
        }
        return values;
    }

    private SurveyImpression findOwnImpression(String clientId, UUID impressionId) {
        return impressions.findByIdAndClientId(impressionId, clientId)
                .orElseThrow(() -> new NotFoundException("Показ не найден"));
    }

    private ActiveSurveyResponse toResponse(Survey survey) {
        Map<Integer, List<ActiveSurveyResponse.Question>> byStep = new TreeMap<>();
        for (SurveyQuestion q : questions.findBySurveyIdOrderByStepAscPositionAsc(survey.getId())) {
            byStep.computeIfAbsent(q.getStep(), s -> new ArrayList<>())
                    .add(new ActiveSurveyResponse.Question(q.getId(), q.getCode(), q.getType(), q.getText(),
                            q.isRequired(), q.getSettings()));
        }
        List<ActiveSurveyResponse.Step> steps = byStep.entrySet().stream()
                .map(e -> new ActiveSurveyResponse.Step(e.getKey(), e.getValue()))
                .toList();
        return new ActiveSurveyResponse(survey.getId(), survey.getCode(), survey.getTitle(), steps);
    }
}
