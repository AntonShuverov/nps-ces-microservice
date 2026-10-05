package ru.survey.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();
    private final ObjectMapper json = new ObjectMapper();

    private final Map<String, Object> repeatPdlClient = Map.of(
            "client_type", "repeat",
            "product", "PDL",
            "loan_number", 3,
            "channel", "organic");

    @Test
    void emptyConditionsAlwaysMatch() throws Exception {
        assertThat(evaluator.matches(json.readTree("[]"), Map.of())).isTrue();
        assertThat(evaluator.matches(null, Map.of())).isTrue();
    }

    @Test
    void allConditionsMustMatch() throws Exception {
        JsonNode conditions = json.readTree("""
                [{"attribute": "client_type", "op": "eq", "value": "repeat"},
                 {"attribute": "product", "op": "in", "value": ["PDL", "IL"]},
                 {"attribute": "loan_number", "op": "gte", "value": 2}]
                """);
        assertThat(evaluator.matches(conditions, repeatPdlClient)).isTrue();
        assertThat(evaluator.matches(conditions, Map.of("client_type", "new", "product", "PDL", "loan_number", 1)))
                .isFalse();
    }

    @Test
    void supportsAllOperators() throws Exception {
        assertThat(matches("""
                {"attribute": "client_type", "op": "neq", "value": "new"}""")).isTrue();
        assertThat(matches("""
                {"attribute": "channel", "op": "not_in", "value": ["partners", "ads"]}""")).isTrue();
        assertThat(matches("""
                {"attribute": "loan_number", "op": "gt", "value": 3}""")).isFalse();
        assertThat(matches("""
                {"attribute": "loan_number", "op": "lt", "value": 4}""")).isTrue();
        assertThat(matches("""
                {"attribute": "loan_number", "op": "lte", "value": 3}""")).isTrue();
        assertThat(matches("""
                {"attribute": "loan_number", "op": "eq", "value": 3}""")).isTrue();
    }

    @Test
    void numbersComparedRegardlessOfRepresentation() throws Exception {
        assertThat(evaluator.matches(json.readTree("""
                [{"attribute": "loan_number", "op": "gte", "value": 2}]"""), Map.of("loan_number", "5"))).isTrue();
        assertThat(evaluator.matches(json.readTree("""
                [{"attribute": "loan_number", "op": "eq", "value": 5}]"""), Map.of("loan_number", 5.0))).isTrue();
    }

    @Test
    void missingAttributeOrUnknownOperatorDoesNotMatch() throws Exception {
        assertThat(matches("""
                {"attribute": "segment", "op": "neq", "value": "vip"}""")).isFalse();
        assertThat(matches("""
                {"attribute": "client_type", "op": "like", "value": "rep"}""")).isFalse();
        assertThat(matches("""
                {"attribute": "client_type", "op": "gt", "value": 1}""")).isFalse();
    }

    private boolean matches(String condition) throws Exception {
        return evaluator.matches(json.readTree("[" + condition + "]"), repeatPdlClient);
    }
}
