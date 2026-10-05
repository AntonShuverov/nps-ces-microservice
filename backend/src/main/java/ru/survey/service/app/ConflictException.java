package ru.survey.service.app;

/** Операция невозможна в текущем состоянии: опрос нельзя показать, показ уже закрыт и т. п. */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
