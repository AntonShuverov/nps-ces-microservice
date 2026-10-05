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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.survey.service.api.ActiveSurveyResponse;
import ru.survey.service.api.CloseRequest;
import ru.survey.service.api.CreateImpressionRequest;
import ru.survey.service.api.ErrorResponse;
import ru.survey.service.api.ImpressionResponse;
import ru.survey.service.api.SaveStepRequest;
import ru.survey.service.api.StepResultResponse;
import ru.survey.service.app.SurveyService;

/** API сервиса опросов (docs/survey-service.md, п. 7). Клиент определяется {@link ClientIdentityFilter}. */
@Tag(name = "Опросы", description = "Показ опроса, ответы, закрытие")
@RestController
@Validated
@RequestMapping("/api/v1/surveys")
public class SurveyController {

    private final SurveyService service;

    public SurveyController(SurveyService service) {
        this.service = service;
    }

    /** 7.1. Активный опрос для шага флоу: 200 с конфигурацией или 204, если опроса нет. */
    @Operation(summary = "Получить опрос для шага флоу",
            description = "Проверяет правила показа и возвращает конфигурацию опроса. Показ не создает.")
    @ApiResponse(responseCode = "200", description = "Опрос есть")
    @ApiResponse(responseCode = "204", description = "Опроса нет", content = @Content)
    @GetMapping("/active")
    public ResponseEntity<ActiveSurveyResponse> getActive(
            @Parameter(hidden = true) @RequestAttribute(ClientIdentityFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @Parameter(description = "Код шага флоу", example = "loan_issued")
            @RequestParam @NotBlank @Size(max = 100) String flowStep) {
        return service.findActiveSurvey(clientId, flowStep)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 7.2. Зафиксировать показ после отрисовки поп-апа: 201 с ID показа или 409, если показ уже невозможен. */
    @Operation(summary = "Зафиксировать показ",
            description = "Вызывается после отрисовки поп-апа. Правила показа проверяются повторно.")
    @ApiResponse(responseCode = "201", description = "Показ создан")
    @ApiResponse(responseCode = "409", description = "Опрос сейчас нельзя показать, например он уже показан сегодня",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/impressions")
    @ResponseStatus(HttpStatus.CREATED)
    public ImpressionResponse createImpression(
            @Parameter(hidden = true) @RequestAttribute(ClientIdentityFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @Valid @RequestBody CreateImpressionRequest request) {
        return new ImpressionResponse(service.recordImpression(
                clientId, request.surveyId(), request.flowStep(), blankToNull(request.eventObjectId())));
    }

    /** 7.3. Сохранить ответы шага. */
    @Operation(summary = "Сохранить ответы шага",
            description = "Повторная отправка шага перезаписывает ответы. После последнего шага показ завершается.")
    @ApiResponse(responseCode = "200", description = "Ответы сохранены")
    @ApiResponse(responseCode = "404", description = "Показ не найден или принадлежит другому клиенту",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Показ уже закрыт или завершен",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Ответы не прошли проверку, ошибки по ID вопроса",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/impressions/{impressionId}/steps/{step}")
    public StepResultResponse saveStep(
            @Parameter(hidden = true) @RequestAttribute(ClientIdentityFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @PathVariable UUID impressionId,
            @PathVariable @Min(1) int step,
            @Valid @RequestBody SaveStepRequest request) {
        return service.saveStep(clientId, impressionId, step, request.answers());
    }

    /** 7.4. Закрыть опрос крестиком. */
    @Operation(summary = "Закрыть опрос крестиком",
            description = "Сохраняет шаг, на котором клиент закрыл опрос. Для завершенного показа ничего не меняет.")
    @ApiResponse(responseCode = "204", description = "Закрыто")
    @ApiResponse(responseCode = "404", description = "Показ не найден или принадлежит другому клиенту",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/impressions/{impressionId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(
            @Parameter(hidden = true) @RequestAttribute(ClientIdentityFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @PathVariable UUID impressionId,
            @Valid @RequestBody CloseRequest request) {
        service.close(clientId, impressionId, request.step());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
