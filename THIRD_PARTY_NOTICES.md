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