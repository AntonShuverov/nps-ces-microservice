package ru.survey.service.api;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import ru.survey.service.domain.QuestionType;

/** Конфигурация опроса для фронта (docs/survey-service.md, п. 7.1). */
public record ActiveSurveyResponse(Long surveyId, String code, String title, List<Step> steps) {

    public record Step(int step, List<Question> questions) {
    }

    public record Question(Long id, String code, QuestionType type, String text, boolean required, JsonNode settings) {
    }
}
