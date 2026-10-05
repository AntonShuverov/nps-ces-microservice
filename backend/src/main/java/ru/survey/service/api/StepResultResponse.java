package ru.survey.service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.survey.service.domain.ImpressionStatus;

public record StepResultResponse(
        @Schema(description = "Статус показа") ImpressionStatus status,
        @Schema(description = "Последний шаг, на который клиент ответил") int lastStep,
        @Schema(description = "Опрос пройден до конца, можно показать экран благодарности") boolean completed) {
}
