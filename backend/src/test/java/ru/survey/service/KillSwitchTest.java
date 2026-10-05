package ru.survey.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "survey.enabled=false")
class KillSwitchTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mvc;

    @Test
    void disabledServiceNeverReturnsSurvey() throws Exception {
        mvc.perform(get("/api/v1/surveys/active").header("X-Client-Id", "client-1").param("flowStep", "loan_issued"))
                .andExpect(status().isNoContent());
    }
}
