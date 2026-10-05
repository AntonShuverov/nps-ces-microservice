package ru.survey.service.domain;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "survey_trigger")
public class SurveyTrigger {

    @Id
    private Long id;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(name = "flow_step_code", nullable = false)
    private String flowStepCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode conditions;

    @Column(name = "show_percent")
    private int showPercent;

    protected SurveyTrigger() {
    }

    public Long getId() {
        return id;
    }

    public Long getSurveyId() {
        return surveyId;
    }

    public String getFlowStepCode() {
        return flowStepCode;
    }

    public JsonNode getConditions() {
        return conditions;
    }

    public int getShowPercent() {
        return showPercent;
    }
}
