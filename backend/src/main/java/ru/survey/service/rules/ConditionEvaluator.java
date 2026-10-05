package ru.survey.service.rules;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Проверяет гибкие условия триггера (docs/survey-service.md, п. 6.2).
 * Условия задаются массивом и объединяются через «И»:
 * <pre>[{"attribute": "client_type", "op": "eq", "value": "repeat"},
 *  {"attribute": "loan_number", "op": "gte", "value": 2},
 *  {"attribute": "product", "op": "in", "value": ["PDL", "IL"]}]</pre>
 * Операторы: eq, neq, in, not_in, gt, gte, lt, lte.
 * Если атрибута у клиента нет или оператор неизвестен, условие не выполняется.
 */
@Component
public class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);

    public static boolean hasConditions(JsonNode conditions) {
        return conditions != null && conditions.isArray() && !conditions.isEmpty();
    }

    public boolean matches(JsonNode conditions, Map<String, Object> attributes) {
        if (!hasConditions(conditions)) {
            return true;
        }
        for (JsonNode condition : conditions) {
            if (!matchesOne(condition, attributes)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesOne(JsonNode condition, Map<String, Object> attributes) {
        String attribute = condition.path("attribute").asText(null);
        String op = condition.path("op").asText("");
        JsonNode expected = condition.path("value");
        Object actual = attribute == null ? null : attributes.get(attribute);
        if (actual == null) {
            return false;
        }
        return switch (op) {
            case "eq" -> valueEquals(actual, expected);
            case "neq" -> !valueEquals(actual, expected);
            case "in" -> containsValue(expected, actual);
            case "not_in" -> expected.isArray() && !containsValue(expected, actual);
            case "gt" -> compare(actual, expected, result -> result > 0);
            case "gte" -> compare(actual, expected, result -> result >= 0);
            case "lt" -> compare(actual, expected, result -> result < 0);
            case "lte" -> compare(actual, expected, result -> result <= 0);
            default -> {
                log.warn("Unknown condition operator '{}' for attribute '{}'", op, attribute);
                yield false;
            }
        };
    }

    private boolean containsValue(JsonNode list, Object actual) {
        if (!list.isArray()) {
            return false;
        }
        for (JsonNode item : list) {
            if (valueEquals(actual, item)) {
                return true;
            }
        }
        return false;
    }

    private boolean valueEquals(Object actual, JsonNode expected) {
        BigDecimal actualNumber = toNumber(actual);
        if (actualNumber != null && expected.isNumber()) {
            return actualNumber.compareTo(expected.decimalValue()) == 0;
        }
        return Objects.equals(String.valueOf(actual), expected.asText());
    }

    /** Сравнение чисел. Если одно из значений не число, условие не выполняется. */
    private boolean compare(Object actual, JsonNode expected, IntPredicate check) {
        BigDecimal actualNumber = toNumber(actual);
        BigDecimal expectedNumber = expected.isNumber() ? expected.decimalValue() : toNumber(expected.asText());
        return actualNumber != null && expectedNumber != null && check.test(actualNumber.compareTo(expectedNumber));
    }

    private static BigDecimal toNumber(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
