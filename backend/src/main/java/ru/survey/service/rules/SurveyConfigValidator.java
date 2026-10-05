package ru.survey.service.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import ru.survey.service.domain.QuestionType;
import ru.survey.service.domain.Survey;
import ru.survey.service.domain.SurveyQuestion;
import ru.survey.service.domain.SurveyTrigger;

/**
 * Проверяет конфигурацию опроса, заведенную миграцией: шаги, настройки вопросов, условия триггеров.
 * Опрос с ошибками не показывается клиентам (см. {@code SurveyConfigRegistry}).
 */
@Component
public class SurveyConfigValidator {

    private static final Set<String> OPERATORS = Set.of("eq", "neq", "in", "not_in", "gt", "gte", "lt", "lte");
    private static final Set<String> LIST_OPERATORS = Set.of("in", "not_in");

    /** Возвращает список ошибок. Пустой список — конфигурация корректна. */
    public List<String> validate(Survey survey, List<SurveyQuestion> questions, List<SurveyTrigger> triggers) {
        List<String> errors = new ArrayList<>();
        if (survey.getStartsAt() != null && survey.getEndsAt() != null
                && !survey.getStartsAt().isBefore(survey.getEndsAt())) {
            errors.add("дата начала не раньше даты окончания");
        }
        if (questions.isEmpty()) {
            errors.add("нет вопросов");
        }
        if (triggers.isEmpty()) {
            errors.add("нет триггеров");
        }

        Set<Integer> steps = new TreeSet<>();
        questions.forEach(q -> steps.add(q.getStep()));
        int expected = 1;
        for (int step : steps) {
            if (step != expected) {
                errors.add("шаги должны идти по порядку с 1, пропущен шаг " + expected);
                break;
            }
            expected++;
        }

        for (SurveyQuestion question : questions) {
            validateQuestion(question).forEach(e -> errors.add("вопрос " + question.getCode() + ": " + e));
            validateShowIf(question, questions).forEach(e -> errors.add("вопрос " + question.getCode() + ": " + e));
        }
        for (SurveyTrigger trigger : triggers) {
            validateConditions(trigger.getConditions())
                    .forEach(e -> errors.add("триггер " + trigger.getFlowStepCode() + ": " + e));
        }
        return errors;
    }

    private List<String> validateQuestion(SurveyQuestion question) {
        List<String> errors = new ArrayList<>();
        JsonNode settings = question.getSettings();
        if (question.getText() == null || question.getText().isBlank()) {
            errors.add("пустой текст");
        }
        switch (question.getType()) {
            case SCALE -> {
                if (!isInt(settings.get("min")) || !isInt(settings.get("max"))) {
                    errors.add("для шкалы нужны целые min и max");
                } else if (settings.get("min").asInt() >= settings.get("max").asInt()) {
                    errors.add("min шкалы должен быть меньше max");
                } else if (settings.get("max").asInt() - settings.get("min").asInt() > 10) {
                    errors.add("в шкале больше 11 значений");
                } else {
                    errors.addAll(validateScaleView(settings));
                }
            }
            case STARS -> {
                JsonNode count = settings.get("count");
                if (count != null && (!isInt(count) || count.asInt() < 2 || count.asInt() > 10)) {
                    errors.add("count звезд должен быть от 2 до 10");
                }
            }
            case TEXT -> {
                JsonNode maxLength = settings.get("maxLength");
                if (maxLength != null && (!isInt(maxLength) || maxLength.asInt() < 1)) {
                    errors.add("maxLength должен быть положительным числом");
                }
            }
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                errors.addAll(validateOptions(settings.path("options")));
                JsonNode maxSelected = settings.get("maxSelected");
                if (maxSelected != null && (!isInt(maxSelected) || maxSelected.asInt() < 1)) {
                    errors.add("maxSelected должен быть положительным числом");
                }
            }
        }
        return errors;
    }

    /** view = "emoji": шкала смайликами. Для шкалы из 5 значений смайлики по умолчанию есть на фронте. */
    private List<String> validateScaleView(JsonNode settings) {
        JsonNode view = settings.get("view");
        if (view == null || view.isNull()) {
            return List.of();
        }
        if (!"emoji".equals(view.asText())) {
            return List.of("неизвестный view '" + view.asText() + "', допустимо: emoji");
        }
        int size = settings.get("max").asInt() - settings.get("min").asInt() + 1;
        JsonNode icons = settings.get("icons");
        if (icons == null) {
            return size == 5 ? List.of() : List.of("для шкалы смайликами не из 5 значений нужен список icons");
        }
        if (!icons.isArray() || icons.size() != size) {
            return List.of("в icons должно быть " + size + " смайликов, по одному на значение");
        }
        return List.of();
    }

    /** showIf ссылается на шкалу или звезды на этом же или более раннем шаге. */
    private List<String> validateShowIf(SurveyQuestion question, List<SurveyQuestion> questions) {
        JsonNode showIf = question.getSettings().get("showIf");
        if (showIf == null || showIf.isNull()) {
            return List.of();
        }
        String code = showIf.path("question").asText("");
        String op = showIf.path("op").asText("");
        JsonNode value = showIf.get("value");
        SurveyQuestion source = questions.stream().filter(q -> q.getCode().equals(code)).findFirst().orElse(null);
        if (source == null) {
            return List.of("showIf ссылается на неизвестный вопрос '" + code + "'");
        }
        if (source == question || source.getStep() > question.getStep()
                || (source.getStep() == question.getStep() && source.getPosition() >= question.getPosition())) {
            return List.of("showIf должен ссылаться на вопрос выше этого");
        }
        if (source.getType() != QuestionType.SCALE && source.getType() != QuestionType.STARS) {
            return List.of("showIf работает только по шкале или звездам");
        }
        if (!VisibilityRule.OPERATORS.contains(op)) {
            return List.of("неизвестный оператор showIf '" + op + "'");
        }
        boolean valid = "in".equals(op)
                ? value != null && value.isArray() && !value.isEmpty()
                : value != null && value.isNumber();
        return valid ? List.of() : List.of("в showIf нужно " + ("in".equals(op) ? "список чисел" : "число") + " в value");
    }

    private List<String> validateOptions(JsonNode options) {
        if (!options.isArray() || options.isEmpty()) {
            return List.of("нет вариантов ответа");
        }
        List<String> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (JsonNode option : options) {
            String code = option.path("code").asText("");
            if (code.isBlank() || option.path("label").asText("").isBlank()) {
                errors.add("у варианта должны быть code и label");
            } else if (!codes.add(code)) {
                errors.add("повторяется код варианта " + code);
            }
        }
        return errors;
    }

    private List<String> validateConditions(JsonNode conditions) {
        if (conditions == null || conditions.isNull()) {
            return List.of();
        }
        if (!conditions.isArray()) {
            return List.of("условия должны быть массивом");
        }
        List<String> errors = new ArrayList<>();
        for (JsonNode condition : conditions) {
            String attribute = condition.path("attribute").asText("");
            String op = condition.path("op").asText("");
            JsonNode value = condition.get("value");
            if (attribute.isBlank()) {
                errors.add("в условии нет attribute");
            } else if (!OPERATORS.contains(op)) {
                errors.add("неизвестный оператор '" + op + "' для " + attribute);
            } else if (value == null || value.isNull()) {
                errors.add("в условии для " + attribute + " нет value");
            } else if (LIST_OPERATORS.contains(op) != value.isArray()) {
                errors.add("для оператора " + op + (value.isArray() ? " нужно одно значение" : " нужен список значений")
                        + " (" + attribute + ")");
            }
        }
        return errors;
    }

    private static boolean isInt(JsonNode node) {
        return node != null && node.isIntegralNumber();
    }
}
