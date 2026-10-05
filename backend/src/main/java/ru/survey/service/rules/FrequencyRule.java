package ru.survey.service.rules;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import ru.survey.service.config.SurveyProperties;

/**
 * Правило «опрос показывается при каждом новом событии, но не чаще 1 раза в N календарных дней»
 * (docs/survey-service.md, п. 6.1). Граница дня считается в часовом поясе survey.business-zone.
 */
@Component
public class FrequencyRule {

    private final ZoneId zone;

    public FrequencyRule(SurveyProperties properties) {
        this.zone = properties.businessZone();
    }

    /** Можно ли показать опрос, если последний показ был в lastShownAt (null — показов не было). */
    public boolean allows(Instant lastShownAt, int minIntervalDays, Instant now) {
        if (lastShownAt == null) {
            return true;
        }
        LocalDate lastDay = businessDay(lastShownAt);
        return !businessDay(now).isBefore(lastDay.plusDays(minIntervalDays));
    }

    public LocalDate businessDay(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    public Instant startOfBusinessDay(Instant now) {
        return businessDay(now).atStartOfDay(zone).toInstant();
    }
}
