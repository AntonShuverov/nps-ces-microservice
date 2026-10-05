package ru.survey.service.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.survey.service.domain.SurveyImpression;

public interface SurveyImpressionRepository extends JpaRepository<SurveyImpression, UUID> {

    Optional<SurveyImpression> findByIdAndClientId(UUID id, String clientId);

    @Query("select max(i.createdAt) from SurveyImpression i where i.clientId = :clientId and i.surveyId = :surveyId")
    Optional<Instant> findLastShownAt(String clientId, Long surveyId);

    long countByClientIdAndCreatedAtGreaterThanEqual(String clientId, Instant from);
}
