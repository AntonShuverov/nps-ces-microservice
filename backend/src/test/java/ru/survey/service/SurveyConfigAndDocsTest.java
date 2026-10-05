package ru.survey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import ru.survey.service.app.SurveyConfigRegistry;

class SurveyConfigAndDocsTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SurveyConfigRegistry registry;

    @Test
    void exampleSurveysHaveValidConfiguration() {
        registry.refresh();
        jdbc.queryForList("SELECT id FROM survey WHERE status = 'ACTIVE'", Long.class)
                .forEach(id -> assertThat(registry.isInvalid(id)).as("survey %s", id).isFalse());
    }

    @Test
    void surveyWithBrokenConfigurationIsNotShown() throws Exception {
        Long surveyId = jdbc.queryForObject("""
                INSERT INTO survey (code, title, status, priority) VALUES ('test_broken', 'Тест', 'ACTIVE', 100)
                RETURNING id""", Long.class);
        jdbc.update("""
                INSERT INTO survey_question (survey_id, code, metric, step, position, type, text, required, settings)
                VALUES (?, 'ces', 'CES', 1, 1, 'SCALE', 'Насколько легко?', true, '{"min": 7, "max": 1}')""", surveyId);
        jdbc.update("INSERT INTO survey_trigger (survey_id, flow_step_code) VALUES (?, 'test_step')", surveyId);

        registry.refresh();

        assertThat(registry.isInvalid(surveyId)).isTrue();
        mvc.perform(get("/api/v1/surveys/active").header("X-Client-Id", "client-1").param("flowStep", "test_step"))
                .andExpect(status().isNoContent());
    }

    @Test
    void apiDocumentationIsAvailableWithoutClientHeader() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Сервис опросов NPS CES"))
                .andExpect(jsonPath("$.paths['/api/v1/surveys/active'].get.parameters[?(@.name == 'X-Client-Id')]")
                        .exists());
    }
}
