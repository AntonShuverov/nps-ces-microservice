package ru.survey.service.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Глобальные настройки сервиса. Меняются через конфиг без релиза.
 *
 * @param enabled          аварийный флаг: при false сервис всегда отвечает «опроса нет» (п. 10.9)
 * @param businessZone     часовой пояс, по которому считается граница дня для правила «1 раз в день» (п. 10.10)
 * @param globalDailyLimit максимум показов любых опросов одному клиенту за день, 0 — без ограничения (п. 10.5)
 * @param clientIdHeader   заголовок с ID клиента, который выставляет API gateway после авторизации
 * @param rateLimit        ограничение частоты запросов на одного клиента (п. 10.8)
 */
@ConfigurationProperties(prefix = "survey")
public record SurveyProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("Europe/Moscow") ZoneId businessZone,
        @DefaultValue("0") int globalDailyLimit,
        @DefaultValue("X-Client-Id") String clientIdHeader,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue ClientAttributes clientAttributes) {

    /**
     * @param requestsPerMinute максимум запросов одного клиента в минуту, 0 — без ограничения
     */
    public record RateLimit(@DefaultValue("60") int requestsPerMinute) {
    }

    /**
     * @param url       базовый URL сервиса клиентов; если не задан, атрибуты не запрашиваются
     *                  и условия триггеров по атрибутам клиента не выполняются
     * @param timeoutMs таймаут запроса атрибутов
     */
    public record ClientAttributes(String url, @DefaultValue("500") int timeoutMs) {
    }
}
