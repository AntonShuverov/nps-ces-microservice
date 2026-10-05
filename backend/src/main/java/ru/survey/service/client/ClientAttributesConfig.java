package ru.survey.service.client;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.survey.service.config.SurveyProperties;

/**
 * Если задан survey.client-attributes.url, атрибуты запрашиваются у сервиса клиентов.
 * Иначе атрибутов нет, и опросы с условиями по атрибутам не показываются.
 */
@Configuration
public class ClientAttributesConfig {

    @Bean
    public ClientAttributesProvider clientAttributesProvider(SurveyProperties properties) {
        String url = properties.clientAttributes().url();
        if (url == null || url.isBlank()) {
            return clientId -> Map.of();
        }
        return new HttpClientAttributesProvider(properties);
    }
}
