package ru.survey.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.survey.service.domain.SurveyQuestion;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {

    List<SurveyQuestion> findBySurveyIdOrderByStepAscPositionAsc(Long surveyId);
}
