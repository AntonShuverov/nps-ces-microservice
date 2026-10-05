package ru.survey.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.survey.service.domain.QuestionType;
import ru.survey.service.domain.Survey;
import ru.survey.service.domain.SurveyQuestion;
import ru.survey.service.domain.SurveyTrigger;

class SurveyConfigValidatorTest {

    private final SurveyConfigValidator validator = new SurveyConfigValidator();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void validCesNpsSurveyHasNoErrors() throws Exception {
        List<String> errors = validator.validate(survey(), List.of(
                question("ces", 1, QuestionType.SCALE, "{\"min\": 1, \"max\": 7}"),
                question("nps", 2, QuestionType.SCALE, "{\"min\": 0, \"max\": 10}"),
                question("nps_comment", 2, QuestionType.TEXT, "{\"maxLength\": 1000}"),
                question("reason", 2, QuestionType.SINGLE_CHOICE,
                        "{\"options\": [{\"code\": \"speed\", \"label\": \"Скорость\"}]}")),
                List.of(trigger("""
                        [{"attribute": "client_type", "op": "eq", "value": "repeat"},
                         {"attribute": "product", "op": "in", "value": ["PDL", "IL"]}]""")));
        assertThat(errors).isEmpty();
    }

    @Test
    void reportsMissingQuestionsAndTriggers() {
        assertThat(validator.validate(survey(), List.of(), List.of()))
                .containsExactly("нет вопросов", "нет триггеров");
    }

    @Test
    void reportsGapInSteps() throws Exception {
        assertThat(validator.validate(survey(),
                List.of(question("ces", 1, QuestionType.SCALE, "{\"min\": 1, \"max\": 7}"),
                        question("nps", 3, QuestionType.SCALE, "{\"min\": 0, \"max\": 10}")),
                List.of(trigger("[]"))))
                .containsExactly("шаги должны идти по порядку с 1, пропущен шаг 2");
    }

    @Test
    void reportsBrokenQuestionSettings() throws Exception {
        assertThat(validator.validate(survey(), List.of(
                question("a", 1, QuestionType.SCALE, "{\"min\": 7, \"max\": 1}"),
                question("b", 1, QuestionType.SCALE, "{}"),
                question("c", 1, QuestionType.SINGLE_CHOICE, "{\"options\": []}"),
                question("d", 1, QuestionType.MULTIPLE_CHOICE,
                        "{\"options\": [{\"code\": \"x\", \"label\": \"X\"}, {\"code\": \"x\", \"label\": \"Y\"}]}"),
                question("e", 1, QuestionType.STARS, "{\"count\": 50}")),
                List.of(trigger("[]"))))
                .containsExactly(
                        "вопрос a: min шкалы должен быть меньше max",
                        "вопрос b: для шкалы нужны целые min и max",
                        "вопрос c: нет вариантов ответа",
                        "вопрос d: повторяется код варианта x",
                        "вопрос e: count звезд должен быть от 2 до 10");
    }

    @Test
    void reportsBrokenConditions() throws Exception {
        assertThat(validator.validate(survey(),
                List.of(question("ces", 1, QuestionType.SCALE, "{\"min\": 1, \"max\": 7}")),
                List.of(trigger("""
                        [{"attribute": "client_type", "op": "equals", "value": "repeat"},
                         {"attribute": "product", "op": "in", "value": "PDL"},
                         {"op": "eq", "value": 1}]"""))))
                .containsExactly(
                        "триггер loan_issued: неизвестный оператор 'equals' для client_type",
                        "триггер loan_issued: для оператора in нужен список значений (product)",
                        "триггер loan_issued: в условии нет attribute");
    }

    @Test
    void reportsInvalidDateRange() throws Exception {
        Survey survey = survey();
        ReflectionTestUtils.setField(survey, "startsAt", Instant.parse("2026-10-10T00:00:00Z"));
        ReflectionTestUtils.setField(survey, "endsAt", Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(validator.validate(survey,
                List.of(question("ces", 1, QuestionType.SCALE, "{\"min\": 1, \"max\": 7}")),
                List.of(trigger("[]"))))
                .containsExactly("дата начала не раньше даты окончания");
    }

    @Test
    void validatesEmojiScaleAndConditions() throws Exception {
        List<SurveyQuestion> valid = List.of(
                question("ces", 1, 1, QuestionType.SCALE, "{\"min\": 1, \"max\": 5, \"view\": \"emoji\"}"),
                question("thanks", 1, 2, QuestionType.TEXT, "{\"showIf\": {\"question\": \"ces\", \"op\": \"gte\", \"value\": 4}}"),
                question("comment", 1, 3, QuestionType.TEXT, "{\"showIf\": {\"question\": \"ces\", \"op\": \"lte\", \"value\": 3}}"));
        assertThat(validator.validate(survey(), valid, List.of(trigger("[]")))).isEmpty();

        assertThat(validator.validate(survey(), List.of(
                question("ces", 1, 1, QuestionType.SCALE, "{\"min\": 1, \"max\": 7, \"view\": \"emoji\"}"),
                question("a", 1, 2, QuestionType.TEXT, "{\"showIf\": {\"question\": \"unknown\", \"op\": \"lte\", \"value\": 3}}"),
                question("b", 1, 3, QuestionType.TEXT, "{\"showIf\": {\"question\": \"a\", \"op\": \"lte\", \"value\": 3}}"),
                question("c", 1, 4, QuestionType.TEXT, "{\"showIf\": {\"question\": \"ces\", \"op\": \"like\", \"value\": 3}}"),
                question("d", 1, 0, QuestionType.TEXT, "{\"showIf\": {\"question\": \"ces\", \"op\": \"lte\", \"value\": 3}}")),
                List.of(trigger("[]"))))
                .containsExactly(
                        "вопрос ces: для шкалы смайликами не из 5 значений нужен список icons",
                        "вопрос a: showIf ссылается на неизвестный вопрос 'unknown'",
                        "вопрос b: showIf работает только по шкале или звездам",
                        "вопрос c: неизвестный оператор showIf 'like'",
                        "вопрос d: showIf должен ссылаться на вопрос выше этого");
    }

    private Survey survey() {
        Survey survey = BeanUtils.instantiateClass(Survey.class);
        ReflectionTestUtils.setField(survey, "id", 1L);
        ReflectionTestUtils.setField(survey, "code", "test");
        return survey;
    }

    private SurveyQuestion question(String code, int step, QuestionType type, String settings) throws Exception {
        return question(code, step, 1, type, settings);
    }

    private SurveyQuestion question(String code, int step, int position, QuestionType type, String settings)
            throws Exception {
        SurveyQuestion question = BeanUtils.instantiateClass(SurveyQuestion.class);
        ReflectionTestUtils.setField(question, "code", code);
        ReflectionTestUtils.setField(question, "step", step);
        ReflectionTestUtils.setField(question, "position", position);
        ReflectionTestUtils.setField(question, "type", type);
        ReflectionTestUtils.setField(question, "text", "Вопрос");
        ReflectionTestUtils.setField(question, "settings", json.readTree(settings));
        return question;
    }

    private SurveyTrigger trigger(String conditions) throws Exception {
        SurveyTrigger trigger = BeanUtils.instantiateClass(SurveyTrigger.class);
        ReflectionTestUtils.setField(trigger, "flowStepCode", "loan_issued");
        ReflectionTestUtils.setField(trigger, "conditions", json.readTree(conditions));
        return trigger;
    }
}
