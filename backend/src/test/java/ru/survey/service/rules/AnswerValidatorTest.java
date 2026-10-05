package ru.survey.service.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.survey.service.domain.AnswerValue;
import ru.survey.service.domain.QuestionType;
import ru.survey.service.domain.SurveyQuestion;
import ru.survey.service.rules.AnswerValidator.InvalidAnswerException;

class AnswerValidatorTest {

    private final AnswerValidator validator = new AnswerValidator();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void scaleAcceptsIntegersInRange() throws Exception {
        SurveyQuestion nps = question(QuestionType.SCALE, "{\"min\": 0, \"max\": 10}");
        assertThat(validator.validate(nps, json.readTree("0"))).contains(AnswerValue.ofNumber(BigDecimal.ZERO));
        assertThat(validator.validate(nps, json.readTree("10"))).contains(AnswerValue.ofNumber(BigDecimal.TEN));
        assertThatThrownBy(() -> validator.validate(nps, json.readTree("11"))).isInstanceOf(InvalidAnswerException.class);
        assertThatThrownBy(() -> validator.validate(nps, json.readTree("5.5"))).isInstanceOf(InvalidAnswerException.class);
        assertThatThrownBy(() -> validator.validate(nps, json.readTree("\"7\""))).isInstanceOf(InvalidAnswerException.class);
    }

    @Test
    void cesScaleUsesOwnBounds() throws Exception {
        SurveyQuestion ces = question(QuestionType.SCALE, "{\"min\": 1, \"max\": 7}");
        assertThatThrownBy(() -> validator.validate(ces, json.readTree("0"))).isInstanceOf(InvalidAnswerException.class);
        assertThatThrownBy(() -> validator.validate(ces, json.readTree("8"))).isInstanceOf(InvalidAnswerException.class);
        assertThat(validator.validate(ces, json.readTree("7"))).isPresent();
    }

    @Test
    void starsUseCount() throws Exception {
        SurveyQuestion stars = question(QuestionType.STARS, "{\"count\": 5}");
        assertThat(validator.validate(stars, json.readTree("5"))).isPresent();
        assertThatThrownBy(() -> validator.validate(stars, json.readTree("0"))).isInstanceOf(InvalidAnswerException.class);
    }

    @Test
    void textIsCleanedAndTruncated() throws Exception {
        SurveyQuestion comment = question(QuestionType.TEXT, "{\"maxLength\": 5}");
        assertThat(validator.validate(comment, json.getNodeFactory().textNode("  a\u0000b\u0007c\r\nde f ")))
                .contains(AnswerValue.ofText("abc\nd"));
        assertThat(validator.validate(comment, json.getNodeFactory().textNode("   "))).isEmpty();
    }

    @Test
    void singleChoiceRequiresKnownOption() throws Exception {
        SurveyQuestion choice = question(QuestionType.SINGLE_CHOICE,
                "{\"options\": [{\"code\": \"fast\", \"label\": \"Быстро\"}, {\"code\": \"slow\", \"label\": \"Долго\"}]}");
        assertThat(validator.validate(choice, json.readTree("\"fast\""))).contains(AnswerValue.ofOptions(List.of("fast")));
        assertThatThrownBy(() -> validator.validate(choice, json.readTree("\"other\"")))
                .isInstanceOf(InvalidAnswerException.class);
    }

    @Test
    void multipleChoiceChecksOptionsAndMaximum() throws Exception {
        SurveyQuestion choice = question(QuestionType.MULTIPLE_CHOICE,
                "{\"maxSelected\": 2, \"options\": [{\"code\": \"a\"}, {\"code\": \"b\"}, {\"code\": \"c\"}]}");
        assertThat(validator.validate(choice, json.readTree("[\"a\", \"b\", \"a\"]")))
                .contains(AnswerValue.ofOptions(List.of("a", "b")));
        assertThat(validator.validate(choice, json.readTree("[]"))).isEmpty();
        assertThatThrownBy(() -> validator.validate(choice, json.readTree("[\"a\", \"b\", \"c\"]")))
                .isInstanceOf(InvalidAnswerException.class);
        assertThatThrownBy(() -> validator.validate(choice, json.readTree("[\"x\"]")))
                .isInstanceOf(InvalidAnswerException.class);
    }

    @Test
    void nullMeansNoAnswer() throws Exception {
        assertThat(validator.validate(question(QuestionType.SCALE, "{}"), json.readTree("null"))).isEmpty();
        assertThat(validator.validate(question(QuestionType.SCALE, "{}"), null)).isEmpty();
    }

    private SurveyQuestion question(QuestionType type, String settings) throws Exception {
        SurveyQuestion question = org.springframework.beans.BeanUtils.instantiateClass(SurveyQuestion.class);
        ReflectionTestUtils.setField(question, "type", type);
        ReflectionTestUtils.setField(question, "settings", json.readTree(settings));
        return question;
    }
}
