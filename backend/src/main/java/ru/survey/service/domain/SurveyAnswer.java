package ru.survey.service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "survey_answer")
public class SurveyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "impression_id", nullable = false)
    private UUID impressionId;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "value_number")
    private BigDecimal valueNumber;

    @Column(name = "value_text")
    private String valueText;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "value_options", columnDefinition = "text[]")
    private List<String> valueOptions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SurveyAnswer() {
    }

    public SurveyAnswer(SurveyImpression impression, Long questionId, Instant now) {
        this.impressionId = impression.getId();
        this.surveyId = impression.getSurveyId();
        this.clientId = impression.getClientId();
        this.questionId = questionId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Повторная отправка шага перезаписывает значение (п. 7.3). */
    public void setValue(AnswerValue value, Instant now) {
        this.valueNumber = value.number();
        this.valueText = value.text();
        this.valueOptions = value.options();
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public UUID getImpressionId() {
        return impressionId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public BigDecimal getValueNumber() {
        return valueNumber;
    }

    public String getValueText() {
        return valueText;
    }

    public List<String> getValueOptions() {
        return valueOptions;
    }
}
