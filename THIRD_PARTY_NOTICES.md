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
