package ru.survey.service.domain;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "survey")
public class Survey {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SurveyStatus status;

    private int priority;

    @Column(name = "min_interval_days")
    private int minIntervalDays;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    protected Survey() {
    }

    public boolean isActiveAt(Instant now) {
        return status == SurveyStatus.ACTIVE
                && (startsAt == null || !now.isBefore(startsAt))
                && (endsAt == null || now.isBefore(endsAt));
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public SurveyStatus getStatus() {
        return status;
    }

    public int getPriority() {
        return priority;
    }

    public int getMinIntervalDays() {
        return minIntervalDays;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }
}
