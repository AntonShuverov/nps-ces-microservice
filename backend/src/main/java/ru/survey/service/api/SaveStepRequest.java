package ru.survey.service.api;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ответы одного шага. Значение зависит от типа вопроса:
 * число для шкалы и звезд, строка для текста и выбора одного, массив строк для выбора нескольких.
 */
public record SaveStepRequest(@NotNull @Size(max = 50) List<@Valid @NotNull Answer> answers) {

    public record Answer(
            @Schema(description = "ID вопроса", example = "2") @NotNull Long questionId,
            @Schema(description = "Число, строка или массив строк в зависимости от типа вопроса", example = "6",
                    types = {"integer", "string", "array"}) JsonNode value) {
    }
}
