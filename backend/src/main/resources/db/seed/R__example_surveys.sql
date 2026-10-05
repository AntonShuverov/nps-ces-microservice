-- Примеры опросов для локальной разработки и стенда (профиль dev).
-- Повторяемая миграция: вставки идемпотентны, существующие записи не меняются.
-- В прод не попадают: реальные опросы заводятся миграциями в db/migration
-- после утверждения текстов продуктом (docs/survey-service.md, п. 10.11).

INSERT INTO survey (code, title, status, priority, min_interval_days) VALUES
    ('ces_after_application_submitted', 'Пройдите опрос', 'ACTIVE', 10, 1),
    ('ces_nps_after_loan_issued',       'Пройдите опрос', 'ACTIVE', 20, 1),
    ('ces_after_loan_repaid',           'Пройдите опрос', 'ACTIVE', 10, 1)
ON CONFLICT (code) DO NOTHING;

-- CES после отправки заявки
INSERT INTO survey_question (survey_id, code, metric, step, position, type, text, required, settings)
SELECT id, 'ces', 'CES', 1, 1, 'SCALE',
       'Насколько легко было оформить заявку?', TRUE,
       '{"min": 1, "max": 7, "minLabel": "Очень сложно", "maxLabel": "Очень легко"}'
FROM survey WHERE code = 'ces_after_application_submitted'
ON CONFLICT (survey_id, code) DO NOTHING;

-- CES + NPS после выдачи займа: шаг 1 — CES, шаг 2 — NPS и комментарий
INSERT INTO survey_question (survey_id, code, metric, step, position, type, text, required, settings)
SELECT id, 'ces', 'CES', 1, 1, 'SCALE',
       'Насколько легко было получить займ?', TRUE,
       '{"min": 1, "max": 7, "minLabel": "Очень сложно", "maxLabel": "Очень легко"}'
FROM survey WHERE code = 'ces_nps_after_loan_issued'
ON CONFLICT (survey_id, code) DO NOTHING;

INSERT INTO survey_question (survey_id, code, metric, step, position, type, text, required, settings)
SELECT id, 'nps', 'NPS', 2, 1, 'SCALE',
       'Насколько вероятно, что вы порекомендуете нас друзьям или коллегам?', TRUE,
       '{"min": 0, "max": 10, "minLabel": "Точно нет", "maxLabel": "Точно да"}'
FROM survey WHERE code = 'ces_nps_after_loan_issued'
ON CONFLICT (survey_id, code) DO NOTHING;

INSERT INTO survey_question (survey_id, code, metric, step, position, type, text, required, settings)
SELECT id, 'nps_comment', 'NPS', 2, 2, 'TEXT',
       'Что нам стоит улучшить?', FALSE,
       '{"placeholder": "Ваш комментарий", "maxLength": 1000}'
FROM survey WHERE code = 'ces_nps_after_loan_issued'
ON CONFLICT (survey_id, code) DO NOTHING;

-- CES после погашения займа
INSERT INTO survey_question (survey_id, code, metric, step, position, type, text, required, settings)
SELECT id, 'ces', 'CES', 1, 1, 'SCALE',
       'Насколько легко было погасить займ?', TRUE,
       '{"min": 1, "max": 7, "minLabel": "Очень сложно", "maxLabel": "Очень легко"}'
FROM survey WHERE code = 'ces_after_loan_repaid'
ON CONFLICT (survey_id, code) DO NOTHING;

INSERT INTO survey_trigger (survey_id, flow_step_code, conditions, show_percent)
SELECT id, 'application_submitted', '[]', 100 FROM survey WHERE code = 'ces_after_application_submitted'
ON CONFLICT (survey_id, flow_step_code) DO NOTHING;

INSERT INTO survey_trigger (survey_id, flow_step_code, conditions, show_percent)
SELECT id, 'loan_issued', '[]', 100 FROM survey WHERE code = 'ces_nps_after_loan_issued'
ON CONFLICT (survey_id, flow_step_code) DO NOTHING;

INSERT INTO survey_trigger (survey_id, flow_step_code, conditions, show_percent)
SELECT id, 'loan_repaid', '[]', 100 FROM survey WHERE code = 'ces_after_loan_repaid'
ON CONFLICT (survey_id, flow_step_code) DO NOTHING;
