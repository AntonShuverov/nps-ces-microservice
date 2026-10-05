package ru.survey.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import ru.survey.service.config.SurveyProperties;

class FrequencyRuleTest {

    private final FrequencyRule rule = new FrequencyRule(new SurveyProperties(true, ZoneId.of("Europe/Moscow"), 0,
            "X-Client-Id", new SurveyProperties.RateLimit(60), new SurveyProperties.ClientAttributes(null, 500)));

    @Test
    void allowsWhenNeverShown() {
        assertThat(rule.allows(null, 1, Instant.parse("2026-10-05T10:00:00Z"))).isTrue();
    }

    @Test
    void blocksSameBusinessDayAndAllowsNextOne() {
        Instant shown = Instant.parse("2026-10-05T08:00:00Z"); // 11:00 МСК
        assertThat(rule.allows(shown, 1, Instant.parse("2026-10-05T20:59:00Z"))).isFalse(); // 23:59 МСК
        assertThat(rule.allows(shown, 1, Instant.parse("2026-10-05T21:00:00Z"))).isTrue(); // 00:00 МСК следующего дня
    }

    @Test
    void respectsLongerInterval() {
        Instant shown = Instant.parse("2026-10-05T08:00:00Z");
        assertThat(rule.allows(shown, 3, Instant.parse("2026-10-07T08:00:00Z"))).isFalse();
        assertThat(rule.allows(shown, 3, Instant.parse("2026-10-07T21:00:00Z"))).isTrue();
    }
}
