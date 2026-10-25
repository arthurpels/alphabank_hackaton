# api.md — API кредитного конвейера

Описание публичного API бэкенда человеческим языком. Машиночитаемый контракт —
`docs/openapi.yaml`, интерактивная документация — Swagger UI (`/swagger-ui.html`),
сырые схемы — `/v3/api-docs`.

Аудитория: фронтенд, аналитик, жюри. Предполагается прочитанным `concept.md`
(идея «Белых пятен») и `synthetic-data.md` (персонажи демо).

## Базовые сведения

- База URL: `http://localhost:8080/api/v1` (порт хоста — `BACKEND_PORT` в `.env`).
- Все тела — JSON в кодировке UTF-8; загрузка документа — `multipart/form-data`.
- Деньги — **в копейках** (`200000000` = 2 000 000 ₽), ставки и цены пятен — в % и п.п.
- Даты — ISO 8601 (`2026-10-23`), `registeredAt` и сроки — `YYYY-MM-DD`.

### Таймауты ML

- `/score`, сетка офферов и `/anomalies`: read timeout 4 с, ретраи как у остальных источников.
- `/purpose`, `/summary` и `/answers`: долгий `ML_LLM_TIMEOUT_MS` (по умолчанию `90000`), без ретраев.
  После `ML_LLM_CIRCUIT_FAILURES` ошибок подряд (по умолчанию 3) circuit breaker размыкается на
  `ML_LLM_CIRCUIT_OPEN_MS` (по умолчанию `30000`); следующий после паузы успешный вызов закрывает его снова.

## Аутентификация: демо-пользователь

Это прототип: вместо логина/пароля заголовок `X-Demo-User` с UUID пользователя.
Список для стартового экрана отдаётся без заголовка:

```
GET /api/v1/demo/users              # без заголовка; ?role=CLIENT|UNDERWRITER — фильтр
→ [{ "id": "00000000-…-0101", "fio": "Смирнова А. В.", "role": "CLIENT",
     "personaTitle": "ООО «Кофейня» — автоодобрение", "company": { "inn": "7701234560", … } }]
```

Дальше каждый запрос несёт `X-Demo-User: <uuid>`; `GET /api/v1/me` возвращает
текущего пользователя. Пользователи и компании засеяны при старте (миграция V6,
данные — `synthetic-data.md` §3). Заголовок битый/пустой/неизвестный → `401`.

Роли:

| Роль | Кто | Что может |
|---|---|---|
| `CLIENT` | владелец компании-персонажа | заявки **своей компании**: создать, изменить, рассчитать, закрывать пятна, загружать документы, принять/подписать/отменить |
| `UNDERWRITER` | Андеррайтер Демо | очередь и сводки, дозапросы (создание пятна), проверка ответов, решение по заявке; карточку заявки читает тоже |

Проверки: нет заголовка → `401`; не та роль (например, клиент в `/underwriting/**`
или в `review`/`decision`) → `403`; чужая заявка у клиента → `403`.

## Формат ошибок

Все ошибки — RFC 7807 Problem Detail с кодом в поле `code`:

```json
{ "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "Действие SUBMIT недопустимо в статусе DRAFT",
  "code": "INVALID_TRANSITION", "currentStatus": "DRAFT" }
```

| Код | HTTP | Когда |
|---|---|---|
| `VALIDATION_ERROR` | 400 | невалидное тело, кривой ИНН, пустой файл, не тот формат |
| `UNAUTHORIZED` | 401 | нет/битый заголовок `X-Demo-User` |
| `FORBIDDEN` | 403 | не та роль или заявка другой компании |
| `NOT_FOUND` | 404 | нет такого id |
| `INVALID_TRANSITION` | 409 | действие не разрешено статусной машиной (`currentStatus` — текущий статус) |
| `SOURCE_UNAVAILABLE` | 503 | мок или быстрый скоринг недоступен после ретраев; долгие LLM-вызовы не повторяются |
| `INTERNAL_ERROR` | 500 | непредвиденное; в логах бэкенда |

## Эндпоинты по группам

### Демо и справочники

- `GET /demo/users` — пользователи стартового экрана (без заголовка).
- `GET /demo/time`, `POST /demo/time/advance { days }`, `POST /demo/time/reset` — время демо (без заголовка):
  перемотка вперёд сразу отменяет просроченные дозапросы (`INFO_REQUESTED` дольше 5 дней → `CANCELLED`).
- `GET /me` — текущий пользователь и его компания.
- `GET /products` — продуктовая сетка: `code` (`EQUIPMENT`, `WORKING_CAPITAL`,
  `OVERDRAFT`), `name`, `minAmount`/`maxAmount` (копейки), `allowedTerms`.

### Жизненный цикл заявки (`/applications`)

- `POST /applications` — создать заявку → `DRAFT`. Тело:
  `productCode`, `amount` (копейки), `termMonths`, `goal` (текст до 1000),
  `consents: { bki, personalData }` — согласия на БКИ и обработку данных.
  → `201` + заявку.
- `GET /applications` — список заявок текущего клиента.
- `GET /applications/{id}` — карточка: параметры, компания, статус, вилка
  (`fork`: `rateFrom`/`rateTo`, `zone`, `thresholds`), пятна, офферы, решение.
  Клиенту — только свои; андеррайтеру — любая.
- `PATCH /applications/{id}` — поменять сумму/срок/цель (только `DRAFT`, `COLLECTING`).
- `POST /applications/{id}/evaluate` — **первичный расчёт**: бэкенд ходит в моки
  (ЕГРЮЛ, БКИ, ФНС), строит пятна и считает вилку через скоринг-сервис. При первом расчёте
  фиксирует на заявке `asOf` из `DEMO_DATE` (по умолчанию `2026-10-23`); все пересчёты
  используют её же, поэтому перемотка демо-времени меняет сроки, но не вилку.
  `DRAFT → COLLECTING`. Источник недоступен → `503 SOURCE_UNAVAILABLE`.
- `POST /applications/{id}/submit` — подать заявку. Дальше статусная машина:
  автоодобрение `→ APPROVED`, андеррайтер `→ UNDERWRITING`, иные условия
  `→ COUNTER_OFFER`, отказ `→ REJECTED` (всегда с причиной).
- `GET /applications/{id}/checks` — проверка компании в ЕГРЮЛ, ФНС и БКИ прямо сейчас: по каждому
  источнику `status` (`OK` / `WARNING` / `BLOCKED`), `facts` — что подтвердилось, `problems` — стоп-факторы.
  Клиент видит это после «Рассчитать». В `DRAFT` — `409`.
- `GET /applications/{id}/fork-history` — история вилки (видно, как сужалась).
- `POST /applications/{id}/fork/verify` — проверка воспроизводимости последнего снимка вилки (M06).
  Каждый снимок хранит полный `ScoreRequest` в каноническом JSON (ключи по алфавиту), его SHA-256,
  ответ `/score`, `rulesVersion`, версию движка `ml` и вердикты разбора цели с `llm.*`. Решение
  ссылается на снимок (`decision.forkSnapshotId`, `decision.requestHash`). `verify` проверяет хеш входа,
  заново отправляет вход в `/score` и сравнивает ответ байт-в-байт. LLM не вызывается: её вердикт уже
  внутри входа. Ответ — `{ reproducible, diff, rulesVersion, engineVersion, currentEngineVersion,
  requestHash, verdicts }`; при расхождении в `diff` — путь и оба значения
  (`{ "path": "$.rateTo", "expected": 19.5, "actual": 20.0 }`). Снимок без входа (рассчитан до M06) → `404`,
  скоринг недоступен → `503`. Доступно клиенту (своя заявка) и андеррайтеру.
- `GET /applications/{id}/history` — журнал событий заявки.

### Пятна и документы (`/applications/{id}/spots`)

- `GET …/spots` — список пятен: `type` (`NO_DATA`, `ANOMALY`, `DOCUMENT`,
  `STOP_FACTOR`, `COLLATERAL`), `title`, `clientHint`, `pricePp` (цена закрытия
  в п.п.), `closeMethod`, `status` (`OPEN`, `ON_REVIEW`, `CLOSED`), `source`.
- `POST …/spots` — дозапрос андеррайтера (`UNDERWRITING → INFO_REQUESTED`).
  Только андеррайтер.
- `POST …/spots/{spotId}/close` — закрыть пятно. Тело зависит от `closeMethod`:
  - `CONNECT_SOURCE` — `{ method, sourceCode, consent: true }`;
  - `ANSWER` — `{ method, optionCode?, text? }` (например, `RENOVATION`). Ответ по цели кредита разбирает
    `/purpose` ML-сервиса; вердикт (кто решил: словарь или LLM, обоснование, модель и версия промпта) хранится в
    `lastAnswer.purposeVerdict` и пишется в журнал. Пересчёты вилки и повтор того же текста модель не вызывают.
    `UNCLEAR` означает, что модель ответила «непонятно»: пятно остаётся `OPEN`, нужен более конкретный текст.
    `UNAVAILABLE` или таймаут означает, что модель не ответила: ответ сохраняется как `PENDING`, пятно уходит
    андеррайтеру в `ON_REVIEW`, отказа из-за недоступности LLM нет.
    Ответ на аномалию выручки (M16): вариант-кнопкой (`optionCode`: `RENOVATION`, `SEASONAL`, `CLIENT_LOST`) или
    свободным текстом — его разбирает `/answers/classify` (словарь, затем LLM). Причину проверяет данными
    `/answers/verify` по ряду источника за 24 месяца: подтвердилась — пятно `CLOSED` системой с правилом в журнале
    (Кофейня: «Был ремонт» → `RENOVATION_ONE_OFF`); нет, причина без проверки данными (`CLIENT_LOST`, `OTHER`),
    попытка управлять проверкой или LLM недоступна — `PENDING`, пятно `ON_REVIEW` у андеррайтера. Разбор и проверка
    хранятся в `lastAnswer.anomalyVerdict`; тот же текст модель повторно не разбирает. Вариант, которого нет в
    `answerOptions` пятна, — `400`;
  - `UPLOAD` — `{ method, documentId, text?, contractAmount?, contractTermMonths? }`
    (сумма/срок договора — если пятно по фактору `debt_load`);
  - `PAY` — `{ method }` (погашение долга ФНС, повторный запрос делает система);
  - `ADD_COLLATERAL` — `{ method, collateralType, collateralComment? }`.
- `POST …/spots/{spotId}/review` — андеррайтер принимает `{verdict: ACCEPTED}` или
  возвращает `{verdict: REJECTED, comment}` (комментарий обязателен).
- `POST …/documents?kind=CONTRACT_GP` — загрузить файл: `multipart/form-data`,
  часть `file`. Форматы: PDF/JPEG/PNG, до 10 МБ. → `201` + `{ id, kind, fileName, uploadedAt }`;
  `id` передаётся в `close` с `UPLOAD`.
- `GET …/documents/{documentId}` — скачать файл (владелец или андеррайтер).

### Офферы и выдача

- `GET …/offers` — варианты условий (`CALCULATED`/`OFFERED`): сумма, срок,
  `rateFrom`/`rateTo`, обеспечение, `proposedBy` (`SYSTEM`/`UNDERWRITER`).
- `POST …/accept { offerId }` — принять условия (`APPROVED|COUNTER_OFFER → SIGNING`).
- `POST …/sign` — подписать договор (`SIGNING → ISSUED`, смоделировано).
- `POST …/cancel { reason? }` — отменить заявку / отказаться от условий.

### Кабинет андеррайтера (`/underwriting`, только `UNDERWRITER`)

- `GET /underwriting/queue?status=…` — очередь заявок. Без `status` — в работе (`UNDERWRITING`,
  `INFO_REQUESTED`), старые сверху. `status` можно повторять: «Завершённые» —
  `APPROVED`, `COUNTER_OFFER`, `REJECTED`, `SIGNING`, `ISSUED`, `CANCELLED`, новые сверху; только поданные заявки.
- `GET /underwriting/applications/{id}/summary` — сохранённая сводка фактов, читается из БД без вызова
  моков/ML. Поля: `status` (`ready`/`generating`), `generatedBy`, `items`, `llm`, `generatedAt`.
  Шаблон создаётся при переходе в `UNDERWRITING` и после изменений пятен. При включённой LLM
  асинхронная версия позже заменяет шаблон; ошибка LLM оставляет шаблон в `ready`.
- `POST /applications/{id}/decision { type, offerIds?, comment?, override? }` —
  решение: `APPROVED` / ветка «иные условия» через `ALTERNATIVE` / `REJECTED`.
  Ручная правка условий — `override: true` + обязательный `comment`.

### Служебное

- `GET /health` — для compose-healthcheck: `{ "status": "ok", "dependencies":
  { "ml": "up", "mocks": "up" } }` (поле `dependencies` — информационное).

## Сквозной пример: ООО «Кофейня»

Персонаж: `X-Demo-User: 00000000-0000-0000-0000-000000000101`
(Смирнова А. В., ООО «Кофейня», ИНН 7701234560). Ожидаемый итог —
`APPROVED` на 18,0–19,5 % (`synthetic-data.md` §4).

**1. Создать заявку** — оборудование, 2 000 000 ₽ на 36 мес:

```
POST /api/v1/applications
{ "productCode": "EQUIPMENT", "amount": 200000000, "termMonths": 36,
  "goal": "Две кофемашины и витрина для второй точки",
  "consents": { "bki": true, "personalData": true } }
→ 201, status: DRAFT
```

**2. Рассчитать** — `POST /api/v1/applications/{id}/evaluate` → `COLLECTING`,
вилка широкая, четыре пятна:

```
[ NO_DATA  "Продажи через кассу не видны"          CONNECT_SOURCE  −3,5 п.п. ]
[ NO_DATA  "Не знаем, стабильна ли выручка"        CONNECT_SOURCE  −2,5 п.п. ]
[ NO_DATA  "На что именно нужны деньги"            ANSWER          −1,5 п.п. ]
[ COLLATERAL "Залог или поручительство — по желанию" ADD_COLLATERAL −1,5 п.п. ]
```

**3. Закрыть пятна** — подключить кассу (мок ОФД подтвердит выручку):

```
POST …/spots/{spotId}/close
{ "method": "CONNECT_SOURCE", "sourceCode": "OFD", "consent": true }

Цель кредита: { "method": "ANSWER", "text": "Кофемашины и витрина" }
Обеспечение: { "method": "ADD_COLLATERAL", "collateralType": "GUARANTEE" }
```

После каждого закрытия вилка пересчитывается и сужается (`fork-history` это показывает).
ОФД пришлёт ряд с падением марта — появится `ANOMALY` «выручка упала»:
закрывается ответом `{ "method": "ANSWER", "optionCode": "RENOVATION" }`
(система принимает сама: падение разовое, апрель восстановился).

**4. Подать** — `POST …/submit` → `AUTO_CHECK → APPROVED`, решение
`decidedBy = SYSTEM`, вилка 18,0–19,5 %, оффер на запрошенных условиях.

**5. Принять и подписать** — `POST …/accept { offerId }` → `SIGNING`;
`POST …/sign` → `ISSUED`. Готово.

Полный цикл воспроизводится с нуля после `scripts/reset-demo`.

## Ссылки

- Машиночитаемый контракт: `docs/openapi.yaml`
- Моки внешних источников (вызывает только бэкенд): `docs/openapi-mocks.yaml`
- Скоринг-сервис (внутренний): `docs/openapi-ml.yaml`
- Модель данных: `docs/day1_backend_data-model.md`; статусы: `docs/day1_backend_application-states.md`
