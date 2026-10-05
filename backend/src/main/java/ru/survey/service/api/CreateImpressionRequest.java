package ru.survey.service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateImpressionRequest(
        @Schema(description = "ID опроса из ответа /active", example = "2") @NotNull Long surveyId,
        @Schema(description = "Код шага флоу", example = "loan_issued") @NotBlank @Size(max = 100) String flowStep,
        @Schema(description = "ID объекта события, например займа. Необязательно", example = "loan-42")
        @Size(max = 100) String eventObjectId) {
}
