package ru.survey.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.survey.service.domain.SurveyQuestion;

class VisibilityRuleTest {

    private final VisibilityRule rule = new VisibilityRule();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void questionWithoutConditionIsVisible() throws Exception {
        assertThat(rule.isVisible(question("{}"), Map.of())).isTrue();
    }

    @Test
    void commentShownOnlyForLowNps() throws Exception {
        SurveyQuestion comment = question("{\"showIf\": {\"question\": \"nps\", \"op\": \"lte\", \"value\": 6}}");
        assertThat(rule.isVisible(comment, Map.of("nps", BigDecimal.valueOf(6)))).isTrue();
        assertThat(rule.isVisible(comment, Map.of("nps", BigDecimal.ZERO))).isTrue();
        assertThat(rule.isVisible(comment, Map.of("nps", BigDecimal.valueOf(7)))).isFalse();
        assertThat(rule.isVisible(comment, Map.of())).as("на вопрос-условие не ответили").isFalse();
    }

    @Test
    void supportsInOperator() throws Exception {
        SurveyQuestion question = question("{\"showIf\": {\"question\": \"ces\", \"op\": \"in\", \"value\": [1, 2]}}");
        assertThat(rule.isVisible(question, Map.of("ces", BigDecimal.valueOf(2)))).isTrue();
        assertThat(rule.isVisible(question, Map.of("ces", BigDecimal.valueOf(3)))).isFalse();
    }

    private SurveyQuestion question(String settings) throws Exception {
        SurveyQuestion question = BeanUtils.instantiateClass(SurveyQuestion.class);
        ReflectionTestUtils.setField(question, "settings", json.readTree(settings));
        return question;
    }
}
