package ru.survey.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Интеграционные тесты на PostgreSQL. По умолчанию БД поднимается через Testcontainers (нужен Docker).
 * Чтобы использовать уже запущенный PostgreSQL, задайте TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(IntegrationTestBase.ClockTestConfig.class)
public abstract class IntegrationTestBase {

    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "survey"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "survey"));
            return;
        }
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void cleanData() {
        jdbc.update("DELETE FROM survey_answer");
        jdbc.update("DELETE FROM survey_impression");
        jdbc.update("DELETE FROM survey_trigger WHERE survey_id IN (SELECT id FROM survey WHERE code LIKE 'test_%')");
        jdbc.update("DELETE FROM survey_question WHERE survey_id IN (SELECT id FROM survey WHERE code LIKE 'test_%')");
        jdbc.update("DELETE FROM survey WHERE code LIKE 'test_%'");
        clock.set(Instant.parse("2026-10-05T09:00:00Z"));
    }

    @TestConfiguration
    static class ClockTestConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-10-05T09:00:00Z"));
        }
    }

    public static class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        public void set(Instant instant) {
            now = instant;
        }

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
