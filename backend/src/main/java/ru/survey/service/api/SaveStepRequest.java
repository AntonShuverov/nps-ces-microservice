package ru.survey.service.api;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ответы одного шага. Значение зависит от типа вопроса:
 * число для шкалы и звезд, строка для текста и выбора одного, массив строк для выбора нескольких.
 */
public record SaveStepRequest(@NotNull @Size(max = 50) List<@Valid @NotNull Answer> answers) {

    public record Answer(@NotNull Long questionId, JsonNode value) {
    }
}
