# THIRD_PARTY_NOTICES

Сторонние компоненты, модели, данные и смоделированные интеграции. Платных зависимостей нет (ограничение ТЗ 02).
Полный файл собирает А (T46); каждый модуль дописывает свой раздел при добавлении зависимости.

## Backend (`server/backend/`)

Версии зафиксированы в `server/backend/pom.xml` (родитель `spring-boot-starter-parent` 3.3.4).

| Компонент | Версия | Лицензия | Зачем |
|---|---|---|---|
| Spring Boot (`spring-boot-starter-web`, `-validation`, `-test`) | 3.3.4 | Apache-2.0 | веб-сервер, REST, валидация запросов, тесты |
| Hibernate Validator (через `spring-boot-starter-validation`) | 8.0.1.Final | Apache-2.0 | проверка полей запросов (`@NotNull`, `@Size`…) |
| springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) | 2.6.0 | Apache-2.0 | Swagger UI и `/v3/api-docs` |
| Swagger UI (через springdoc) | 5.17.14 | Apache-2.0 | интерфейс документации API |
| Spring JDBC (`spring-boot-starter-jdbc`) | 3.3.4 | Apache-2.0 | доступ к PostgreSQL (HikariCP) |
| Flyway (`flyway-core`, `flyway-database-postgresql`) | 10.10.0 (BOM) | Apache-2.0 | миграции схемы БД при старте |
| PostgreSQL JDBC Driver | 42.7.4 (BOM) | BSD-2-Clause | драйвер подключения к PostgreSQL |

## Mocks (`mocks/`)

Версии зафиксированы в `mocks/requirements.txt`.

| Компонент | Версия | Лицензия | Зачем |
|---|---|---|---|
| FastAPI | 0.115.2 | MIT | HTTP-фреймворк сервиса моков |
| uvicorn | 0.32.0 | BSD-3-Clause | ASGI-сервер |
| pydantic (через FastAPI) | 2.9.2 | MIT | схемы запросов `connect` / `pay` |

Смоделированные интеграции (требование ТЗ 04 — вместо реальных внешних систем):
БКИ, ЕГРЮЛ/ЕГРИП, ФНС (+ `pay`), ОФД, маркетплейс (Wildberries), эквайринг.
Данные — 5 синтетических персонажей из `docs/synthetic-data.md`.

## Скоринг-сервис (`ml/`)

Версии зафиксированы в `ml/requirements.txt` (полный lock, собран `uv pip compile --exclude-newer 2026-10-01` из `ml/requirements.in` — без релизов моложе трёх недель); инструменты разработки — в `ml/requirements-dev.txt`.

| Компонент | Версия | Лицензия | Зачем |
|---|---|---|---|
| FastAPI | 0.115.2 | MIT | HTTP API сервиса (`/score`, `/offers/grid`, `/anomalies`, `/purpose`, `/summary`) |
| Starlette (через FastAPI) | 0.40.0 | BSD-3-Clause | ASGI-основа FastAPI |
| Pydantic, pydantic-core | 2.9.2, 2.23.4 | MIT | схемы и валидация запросов и ответов |
| Uvicorn | 0.32.0 | BSD-3-Clause | ASGI-сервер |
| PyYAML | 6.0.2 | MIT | чтение правил скоринга `ml/scoring_rules.yaml` и промптов `ml/llm.yaml` |
| HTTPX, httpcore | 0.27.2, 1.0.9 | BSD-3-Clause | клиент к Ollama; тестовый клиент FastAPI |
| certifi | 2026.7.22 | MPL-2.0 | корневые сертификаты для HTTPX |
| pytest (разработка) | 8.3.3 | MIT | тесты движка |
| Ruff (разработка) | 0.7.1 | MIT | линтер и форматтер |

### Локальная LLM (профиль compose `llm`, опционально)

Включается `LLM_ENABLED=true` и `docker compose --profile llm up`; без неё сервис работает на словаре и шаблонах. Модель с открытыми весами, запускается локально, внешних API нет (ограничения ТЗ 02 и 08).

| Компонент | Версия | Лицензия | Зачем |
|---|---|---|---|
| Ollama (образ `ollama/ollama`) | 0.12.0 | MIT | локальный сервер LLM |
| Qwen3 4B (`qwen3:4b`, Alibaba Qwen) | веса из реестра Ollama | Apache-2.0 | сводка андеррайтеру и разбор неоднозначной цели кредита |

## Frontend (`client/frontend/`)

Версии зафиксированы в `client/frontend/package-lock.json`.

| Компонент | Версия | Лицензия | Зачем |
|---|---|---|---|
| @alfalab/core-components | 50.37.0 | MIT | компоненты и тема Альфа-Банка |
| @alfalab/core-components-vars | 11.4.1 | MIT | CSS-переменные темы (приходит с core-components) |
| @alfalab/icons-glyph | 2.293.3 | MIT | иконки |
| React, React DOM | 18.3.1 | MIT | интерфейс |
| Vite | 8.3.5 | MIT | dev-сервер и сборка |
| @vitejs/plugin-react | 6.1.2 | MIT | поддержка React в Vite |
| TypeScript | 6.0.3 | Apache-2.0 | типизация (только разработка) |
| oxlint | 1.89.0 | MIT | линтер (только разработка) |
| react-router-dom | 7.18.4 | MIT | переходы между страницами |
