package ru.survey.service.client;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import ru.survey.service.config.SurveyProperties;

/**
 * Запрашивает атрибуты клиента у внутреннего сервиса: GET {url}/clients/{clientId}/attributes.
 * Ожидаемый ответ — плоский JSON-объект, например {"client_type": "repeat", "product": "PDL", "loan_number": 3}.
 */
public class HttpClientAttributesProvider implements ClientAttributesProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpClientAttributesProvider.class);

    private final RestClient restClient;

    public HttpClientAttributesProvider(SurveyProperties properties) {
        Duration timeout = Duration.ofMillis(properties.clientAttributes().timeoutMs());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(properties.clientAttributes().url())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Map<String, Object> getAttributes(String clientId) {
        try {
            Map<String, Object> attributes = restClient.get()
                    .uri("/clients/{clientId}/attributes", clientId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return attributes == null ? Map.of() : attributes;
        } catch (RuntimeException e) {
            log.warn("Failed to load client attributes for client {}: {}", clientId, e.getMessage());
            return Map.of();
        }
    }
}
