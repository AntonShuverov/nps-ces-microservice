package ru.survey.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.survey.service.domain.Survey;

public interface SurveyRepository extends JpaRepository<Survey, Long> {
}
