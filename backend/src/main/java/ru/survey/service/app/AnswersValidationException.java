package ru.survey.service.app;

import java.util.Map;

/** Ошибки валидации ответов шага: ключ — ID вопроса, значение — текст ошибки. */
public class AnswersValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public AnswersValidationException(Map<String, String> errors) {
        super("Ответы не прошли проверку");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
