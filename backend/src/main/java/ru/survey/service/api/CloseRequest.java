package ru.survey.service.api;

import jakarta.validation.constraints.Min;

/** Закрытие поп-апа. step — номер шага опроса, на котором клиент нажал крестик. */
public record CloseRequest(@Min(1) int step) {
}
