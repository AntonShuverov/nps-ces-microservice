package ru.survey.service.domain;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "survey_question")
public class SurveyQuestion {

    @Id
    private Long id;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Metric metric;

    private int step;

    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Column(nullable = false)
    private String text;

    private boolean required;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode settings;

    protected SurveyQuestion() {
    }

    public Long getId() {
        return id;
    }

    public Long getSurveyId() {
        return surveyId;
    }

    public String getCode() {
        return code;
    }

    public Metric getMetric() {
        return metric;
    }

    public int getStep() {
        return step;
    }

    public int getPosition() {
        return position;
    }

    public QuestionType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public boolean isRequired() {
        return required;
    }

    public JsonNode getSettings() {
        return settings;
    }
}
