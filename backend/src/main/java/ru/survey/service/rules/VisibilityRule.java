package ru.survey.service.rules;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ru.survey.service.domain.SurveyQuestion;

/**
 * Условный показ вопроса по ответу на другой вопрос (по макетам: комментарий только при низкой оценке).
 * Задается в settings.showIf: {"question": "nps", "op": "lte", "value": 6}.
 * Операторы: eq, neq, gt, gte, lt, lte, in (value — список чисел).
 * Если на вопрос-условие не ответили, зависимый вопрос скрыт.
 */
@Component
public class VisibilityRule {

    public static final Set<String> OPERATORS = Set.of("eq", "neq", "gt", "gte", "lt", "lte", "in");

    /**
     * @param numericAnswers числовые ответы показа по коду вопроса (шкала, звезды)
     */
    public boolean isVisible(SurveyQuestion question, Map<String, BigDecimal> numericAnswers) {
        JsonNode showIf = question.getSettings().get("showIf");
        if (showIf == null || showIf.isNull()) {
            return true;
        }
        BigDecimal actual = numericAnswers.get(showIf.path("question").asText(""));
        if (actual == null) {
            return false;
        }
        JsonNode expected = showIf.path("value");
        String op = showIf.path("op").asText("");
        if ("in".equals(op)) {
            for (JsonNode item : expected) {
                if (item.isNumber() && actual.compareTo(item.decimalValue()) == 0) {
                    return true;
                }
            }
            return false;
        }
        if (!expected.isNumber()) {
            return false;
        }
        int result = actual.compareTo(expected.decimalValue());
        return switch (op) {
            case "eq" -> result == 0;
            case "neq" -> result != 0;
            case "gt" -> result > 0;
            case "gte" -> result >= 0;
            case "lt" -> result < 0;
            case "lte" -> result <= 0;
            default -> false;
        };
    }
}
