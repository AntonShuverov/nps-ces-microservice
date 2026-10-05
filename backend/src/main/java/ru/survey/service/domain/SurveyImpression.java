package ru.survey.service.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "survey_impression")
public class SurveyImpression {

    @Id
    private UUID id;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "flow_step_code", nullable = false)
    private String flowStepCode;

    @Column(name = "event_object_id")
    private String eventObjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImpressionStatus status;

    @Column(name = "last_step")
    private int lastStep;

    @Column(name = "closed_at_step")
    private Integer closedAtStep;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SurveyImpression() {
    }

    public SurveyImpression(Long surveyId, String clientId, String flowStepCode, String eventObjectId, Instant now) {
        this.id = UUID.randomUUID();
        this.surveyId = surveyId;
        this.clientId = clientId;
        this.flowStepCode = flowStepCode;
        this.eventObjectId = eventObjectId;
        this.status = ImpressionStatus.SHOWN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isOpen() {
        return status == ImpressionStatus.SHOWN;
    }

    public void stepAnswered(int step, boolean lastStepOfSurvey, Instant now) {
        lastStep = Math.max(lastStep, step);
        if (lastStepOfSurvey) {
            status = ImpressionStatus.COMPLETED;
        }
        updatedAt = now;
    }

    public void close(int step, Instant now) {
        status = ImpressionStatus.CLOSED;
        closedAtStep = step;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Long getSurveyId() {
        return surveyId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getFlowStepCode() {
        return flowStepCode;
    }

    public String getEventObjectId() {
        return eventObjectId;
    }

    public ImpressionStatus getStatus() {
        return status;
    }

    public int getLastStep() {
        return lastStep;
    }

    public Integer getClosedAtStep() {
        return closedAtStep;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
