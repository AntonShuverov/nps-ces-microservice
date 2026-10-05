package ru.survey.service.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateImpressionRequest(
        @NotNull Long surveyId,
        @NotBlank @Size(max = 100) String flowStep,
        @Size(max = 100) String eventObjectId) {
}
