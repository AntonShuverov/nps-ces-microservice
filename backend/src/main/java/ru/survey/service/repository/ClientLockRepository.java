package ru.survey.service.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Блокировка на уровне клиента на время транзакции создания показа (п. 10.2).
 * Не дает двум вкладкам одновременно создать два показа в обход правила «1 раз в день».
 */
@Repository
public class ClientLockRepository {

    private final JdbcTemplate jdbc;

    public ClientLockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockClient(String clientId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", clientId);
    }
}
