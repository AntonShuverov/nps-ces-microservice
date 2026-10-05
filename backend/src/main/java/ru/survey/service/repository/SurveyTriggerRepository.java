package ru.survey.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.survey.service.domain.SurveyTrigger;

public interface SurveyTriggerRepository extends JpaRepository<SurveyTrigger, Long> {

    List<SurveyTrigger> findByFlowStepCode(String flowStepCode);

    List<SurveyTrigger> findBySurveyIdAndFlowStepCode(Long surveyId, String flowStepCode);
}
