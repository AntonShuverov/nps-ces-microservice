package ru.survey.service.client;

import java.util.Map;

/**
 * Источник атрибутов клиента для условий показа (тип клиента, продукт, канал, номер займа).
 * Откуда брать атрибуты, решает бэкенд (docs/survey-service.md, п. 13).
 */
public interface ClientAttributesProvider {

    /**
     * Возвращает атрибуты клиента. При ошибке или таймауте возвращает пустую карту:
     * условия по атрибутам тогда не выполняются, и опрос с такими условиями не показывается.
     */
    Map<String, Object> getAttributes(String clientId);
}
