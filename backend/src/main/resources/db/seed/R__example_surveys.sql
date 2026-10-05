-- Примеры опросов для локальной разработки и стенда (профиль dev), по макетам Figma «NPS CES».
-- В прод не попадают: реальные опросы заводятся миграциями в db/migration
-- после утверждения текстов продуктом (docs/survey-service.md, п. 10.11).
-- Повторяемая миграция: вставки идемпотентны, существующие записи не меняются.

-- Первая версия примеров (CES 1–7 до NPS) больше не используется.
UPDATE survey SET status = 'ARCHIVED', updated_at = now()
WHERE code IN ('ces_after_application_submitted', 'ces_nps_after_loan_issued', 'ces_after_loan_repaid')
  AND status <> 'ARCHIVED';

INSERT INTO survey (code, title, status, priority, min_interval_days) VALUES
    ('nps_ces_loan_issued',        'Пройдите опрос', 'ACTIVE', 20, 1),
    ('ces_application_submitted',  'Пройдите опрос', 'ACTIVE', 10, 1),
    ('ces_loan_repaid',            'Пройдите опрос', 'ACTIVE', 10, 1)
ON CONFLICT (code) DO NOTHING;

-- NPS + CES после выдачи займа: шаг 1 — NPS, шаг 2 — CES. Комментарий только при низкой оценке.
INSERT INTO survey_question (survey_id, code, metric, step, position, type, text, required, settings)
SELECT s.id, q.code, q.metric, q.step, q.position, q.type, q.text, q.required, q.settings::jsonb
FROM survey s,
     (VALUES
        ('nps', 'NPS', 1, 1, 'SCALE', 'Насколько вероятно, что вы порекомендуете наш сервис?', TRUE,
         '{"min": 0, "max": 10}'),
        ('nps_comment', 'NPS', 1, 2, 'TEXT', 'Посоветуйте, что можно сделать лучше', FALSE,
         '{"placeholder": "Ваш комментарий здесь", "maxLength": 1000, "showIf": {"question": "nps", "op": "lte", "value": 6}}'),
        ('ces', 'CES', 2, 1, 'SCALE', 'Насколько легко было получить займ?', TRUE,
         '{"min": 1, "max": 5, "view": "emoji"}'),
        ('ces_comment', 'CES', 2, 2, 'TEXT', 'С какими трудностями вы столкнулись?', FALSE,
         '{"placeholder": "Ваш комментарий здесь", "maxLength": 1000, "showIf": {"question": "ces", "op": "lte", "value": 3}}')
     ) AS q(code, metric, step, position, type, text, required, settings)
WHERE s.code = 'nps_ces_loan_issued'
ON CONFLICT (survey_id, code) DO NOTHING;

-- CES после отправки заявки
INSERT INTO survey_question (survey_id, code, metric, step, position, type, text, required, settings)
SELECT s.id, q.code, q.metric, q.step, q.position, q.type, q.text, q.required, q.settings::jsonb
FROM survey s,
     (VALUES
        ('ces', 'CES', 1, 1, 'SCALE', 'Насколько легко было оформить заявку?', TRUE,
         '{"min": 1, "max": 5, "view": "emoji"}'),
        ('ces_comment', 'CES', 1, 2, 'TEXT', 'С какими трудностями вы столкнулись?', FALSE,
         '{"placeholder": "Ваш комментарий здесь", "maxLength": 1000, "showIf": {"question": "ces", "op": "lte", "value": 3}}')
     ) AS q(code, metric, step, position, type, text, required, settings)
WHERE s.code = 'ces_application_submitted'
ON CONFLICT (survey_id, code) DO NOTHING;

-- CES после погашения займа
INSERT INTO survey_question (survey_id, code, metric, step, position, type, text, required, settings)
SELECT s.id, q.code, q.metric, q.step, q.position, q.type, q.text, q.required, q.settings::jsonb
FROM survey s,
     (VALUES
        ('ces', 'CES', 1, 1, 'SCALE', 'Насколько легко оплатить займ в Личном кабинете?', TRUE,
         '{"min": 1, "max": 5, "view": "emoji"}'),
        ('ces_comment', 'CES', 1, 2, 'TEXT', 'С какими трудностями вы столкнулись при оплате?', FALSE,
         '{"placeholder": "Ваш комментарий здесь", "maxLength": 1000, "showIf": {"question": "ces", "op": "lte", "value": 3}}')
     ) AS q(code, metric, step, position, type, text, required, settings)
WHERE s.code = 'ces_loan_repaid'
ON CONFLICT (survey_id, code) DO NOTHING;

INSERT INTO survey_trigger (survey_id, flow_step_code, conditions, show_percent)
SELECT s.id, t.flow_step, '[]'::jsonb, 100
FROM survey s
JOIN (VALUES
        ('nps_ces_loan_issued',       'loan_issued'),
        ('ces_application_submitted', 'application_submitted'),
        ('ces_loan_repaid',           'loan_repaid')
     ) AS t(code, flow_step) ON t.code = s.code
ON CONFLICT (survey_id, flow_step_code) DO NOTHING;
