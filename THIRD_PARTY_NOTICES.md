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

## Скоринг-сервис (`ml/`)

Версии зафиксированы в `ml/requirements.txt` (полный lock, собран `uv pip compile` из `ml/requirements.in`); инструменты разработки — в `ml/requirements-dev.txt`.

| Компонент | Версия | Лицензия | Зачем |
|---|---|---|---|
| FastAPI | 0.115.2 | MIT | HTTP API сервиса (`/score`, `/health`) |
| Starlette (через FastAPI) | 0.40.0 | BSD-3-Clause | ASGI-основа FastAPI |
| Pydantic, pydantic-core | 2.9.2, 2.23.4 | MIT | схемы и валидация запросов и ответов |
| Uvicorn | 0.32.0 | BSD-3-Clause | ASGI-сервер |
| PyYAML | 6.0.2 | MIT | чтение правил скоринга `ml/scoring_rules.yaml` |
| pytest (разработка) | 8.3.3 | MIT | тесты движка |
| HTTPX (разработка) | 0.27.2 | BSD-3-Clause | тестовый клиент FastAPI |
| Ruff (разработка) | 0.7.1 | MIT | линтер и форматтер |
