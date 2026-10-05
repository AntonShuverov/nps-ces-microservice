package ru.survey.service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/** Закрытие поп-апа. step — номер шага опроса, на котором клиент нажал крестик. */
public record CloseRequest(
        @Schema(description = "Номер шага опроса, на котором клиент закрыл поп-ап", example = "2") @Min(1) int step) {
}
