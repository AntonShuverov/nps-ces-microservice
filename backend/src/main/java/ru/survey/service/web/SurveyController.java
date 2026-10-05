package ru.survey.service.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.survey.service.api.ActiveSurveyResponse;
import ru.survey.service.api.CloseRequest;
import ru.survey.service.api.CreateImpressionRequest;
import ru.survey.service.api.ImpressionResponse;
import ru.survey.service.api.SaveStepRequest;
import ru.survey.service.api.StepResultResponse;
import ru.survey.service.app.SurveyService;

/** API сервиса опросов (docs/survey-service.md, п. 7). Клиент определяется {@link ClientIdentityFilter}. */
@RestController
@Validated
@RequestMapping("/api/v1/surveys")
public class SurveyController {

    private final SurveyService service;

    public SurveyController(SurveyService service) {
        this.service = service;
    }

    /** 7.1. Активный опрос для шага флоу: 200 с конфигурацией или 204, если опроса нет. */
    @GetMapping("/active")
    public ResponseEntity<ActiveSurveyResponse> getActive(
            @RequestAttribute(ClientIdentityFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @RequestParam @NotBlank @Size(max = 100) String flowStep) {
        return service.findActiveSurvey(clientId, flowStep)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 7.2. Зафиксировать показ после отрисовки поп-апа: 201 с ID показа или 409, если показ уже невозможен. */
    @PostMapping("/impressions")
    @ResponseStatus(HttpStatus.CREATED)
    public ImpressionResponse createImpression(
            @RequestAttribute(ClientIdentityFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @Valid @RequestBody CreateImpressionRequest request) {
        return new ImpressionResponse(service.recordImpression(
                clientId, request.surveyId(), request.flowStep(), blankToNull(request.eventObjectId())));
    }

    /** 7.3. Сохранить ответы шага. */
    @PutMapping("/impressions/{impressionId}/steps/{step}")
    public StepResultResponse saveStep(
            @RequestAttribute(ClientIdentityFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @PathVariable UUID impressionId,
            @PathVariable @Min(1) int step,
            @Valid @RequestBody SaveStepRequest request) {
        return service.saveStep(clientId, impressionId, step, request.answers());
    }

    /** 7.4. Закрыть опрос крестиком. */
    @PostMapping("/impressions/{impressionId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(
            @RequestAttribute(ClientIdentityFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @PathVariable UUID impressionId,
            @Valid @RequestBody CloseRequest request) {
        service.close(clientId, impressionId, request.step());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
