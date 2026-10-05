package ru.survey.service.domain;

import java.math.BigDecimal;
import java.util.List;

/** Проверенное значение ответа. Заполнено одно поле в зависимости от типа вопроса. */
public record AnswerValue(BigDecimal number, String text, List<String> options) {

    public static AnswerValue ofNumber(BigDecimal number) {
        return new AnswerValue(number, null, null);
    }

    public static AnswerValue ofText(String text) {
        return new AnswerValue(null, text, null);
    }

    public static AnswerValue ofOptions(List<String> options) {
        return new AnswerValue(null, null, List.copyOf(options));
    }
}
