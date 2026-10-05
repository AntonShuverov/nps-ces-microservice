package ru.survey.service.api;

import ru.survey.service.domain.ImpressionStatus;

public record StepResultResponse(ImpressionStatus status, int lastStep, boolean completed) {
}
