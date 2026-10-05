-- Пример витрины для DWH/BI: одна строка на «показ + вопрос» (docs/survey-service.md, п. 5.6 и 9).
-- Строится на сырых таблицах сервиса после загрузки в DWH. Расчет NPS и CES делается в BI.
--
-- Результат по вопросу:
--   ANSWERED     — по вопросу есть ответ;
--   CLOSED       — ответа нет, клиент закрыл поп-ап на шаге этого вопроса или раньше;
--   NOT_REACHED  — ответа нет, показ остался в статусе SHOWN (закрыл вкладку, обновил страницу)
--                  или клиент закрыл опрос, не дойдя до шага (на случай будущих сценариев).

CREATE OR REPLACE VIEW survey_question_result AS
SELECT
    i.id                                        AS impression_id,
    i.created_at                                AS shown_at,
    i.client_id,
    i.event_object_id,
    i.flow_step_code,
    s.code                                      AS survey_code,
    q.code                                      AS question_code,
    q.metric,
    q.step                                      AS question_step,
    q.type                                      AS question_type,
    i.status                                    AS impression_status,
    i.last_step,
    i.closed_at_step,
    a.value_number,
    a.value_text,
    a.value_options,
    a.updated_at                                AS answered_at,
    CASE
        WHEN a.id IS NOT NULL THEN 'ANSWERED'
        WHEN i.status = 'CLOSED' AND q.step >= i.closed_at_step THEN 'CLOSED'
        ELSE 'NOT_REACHED'
    END                                         AS question_result
FROM survey_impression i
JOIN survey s           ON s.id = i.survey_id
JOIN survey_question q  ON q.survey_id = i.survey_id
LEFT JOIN survey_answer a ON a.impression_id = i.id AND a.question_id = q.id;

-- Пример: NPS по шагу флоу за период (в BI считается так же).
-- SELECT flow_step_code,
--        count(*) AS answers,
--        round(100.0 * (count(*) FILTER (WHERE value_number >= 9) - count(*) FILTER (WHERE value_number <= 6))
--              / count(*), 1) AS nps
-- FROM survey_question_result
-- WHERE question_code = 'nps' AND question_result = 'ANSWERED'
--   AND shown_at >= date '2026-10-01'
-- GROUP BY flow_step_code;
