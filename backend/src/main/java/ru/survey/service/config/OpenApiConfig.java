package ru.survey.service.config;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;

/** Документация API: /swagger-ui.html и /v3/api-docs. Отключается через SURVEY_API_DOCS_ENABLED=false. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI surveyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Сервис опросов NPS CES")
                .version("v1")
                .description("""
                        API поп-апа опросов на сайте. Требования: docs/survey-service.md, п. 7.

                        Все запросы проходят через API gateway, который авторизует клиента и передает его ID \
                        в заголовке X-Client-Id. Фронт ID клиента не передает. \
                        Для проверки из этой страницы заголовок можно указать вручную."""));
    }

    /** Заголовок с ID клиента в каждой операции API, чтобы запросы можно было отправить из Swagger UI. */
    @Bean
    public OperationCustomizer clientIdHeader(SurveyProperties properties) {
        return (operation, handlerMethod) -> operation.addParametersItem(new HeaderParameter()
                .name(properties.clientIdHeader())
                .required(true)
                .description("ID клиента. Выставляет API gateway после авторизации")
                .schema(new StringSchema().example("client-1")));
    }
}
