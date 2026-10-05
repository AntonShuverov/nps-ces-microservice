-- Схема сервиса опросов. Описание сущностей: docs/survey-service.md, раздел 5.
-- Все даты хранятся в UTC (timestamptz).

CREATE TABLE survey (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(100) NOT NULL UNIQUE,
    title               VARCHAR(255) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    priority            INT          NOT NULL DEFAULT 0,
    min_interval_days   INT          NOT NULL DEFAULT 1 CHECK (min_interval_days >= 1),
    starts_at           TIMESTAMPTZ,
    ends_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE survey IS 'Опрос и общие настройки';
COMMENT ON COLUMN survey.code IS 'Уникальный неизменяемый код, например ces_nps_after_loan_issued';
COMMENT ON COLUMN survey.min_interval_days IS 'Опрос показывается клиенту не чаще, чем раз в N календарных дней';

CREATE TABLE survey_question (
    id          BIGSERIAL PRIMARY KEY,
    survey_id   BIGINT       NOT NULL REFERENCES survey (id),
    code        VARCHAR(100) NOT NULL,
    metric      VARCHAR(20)  NOT NULL DEFAULT 'OTHER' CHECK (metric IN ('CES', 'NPS', 'OTHER')),
    step        INT          NOT NULL CHECK (step >= 1),
    position    INT          NOT NULL DEFAULT 1,
    type        VARCHAR(30)  NOT NULL
                CHECK (type IN ('SCALE', 'TEXT', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'STARS')),
    text        TEXT         NOT NULL,
    required    BOOLEAN      NOT NULL DEFAULT FALSE,
    settings    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (survey_id, code),
    UNIQUE (survey_id, step, position)
);

COMMENT ON TABLE survey_question IS 'Вопрос опроса';
COMMENT ON COLUMN survey_question.code IS 'Код вопроса (ces, nps, nps_comment), по нему BI отличает метрики';
COMMENT ON COLUMN survey_question.settings IS 'Настройки по типу: min/max/minLabel/maxLabel, placeholder/maxLength, options[{code,label}], maxSelected, count';

CREATE TABLE survey_trigger (
    id              BIGSERIAL PRIMARY KEY,
    survey_id       BIGINT       NOT NULL REFERENCES survey (id),
    flow_step_code  VARCHAR(100) NOT NULL,
    conditions      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    show_percent    INT          NOT NULL DEFAULT 100 CHECK (show_percent BETWEEN 0 AND 100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (survey_id, flow_step_code)
);

CREATE INDEX survey_trigger_flow_step_idx ON survey_trigger (flow_step_code);

COMMENT ON TABLE survey_trigger IS 'Связь опроса с шагом флоу и условия показа';
COMMENT ON COLUMN survey_trigger.conditions IS 'Список условий через И: [{"attribute":"client_type","op":"eq","value":"repeat"}]';
COMMENT ON COLUMN survey_trigger.show_percent IS 'Доля показа из подходящих клиентов, %';

CREATE TABLE survey_impression (
    id               UUID         PRIMARY KEY,
    survey_id        BIGINT       NOT NULL REFERENCES survey (id),
    client_id        VARCHAR(100) NOT NULL,
    flow_step_code   VARCHAR(100) NOT NULL,
    event_object_id  VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'SHOWN'
                     CHECK (status IN ('SHOWN', 'CLOSED', 'COMPLETED')),
    last_step        INT          NOT NULL DEFAULT 0,
    closed_at_step   INT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX survey_impression_client_survey_idx ON survey_impression (client_id, survey_id, created_at DESC);
CREATE INDEX survey_impression_client_created_idx ON survey_impression (client_id, created_at DESC);
CREATE INDEX survey_impression_updated_idx ON survey_impression (updated_at);

COMMENT ON TABLE survey_impression IS 'Факт показа опроса клиенту';
COMMENT ON COLUMN survey_impression.event_object_id IS 'ID объекта события (займа, заявки) для связи с DWH';
COMMENT ON COLUMN survey_impression.last_step IS 'Номер последнего шага опроса, на который клиент ответил';
COMMENT ON COLUMN survey_impression.closed_at_step IS 'Номер шага, на котором клиент закрыл поп-ап';

CREATE TABLE survey_answer (
    id             BIGSERIAL PRIMARY KEY,
    impression_id  UUID         NOT NULL REFERENCES survey_impression (id),
    survey_id      BIGINT       NOT NULL REFERENCES survey (id),
    question_id    BIGINT       NOT NULL REFERENCES survey_question (id),
    client_id      VARCHAR(100) NOT NULL,
    value_number   NUMERIC(10, 2),
    value_text     TEXT,
    value_options  TEXT[],
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (impression_id, question_id)
);

CREATE INDEX survey_answer_updated_idx ON survey_answer (updated_at);

COMMENT ON TABLE survey_answer IS 'Ответ на вопрос в рамках показа. Заполнено одно поле значения в зависимости от типа вопроса';

-- Правило 10.1: у опроса с ответами нельзя менять формулировки, типы, варианты и шкалу.
-- Для изменений создается новый опрос с новым кодом.
CREATE FUNCTION survey_question_protect() RETURNS trigger AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM survey_answer WHERE question_id = OLD.id) THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'Вопрос % уже имеет ответы и не может быть удален', OLD.id;
        END IF;
        IF NEW.text IS DISTINCT FROM OLD.text
           OR NEW.type IS DISTINCT FROM OLD.type
           OR NEW.settings IS DISTINCT FROM OLD.settings
           OR NEW.code IS DISTINCT FROM OLD.code
           OR NEW.step IS DISTINCT FROM OLD.step
           OR NEW.survey_id IS DISTINCT FROM OLD.survey_id THEN
            RAISE EXCEPTION 'Вопрос % уже имеет ответы: создайте новый опрос вместо изменения', OLD.id;
        END IF;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER survey_question_protect_trg
    BEFORE UPDATE OR DELETE ON survey_question
    FOR EACH ROW EXECUTE FUNCTION survey_question_protect();
