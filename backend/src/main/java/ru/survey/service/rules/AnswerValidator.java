package ru.survey.service.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ru.survey.service.domain.AnswerValue;
import ru.survey.service.domain.SurveyQuestion;

/**
 * Проверяет и нормализует значение ответа по типу вопроса (docs/survey-service.md, п. 7.3).
 * Возвращает пустой Optional, если ответа нет (null, пустая строка, пустой список).
 */
@Component
public class AnswerValidator {

    static final int DEFAULT_TEXT_MAX_LENGTH = 1000;

    public Optional<AnswerValue> validate(SurveyQuestion question, JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return Optional.empty();
        }
        JsonNode settings = question.getSettings();
        return switch (question.getType()) {
            case SCALE -> Optional.of(integerInRange(value,
                    settings.path("min").asInt(0), settings.path("max").asInt(10)));
            case STARS -> Optional.of(integerInRange(value, 1, settings.path("count").asInt(5)));
            case TEXT -> text(value, settings.path("maxLength").asInt(DEFAULT_TEXT_MAX_LENGTH));
            case SINGLE_CHOICE -> Optional.of(singleChoice(value, optionCodes(settings)));
            case MULTIPLE_CHOICE -> multipleChoice(value, optionCodes(settings),
                    settings.path("maxSelected").asInt(Integer.MAX_VALUE));
        };
    }

    private AnswerValue integerInRange(JsonNode value, int min, int max) {
        if (!value.isIntegralNumber()) {
            throw new InvalidAnswerException("Ожидается целое число");
        }
        int number = value.asInt();
        if (number < min || number > max) {
            throw new InvalidAnswerException("Значение должно быть от " + min + " до " + max);
        }
        return AnswerValue.ofNumber(BigDecimal.valueOf(number));
    }

    private Optional<AnswerValue> text(JsonNode value, int maxLength) {
        if (!value.isTextual()) {
            throw new InvalidAnswerException("Ожидается текст");
        }
        String cleaned = stripControlCharacters(value.asText()).strip();
        if (cleaned.isEmpty()) {
            return Optional.empty();
        }
        if (cleaned.codePointCount(0, cleaned.length()) > maxLength) {
            cleaned = cleaned.substring(0, cleaned.offsetByCodePoints(0, maxLength));
        }
        return Optional.of(AnswerValue.ofText(cleaned));
    }

    private AnswerValue singleChoice(JsonNode value, Set<String> options) {
        if (!value.isTextual() || !options.contains(value.asText())) {
            throw new InvalidAnswerException("Неизвестный вариант ответа");
        }
        return AnswerValue.ofOptions(List.of(value.asText()));
    }

    private Optional<AnswerValue> multipleChoice(JsonNode value, Set<String> options, int maxSelected) {
        if (!value.isArray()) {
            throw new InvalidAnswerException("Ожидается список вариантов");
        }
        Set<String> selected = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || !options.contains(item.asText())) {
                throw new InvalidAnswerException("Неизвестный вариант ответа");
            }
            selected.add(item.asText());
        }
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        if (selected.size() > maxSelected) {
            throw new InvalidAnswerException("Можно выбрать не больше " + maxSelected + " вариантов");
        }
        return Optional.of(AnswerValue.ofOptions(new ArrayList<>(selected)));
    }

    private static Set<String> optionCodes(JsonNode settings) {
        Set<String> codes = new LinkedHashSet<>();
        for (JsonNode option : settings.path("options")) {
            codes.add(option.path("code").asText());
        }
        return codes;
    }

    /** Удаляет управляющие символы, кроме перевода строки и табуляции. */
    static String stripControlCharacters(String text) {
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints()
                .filter(cp -> cp == '\n' || cp == '\t' || !Character.isISOControl(cp))
                .filter(cp -> Character.getType(cp) != Character.FORMAT || cp == 0x200D)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    public static class InvalidAnswerException extends RuntimeException {
        public InvalidAnswerException(String message) {
            super(message);
        }
    }
}
