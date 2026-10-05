package ru.survey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.survey.service.client.ClientAttributesProvider;

class SurveyApiTest extends IntegrationTestBase {

    private static final String CLIENT = "client-1";
    private static final String OTHER_CLIENT = "client-2";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private ClientAttributesProvider attributes;

    @BeforeEach
    void noAttributesByDefault() {
        when(attributes.getAttributes(anyString())).thenReturn(Map.of());
    }

    @Test
    void requiresClientHeader() throws Exception {
        mvc.perform(get("/api/v1/surveys/active").param("flowStep", "loan_issued"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void returnsNoContentForStepWithoutSurvey() throws Exception {
        active(CLIENT, "unknown_step").andExpect(status().isNoContent());
    }

    @Test
    void returnsSurveyConfigurationWithSteps() throws Exception {
        active(CLIENT, "loan_issued")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("nps_ces_loan_issued"))
                .andExpect(jsonPath("$.title").value("Пройдите опрос"))
                .andExpect(jsonPath("$.steps", hasSize(2)))
                .andExpect(jsonPath("$.steps[0].step").value(1))
                .andExpect(jsonPath("$.steps[0].questions[0].code").value("nps"))
                .andExpect(jsonPath("$.steps[0].questions[0].type").value("SCALE"))
                .andExpect(jsonPath("$.steps[0].questions[0].settings.min").value(1))
                .andExpect(jsonPath("$.steps[0].questions[0].settings.max").value(10))
                .andExpect(jsonPath("$.steps[0].questions[1].code").value("nps_comment"))
                .andExpect(jsonPath("$.steps[0].questions[1].settings.showIf.question").value("nps"))
                .andExpect(jsonPath("$.steps[0].questions[1].settings.showIf.value").value(6))
                .andExpect(jsonPath("$.steps[1].questions[0].code").value("ces"))
                .andExpect(jsonPath("$.steps[1].questions[0].settings.view").value("emoji"))
                .andExpect(jsonPath("$.steps[1].questions[0].settings.max").value(5))
                .andExpect(jsonPath("$.steps[1].questions[1].code").value("ces_comment"));
    }

    @Test
    void fullFlowCompletesImpressionAndStoresTypedValues() throws Exception {
        JsonNode survey = activeSurvey(CLIENT, "loan_issued");
        String impressionId = createImpression(CLIENT, survey, "loan_issued", "loan-42");

        saveStep(CLIENT, impressionId, 1, Map.of(
                questionId(survey, "nps"), 5,
                questionId(survey, "nps_comment"), "Долго ждал одобрения"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHOWN"))
                .andExpect(jsonPath("$.lastStep").value(1))
                .andExpect(jsonPath("$.completed").value(false));

        saveStep(CLIENT, impressionId, 2, Map.of(questionId(survey, "ces"), 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.lastStep").value(2))
                .andExpect(jsonPath("$.completed").value(true));

        Map<String, Object> impression = jdbc.queryForMap(
                "SELECT status, last_step, event_object_id, client_id FROM survey_impression WHERE id = ?::uuid",
                impressionId);
        assertThat(impression).containsEntry("status", "COMPLETED").containsEntry("last_step", 2)
                .containsEntry("event_object_id", "loan-42").containsEntry("client_id", CLIENT);

        List<Map<String, Object>> answers = jdbc.queryForList("""
                SELECT q.code, a.value_number, a.value_text, a.client_id
                FROM survey_answer a JOIN survey_question q ON q.id = a.question_id
                WHERE a.impression_id = ?::uuid ORDER BY q.step, q.position""", impressionId);
        assertThat(answers).hasSize(3);
        assertThat(answers.get(0)).containsEntry("code", "nps");
        assertThat(answers.get(0).get("value_number").toString()).isEqualTo("5.00");
        assertThat(answers.get(1)).containsEntry("value_text", "Долго ждал одобрения").containsEntry("client_id", CLIENT);
        assertThat(answers.get(2)).containsEntry("code", "ces");
        assertThat(answers.get(2).get("value_number").toString()).isEqualTo("4.00");
    }

    @Test
    void showsOncePerDayAndAgainOnNextDayEvenAfterAnswer() throws Exception {
        JsonNode survey = activeSurvey(CLIENT, "loan_issued");
        String impressionId = createImpression(CLIENT, survey, "loan_issued", null);
        saveStep(CLIENT, impressionId, 1, Map.of(questionId(survey, "nps"), 10)).andExpect(status().isOk());
        saveStep(CLIENT, impressionId, 2, Map.of(questionId(survey, "ces"), 5)).andExpect(status().isOk());

        clock.advance(Duration.ofHours(5)); // 14:00 МСК того же дня
        active(CLIENT, "loan_issued").andExpect(status().isNoContent());
        active(OTHER_CLIENT, "loan_issued").andExpect(status().isOk());

        clock.set(java.time.Instant.parse("2026-10-05T21:00:00Z")); // 00:00 МСК следующего дня
        active(CLIENT, "loan_issued").andExpect(status().isOk());
    }

    @Test
    void differentSurveysOnSameDayAreIndependent() throws Exception {
        JsonNode ces = activeSurvey(CLIENT, "application_submitted");
        createImpression(CLIENT, ces, "application_submitted", null);
        active(CLIENT, "loan_issued").andExpect(status().isOk());
    }

    @Test
    void secondImpressionOnSameDayIsRejected() throws Exception {
        JsonNode survey = activeSurvey(CLIENT, "loan_issued");
        createImpression(CLIENT, survey, "loan_issued", null);
        postImpression(CLIENT, survey.get("surveyId").asLong(), "loan_issued")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ELIGIBLE"));
    }

    @Test
    void impressionForSurveyNotTriggeredByStepIsRejected() throws Exception {
        JsonNode survey = activeSurvey(CLIENT, "loan_issued");
        postImpression(CLIENT, survey.get("surveyId").asLong(), "loan_repaid")
                .andExpect(status().isConflict());
    }

    @Test
    void closingKeepsAnsweredStepsAndBlocksFurtherAnswers() throws Exception {
        JsonNode survey = activeSurvey(CLIENT, "loan_issued");
        String impressionId = createImpression(CLIENT, survey, "loan_issued", null);
        saveStep(CLIENT, impressionId, 1, Map.of(questionId(survey, "nps"), 8)).andExpect(status().isOk());

        close(CLIENT, impressionId, 2).andExpect(status().isNoContent());

        Map<String, Object> impression = jdbc.queryForMap(
                "SELECT status, last_step, closed_at_step FROM survey_impression WHERE id = ?::uuid", impressionId);
        assertThat(impression).containsEntry("status", "CLOSED").containsEntry("last_step", 1)
                .containsEntry("closed_at_step", 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM survey_answer WHERE impression_id = ?::uuid",
                Integer.class, impressionId)).isEqualTo(1);

        saveStep(CLIENT, impressionId, 2, Map.of(questionId(survey, "ces"), 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPRESSION_FINISHED"));
    }

    @Test
    void closingCompletedImpressionChangesNothing() throws Exception {
        JsonNode survey = activeSurvey(CLIENT, "loan_repaid");
        String impressionId = createImpression(CLIENT, survey, "loan_repaid", null);
        saveStep(CLIENT, impressionId, 1, Map.of(questionId(survey, "ces"), 4))
                .andExpect(jsonPath("$.completed").value(true));

        close(CLIENT, impressionId, 1).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT status FROM survey_impression WHERE id = ?::uuid",
                String.class, impressionId)).isEqualTo("COMPLETED");
    }

    @Test
    void resubmittingStepOverwritesAnswers() throws Exception {
        JsonNode survey = activeSurvey(CLIENT, "loan_issued");
        String impressionId = createImpression(CLIENT, survey, "loan_issued", null);
        saveStep(CLIENT, impressionId, 1, Map.of(questionId(survey, "nps"), 2)).andExpect(status().isOk());
        saveStep(CLIENT, impressionId, 1, Map.of(questionId(survey, "nps"), 6)).andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT value_number FROM survey_answer WHERE impression_id = ?::uuid",
                java.math.BigDecimal.class, impressionId))
                .singleElement().satisfies(v -> assertThat(v).isEqualByComparingTo("6"));
    }

    @Test
    void validatesAnswers() throws Exception {
        JsonNode survey = activeSurvey(CLIENT, "loan_issued");
        String impressionId = createImpression(CLIENT, survey, "loan_issued", null);
        long nps = questionId(survey, "nps");
        long ces = questionId(survey, "ces");

        saveStep(CLIENT, impressionId, 1, Map.of(nps, 11))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ANSWERS"))
                .andExpect(jsonPath("$.errors." + nps).value("Значение должно быть от 1 до 10"));

        saveStep(CLIENT, impressionId, 1, Map.of())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors." + nps).value("Обязательный вопрос"));

        saveStep(CLIENT, impressionId, 1, Map.of(nps, 5, ces, 4))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors." + ces).value("Вопрос не относится к этому шагу опроса"));

        saveStep(CLIENT, impressionId, 3, Map.of(nps, 5))
                .andExpect(status().isUnprocessableEntity());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM survey_answer", Integer.class)).isZero();
    }

    @Test
    void impressionOfAnotherClientIsNotAccessible() throws Exception {
        JsonNode survey = activeSurvey(CLIENT, "loan_issued");
        String impressionId = createImpression(CLIENT, survey, "loan_issued", null);

        saveStep(OTHER_CLIENT, impressionId, 1, Map.of(questionId(survey, "ces"), 5))
                .andExpect(status().isNotFound());
        close(OTHER_CLIENT, impressionId, 1).andExpect(status().isNotFound());
    }

    @Test
    void flexibleConditionsUseClientAttributes() throws Exception {
        long surveyId = createTestSurvey("test_conditions", 100, 100, """
                [{"attribute": "client_type", "op": "eq", "value": "repeat"},
                 {"attribute": "product", "op": "in", "value": ["PDL"]}]""");

        when(attributes.getAttributes("repeat-pdl")).thenReturn(Map.of("client_type", "repeat", "product", "PDL"));
        when(attributes.getAttributes("new-pdl")).thenReturn(Map.of("client_type", "new", "product", "PDL"));

        active("repeat-pdl", "test_step").andExpect(status().isOk())
                .andExpect(jsonPath("$.surveyId").value(surveyId));
        active("new-pdl", "test_step").andExpect(status().isNoContent());
        active("unknown", "test_step").andExpect(status().isNoContent());
    }

    @Test
    void zeroShowPercentNeverShows() throws Exception {
        createTestSurvey("test_sampling", 100, 0, "[]");
        active(CLIENT, "test_step").andExpect(status().isNoContent());
    }

    @Test
    void highestPriorityWinsAndFallsBackWhenItWasShownToday() throws Exception {
        long low = createTestSurvey("test_low", 1, 100, "[]");
        long high = createTestSurvey("test_high", 5, 100, "[]");

        JsonNode first = activeSurvey(CLIENT, "test_step");
        assertThat(first.get("surveyId").asLong()).isEqualTo(high);
        createImpression(CLIENT, first, "test_step", null);

        assertThat(activeSurvey(CLIENT, "test_step").get("surveyId").asLong()).isEqualTo(low);
    }

    @Test
    void archivedOrExpiredSurveysAreNotShown() throws Exception {
        long surveyId = createTestSurvey("test_expired", 1, 100, "[]");
        jdbc.update("UPDATE survey SET ends_at = '2026-10-05T08:00:00Z' WHERE id = ?", surveyId);
        active(CLIENT, "test_step").andExpect(status().isNoContent());

        jdbc.update("UPDATE survey SET ends_at = NULL, status = 'ARCHIVED' WHERE id = ?", surveyId);
        active(CLIENT, "test_step").andExpect(status().isNoContent());
    }

    @Test
    void questionsWithAnswersCannotBeChanged() throws Exception {
        JsonNode survey = activeSurvey(CLIENT, "loan_repaid");
        String impressionId = createImpression(CLIENT, survey, "loan_repaid", null);
        long ces = questionId(survey, "ces");
        saveStep(CLIENT, impressionId, 1, Map.of(ces, 4)).andExpect(status().isOk());

        assertThatThrownBy(() -> jdbc.update("UPDATE survey_question SET text = 'Новый текст' WHERE id = ?", ces))
                .hasMessageContaining("уже имеет ответы");
        jdbc.update("UPDATE survey_question SET required = required WHERE id = ?", ces);
    }

    @Test
    void commentIsSavedOnlyWhenScoreIsLow() throws Exception {
        JsonNode survey = activeSurvey(CLIENT, "loan_issued");
        String impressionId = createImpression(CLIENT, survey, "loan_issued", null);
        long nps = questionId(survey, "nps");
        long comment = questionId(survey, "nps_comment");

        // Высокая оценка: комментарий скрыт, даже присланный он не сохраняется
        saveStep(CLIENT, impressionId, 1, Map.of(nps, 9, comment, "Не должен сохраниться")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM survey_answer WHERE impression_id = ?::uuid",
                Integer.class, impressionId)).isEqualTo(1);

        // Клиент передумал и поставил низкую оценку: комментарий сохраняется
        saveStep(CLIENT, impressionId, 1, Map.of(nps, 6, comment, "Сложная анкета")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT value_text FROM survey_answer WHERE question_id = ? AND impression_id = ?::uuid",
                String.class, comment, impressionId)).isEqualTo("Сложная анкета");

        // И снова высокая: сохраненный ранее комментарий удаляется
        saveStep(CLIENT, impressionId, 1, Map.of(nps, 10)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM survey_answer WHERE impression_id = ?::uuid",
                Integer.class, impressionId)).isEqualTo(1);
    }

    private long createTestSurvey(String code, int priority, int showPercent, String conditions) {
        Long surveyId = jdbc.queryForObject("""
                INSERT INTO survey (code, title, status, priority) VALUES (?, 'Тест', 'ACTIVE', ?) RETURNING id""",
                Long.class, code, priority);
        jdbc.update("""
                INSERT INTO survey_question (survey_id, code, metric, step, position, type, text, required, settings)
                VALUES (?, 'ces', 'CES', 1, 1, 'SCALE', 'Насколько легко?', true, '{"min": 1, "max": 7}')""", surveyId);
        jdbc.update("""
                INSERT INTO survey_trigger (survey_id, flow_step_code, conditions, show_percent)
                VALUES (?, 'test_step', ?::jsonb, ?)""", surveyId, conditions, showPercent);
        return surveyId;
    }

    private ResultActions active(String clientId, String flowStep) throws Exception {
        return mvc.perform(get("/api/v1/surveys/active").header("X-Client-Id", clientId).param("flowStep", flowStep));
    }

    private JsonNode activeSurvey(String clientId, String flowStep) throws Exception {
        String body = active(clientId, flowStep).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private ResultActions postImpression(String clientId, long surveyId, String flowStep) throws Exception {
        return mvc.perform(post("/api/v1/surveys/impressions").header("X-Client-Id", clientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("surveyId", surveyId, "flowStep", flowStep))));
    }

    private String createImpression(String clientId, JsonNode survey, String flowStep, String eventObjectId)
            throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("surveyId", survey.get("surveyId").asLong());
        body.put("flowStep", flowStep);
        body.put("eventObjectId", eventObjectId);
        String response = mvc.perform(post("/api/v1/surveys/impressions").header("X-Client-Id", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("impressionId").asText();
    }

    private ResultActions saveStep(String clientId, String impressionId, int step, Map<Long, Object> values)
            throws Exception {
        List<Map<String, Object>> answers = values.entrySet().stream()
                .map(e -> Map.<String, Object>of("questionId", e.getKey(), "value", e.getValue()))
                .toList();
        return mvc.perform(put("/api/v1/surveys/impressions/{id}/steps/{step}", impressionId, step)
                .header("X-Client-Id", clientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("answers", answers))));
    }

    private ResultActions close(String clientId, String impressionId, int step) throws Exception {
        return mvc.perform(post("/api/v1/surveys/impressions/{id}/close", impressionId)
                .header("X-Client-Id", clientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("step", step))));
    }

    private static long questionId(JsonNode survey, String code) {
        for (JsonNode step : survey.get("steps")) {
            for (JsonNode question : step.get("questions")) {
                if (code.equals(question.get("code").asText())) {
                    return question.get("id").asLong();
                }
            }
        }
        throw new IllegalArgumentException("No question " + code);
    }
}
