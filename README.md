# Микросервис NPS CES

Сервис опросов клиентов на сайте. На заданных шагах флоу (заявка отправлена, займ выдан, займ погашен) клиент видит поп-ап с вопросами CES и NPS. Оценки сохраняются в собственной БД и загружаются в DWH, а NPS и CES считает BI.

Техническое задание: [docs/survey-service.md](docs/survey-service.md).

## Как это работает

```
Сайт (виджет)                     survey-service                      PostgreSQL ──► DWH ──► BI
     │  GET  /active?flowStep=…        │ правила показа, условия
     │ ◄── конфигурация опроса / 204   │
     │  POST /impressions              │ повторная проверка, фиксация показа
     │  PUT  /impressions/{id}/steps/N │ валидация, сохранение ответов
     │  POST /impressions/{id}/close   │ шаг, на котором закрыли
```

Ключевые правила:
- опрос показывается при каждом новом наступлении события, но не чаще 1 раза в день на опрос (граница дня по Москве);
- лимитов показов и пауз после закрытия нет;
- гибкие условия показа: тип клиента, продукт, канал, номер займа и т. п., плюс доля показа в процентах;
- результат считается по каждому вопросу: если клиент ответил на CES и закрыл опрос на NPS, CES — «ответил», NPS — «закрыл»;
- сбой сервиса не влияет на флоу клиента: виджет молча не показывает опрос.

## Структура

| Папка | Что внутри |
|---|---|
| [backend](backend) | Сервис на Java 21 + Spring Boot 3.5, PostgreSQL, миграции Flyway |
| [widget](widget) | Поп-ап для сайта на TypeScript без зависимостей, работает в Shadow DOM |
| [dwh](dwh) | Пример витрины «показ + вопрос» для DWH и BI |
| [docs](docs) | Техническое задание |

## Быстрый старт

Нужны Docker, Node.js 20+.

```bash
# PostgreSQL + сервис с примерами опросов (профиль dev)
docker compose up --build

# Демо-страница виджета: http://localhost:5173/demo/index.html
cd widget && npm install && npm run dev
```

Без Docker: запустите PostgreSQL, создайте БД `survey` (пользователь и пароль `survey`) и выполните `cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run`.

## Бэкенд

### API

Все запросы идут через API gateway. Gateway авторизует клиента и передает его ID в заголовке `X-Client-Id`. Фронт ID клиента не передает.

> Gateway обязан удалять `X-Client-Id` из входящих внешних запросов, иначе клиент сможет отвечать от имени другого.

| Операция | Запрос | Ответ |
|---|---|---|
| Получить опрос для шага | `GET /api/v1/surveys/active?flowStep=loan_issued` | `200` конфигурация опроса или `204` |
| Зафиксировать показ | `POST /api/v1/surveys/impressions` `{"surveyId": 2, "flowStep": "loan_issued", "eventObjectId": "loan-42"}` | `201 {"impressionId": "…"}` или `409`, если показать уже нельзя |
| Сохранить ответы шага | `PUT /api/v1/surveys/impressions/{id}/steps/1` `{"answers": [{"questionId": 2, "value": 6}]}` | `200 {"status": "SHOWN", "lastStep": 1, "completed": false}`, `422` при ошибках валидации |
| Закрыть опрос | `POST /api/v1/surveys/impressions/{id}/close` `{"step": 2}` | `204` |

Значение ответа: число для шкалы и звезд, строка для текста и выбора одного, массив строк для выбора нескольких.

### Настройки

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/survey`, `survey`, `survey` | Подключение к БД |
| `SURVEY_ENABLED` | `true` | Аварийный флаг: `false` — опросы не показываются |
| `SURVEY_BUSINESS_ZONE` | `Europe/Moscow` | Часовой пояс для правила «1 раз в день» |
| `SURVEY_GLOBAL_DAILY_LIMIT` | `0` | Максимум показов любых опросов клиенту за день, `0` — без лимита |
| `SURVEY_CLIENT_ID_HEADER` | `X-Client-Id` | Заголовок с ID клиента от gateway |
| `SURVEY_RATE_LIMIT_PER_MINUTE` | `60` | Лимит запросов одного клиента в минуту |
| `SURVEY_CLIENT_ATTRIBUTES_URL` | пусто | Сервис клиентов для атрибутов условий: `GET {url}/clients/{id}/attributes` |
| `SURVEY_CORS_ALLOWED_ORIGINS` | пусто | Домены сайта, если виджет ходит в сервис напрямую, а не через gateway |

Метрики Prometheus: `/actuator/prometheus`, health-проверки: `/actuator/health/liveness`, `/actuator/health/readiness`.

### Как завести опрос

Опросы заводятся миграцией Flyway в `backend/src/main/resources/db/migration` (пример заполнения — `db/seed/R__example_surveys.sql`, он подключается только в профиле `dev`).

Условия триггера — JSON-массив, условия объединяются через «И»:

```json
[
  {"attribute": "client_type", "op": "eq", "value": "repeat"},
  {"attribute": "product", "op": "in", "value": ["PDL", "IL"]},
  {"attribute": "loan_number", "op": "gte", "value": 2}
]
```

Операторы: `eq`, `neq`, `in`, `not_in`, `gt`, `gte`, `lt`, `lte`. Доля показа задается в `survey_trigger.show_percent`.

У вопроса, на который уже есть ответы, нельзя менять текст, тип, шкалу и варианты: это запрещено триггером в БД. Для изменений создается новый опрос с новым кодом.

### Тесты

```bash
cd backend
mvn test                       # PostgreSQL поднимается через Testcontainers (нужен Docker)
TEST_DB_URL=jdbc:postgresql://localhost:5432/survey_test mvn test   # или на своем PostgreSQL
```

## Виджет

```bash
cd widget
npm install
npm test        # тесты
npm run build   # dist/survey-widget.js (ES-модуль) и dist/survey-widget.iife.js (для тега <script>)
```

Подключение на сайте:

```js
import { initSurveys, showSurvey } from 'survey-widget';

initSurveys({ baseUrl: '/api/v1/surveys' });

// на шаге «Займ выдан», после загрузки основного контента
showSurvey('loan_issued', { eventObjectId: loan.id });
```

Через тег `<script>`: `SurveyWidget.initSurveys({...})` и `SurveyWidget.showSurvey('loan_issued')`.

| Опция | По умолчанию | Назначение |
|---|---|---|
| `baseUrl` | `/api/v1/surveys` | Адрес API |
| `delayMs` | `2000` | Задержка перед показом, чтобы клиент сначала увидел результат шага |
| `requestTimeoutMs` | `1500` | Таймаут запроса опроса, при таймауте опрос не показывается |
| `headers` | — | Дополнительные заголовки, если авторизация не на cookie |
| `isOtherModalOpen` | ищет `[aria-modal="true"]` и `dialog[open]` | Не показывать опрос поверх другого модального окна |
| `closeOnEsc`, `closeOnOutsideClick` | `false` | Закрытие по Esc и клику вне поп-апа (решение за дизайном) |
| `thankYouAutoCloseMs` | `4000` | Автозакрытие экрана благодарности, `0` — не закрывать |
| `texts` | русские тексты | Тексты кнопок и сообщений |

Виджет запрашивает опрос один раз за визит на шаг: повторные вызовы при перерисовке игнорируются. В SPA при новом визите вызовите `resetSurveyVisit('loan_issued')`.

## Что осталось решить

Открытые вопросы из [п. 13 ТЗ](docs/survey-service.md#13-вопросы-до-старта-разработки), от которых зависит запуск в прод:
- как gateway передает ID клиента (сейчас заголовок `X-Client-Id`);
- откуда брать атрибуты клиента для условий (сейчас HTTP-запрос к сервису клиентов);
- стек сайта и подключение виджета, общий механизм модальных окон;
- шкала CES, тексты вопросов и экрана благодарности из Figma;
- подключение таблиц к загрузке в DWH и формат ID клиента и займа.
