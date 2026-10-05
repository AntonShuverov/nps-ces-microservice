package ru.survey.service.web;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.survey.service.api.ErrorResponse;
import ru.survey.service.config.SurveyProperties;

/**
 * Определяет клиента и ограничивает частоту запросов (docs/survey-service.md, п. 7 и 10.8).
 *
 * <p>ID клиента берется из заголовка survey.client-id-header, который выставляет API gateway после авторизации.
 * Фронт ID клиента не передает. Gateway обязан удалять этот заголовок из внешних запросов,
 * иначе клиент сможет отвечать от имени другого клиента.
 *
 * <p>Ограничение частоты работает в памяти одного экземпляра сервиса (окно в одну минуту).
 * Если сервис запущен в нескольких экземплярах, основной лимит лучше настроить на gateway.
 */
@Component
public class ClientIdentityFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTRIBUTE = "survey.clientId";
    private static final int MAX_CLIENT_ID_LENGTH = 100;

    private final SurveyProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public ClientIdentityFilter(SurveyProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String clientId = request.getHeader(properties.clientIdHeader());
        if (clientId == null || clientId.isBlank() || clientId.length() > MAX_CLIENT_ID_LENGTH) {
            reject(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Клиент не авторизован");
            return;
        }
        clientId = clientId.strip();
        if (!tryAcquire(clientId)) {
            reject(response, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "Слишком много запросов");
            return;
        }
        request.setAttribute(CLIENT_ID_ATTRIBUTE, clientId);
        chain.doFilter(request, response);
    }

    private boolean tryAcquire(String clientId) {
        int limit = properties.rateLimit().requestsPerMinute();
        if (limit <= 0) {
            return true;
        }
        long minute = clock.millis() / 60_000;
        if (windows.size() > 100_000) {
            windows.values().removeIf(w -> w.minute < minute);
        }
        Window window = windows.compute(clientId,
                (id, current) -> current == null || current.minute != minute
                        ? new Window(minute, 1)
                        : new Window(minute, current.count + 1));
        return window.count <= limit;
    }

    private void reject(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(code, message));
    }

    private record Window(long minute, int count) {
    }
}
