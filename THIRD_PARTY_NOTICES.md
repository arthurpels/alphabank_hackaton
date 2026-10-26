# THIRD_PARTY_NOTICES

Сторонние компоненты, модели, данные и смоделированные интеграции, которые использует решение. Все
компоненты — с открытыми лицензиями; платных подписок, платных API, покупных данных и закрытых
лицензий нет (ограничение ТЗ 02). LLM — модель с открытыми весами, работает локально (ограничение ТЗ 08).

## Образы Docker

Версии образов заданы в `docker-compose.yml` и `Dockerfile` модулей.

| Образ | Версия | Лицензия | Зачем |
|---|---|---|---|
| `postgres` | 16 | PostgreSQL License | база данных |
| `ollama/ollama` | 0.12.0 | MIT | локальный сервер LLM (профиль `llm`) |
| `maven` | 3.9-eclipse-temurin-17 | Apache-2.0 (Maven); GPL-2.0 с Classpath Exception (Temurin) | сборка бэкенда |
| `eclipse-temurin` | 17-jre | GPL-2.0 с Classpath Exception | запуск бэкенда |
| `node` | 20-alpine | MIT (Node.js) | сборка и запуск фронтенда |
| `python` | 3.12-slim | PSF-2.0 | запуск `ml` и `mocks` |

## Backend (`server/backend/`)

Версии зафиксированы в `server/backend/pom.xml` (родитель `spring-boot-starter-parent` 3.3.4).

| Компонент | Версия | Лицензия | Зачем |
|---|---|---|---|
| Spring Boot (`spring-boot-starter-web`, `-validation`, `-security`, `-test`) | 3.3.4 | Apache-2.0 | веб-сервер, REST, валидация запросов, роли, тесты |
| Hibernate Validator (через `spring-boot-starter-validation`) | 8.0.1.Final | Apache-2.0 | проверка полей запросов |
| springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) | 2.6.0 | Apache-2.0 | Swagger UI и `/v3/api-docs` |
| Swagger UI (через springdoc) | 5.17.14 | Apache-2.0 | интерфейс документации API |
| Spring JDBC (`spring-boot-starter-jdbc`) | 3.3.4 | Apache-2.0 | доступ к PostgreSQL (HikariCP) |
| Flyway (`flyway-core`, `flyway-database-postgresql`) | 10.10.0 (BOM) | Apache-2.0 | миграции схемы БД при старте |
| PostgreSQL JDBC Driver | 42.7.4 (BOM) | BSD-2-Clause | драйвер PostgreSQL |
| Testcontainers (`junit-jupiter`, `postgresql`; тесты) | 1.19.8 (BOM) | MIT | golden-master тест: Postgres, моки и `ml` в контейнерах |
| docker-java (через Testcontainers; тесты) | 3.3.6 | Apache-2.0 | клиент Docker API для Testcontainers |

## Скоринг-сервис (`ml/`)

Версии зафиксированы в `ml/requirements.txt` — полный lock, включая транзитивные зависимости, собран
`uv pip compile --exclude-newer 2026-10-01` из `ml/requirements.in`. Инструменты разработки — в
`ml/requirements-dev.txt`.

| Компонент | Версия | Лицензия | Зачем |
|---|---|---|---|
| FastAPI | 0.115.2 | MIT | HTTP API сервиса |
| Starlette (через FastAPI) | 0.40.0 | BSD-3-Clause | ASGI-основа FastAPI |
| Pydantic, pydantic-core | 2.9.2, 2.23.4 | MIT | схемы и валидация запросов и ответов |
| annotated-types (через Pydantic) | 0.8.0 | MIT | аннотации ограничений для Pydantic |
| typing-extensions (через Pydantic) | 4.16.0 | PSF-2.0 | расширения типизации |
| Uvicorn | 0.32.0 | BSD-3-Clause | ASGI-сервер |
| click (через Uvicorn) | 8.5.0 | BSD-3-Clause | командная строка Uvicorn |
| h11 (через Uvicorn и httpcore) | 0.16.0 | MIT | протокол HTTP/1.1 |
| PyYAML | 6.0.2 | MIT | чтение правил `scoring_rules.yaml`, промптов `llm.yaml`, справочника `okved.yaml` |
| HTTPX, httpcore | 0.27.2, 1.0.9 | BSD-3-Clause | клиент к Ollama; тестовый клиент FastAPI |
| anyio (через HTTPX и Starlette) | 4.15.1 | MIT | асинхронный ввод-вывод |
| sniffio (через anyio и HTTPX) | 1.3.1 | MIT или Apache-2.0 | определение асинхронной библиотеки |
| idna (через HTTPX и anyio) | 3.20 | BSD-3-Clause | международные доменные имена |
| certifi (через HTTPX) | 2026.7.22 | MPL-2.0 | корневые сертификаты |
| SQLite (модуль `sqlite3` стандартной библиотеки Python) | из образа `python:3.12-slim` | Public Domain | кеш вердиктов LLM |
| pytest (разработка) | 8.3.3 | MIT | тесты |
| Ruff (разработка) | 0.7.1 | MIT | линтер и форматтер |
| Hypothesis (разработка) | 6.112.1 | MPL-2.0 | property-тесты движка |
| attrs (разработка, через Hypothesis) | 24.2.0 | MIT | зависимость Hypothesis |
| sortedcontainers (разработка, через Hypothesis) | 2.4.0 | Apache-2.0 | зависимость Hypothesis |
| NumPy (разработка) | 2.1.3 | BSD-3-Clause | генератор синтетической популяции и калибровка (`ml/calibration/`) |

## Сервис моков (`mocks/`)

Прямые зависимости зафиксированы в `mocks/requirements.txt`; транзитивные ставятся при сборке образа
в версиях, совместимых с FastAPI 0.115.2.

| Компонент | Версия | Лицензия | Зачем |
|---|---|---|---|
| FastAPI | 0.115.2 | MIT | HTTP-фреймворк сервиса моков |
| Uvicorn | 0.32.0 | BSD-3-Clause | ASGI-сервер |
| Starlette, Pydantic, pydantic-core, annotated-types, typing-extensions, anyio, sniffio, idna, click, h11 (транзитивные) | при сборке образа | BSD-3-Clause, MIT, PSF-2.0, MIT или Apache-2.0 | основа FastAPI и Uvicorn |

## Frontend (`client/frontend/`)

Версии зафиксированы в `client/frontend/package-lock.json` (442 пакета вместе с транзитивными).

| Компонент | Версия | Лицензия | Зачем |
|---|---|---|---|
| @alfalab/core-components | 50.37.0 | MIT | компоненты и тема Альфа-Банка |
| @alfalab/core-components-vars | 11.4.1 | MIT | CSS-переменные темы (приходит с core-components) |
| @alfalab/icons-glyph | 2.293.3 | MIT | иконки |
| React, React DOM | 18.3.1 | MIT | интерфейс |
| react-router-dom | 7.18.4 | MIT | переходы между страницами |
| Vite | 8.3.5 | MIT | dev-сервер и сборка |
| @vitejs/plugin-react | 6.1.2 | MIT | поддержка React в Vite |
| TypeScript (разработка) | 6.0.3 | Apache-2.0 | типизация |
| @types/node, @types/react, @types/react-dom (разработка) | 24.19.2, 18.3.31, 18.3.7 | MIT | типы для TypeScript |
| oxlint (разработка) | 1.89.0 | MIT | линтер |
| @playwright/test (разработка) | 1.64.1 | Apache-2.0 | e2e-тесты по персонажам |

## Инструменты проверки

| Компонент | Версия | Лицензия | Зачем |
|---|---|---|---|
| newman (через `npx`, версия закреплена в скрипте) | 6.2.1 | Apache-2.0 | прогон Postman-коллекции в `scripts/check-determinism` |

## Модель

| Модель | Версия | Лицензия | Что делает |
|---|---|---|---|
| Qwen3 4B (`qwen3:4b`, Alibaba Qwen), открытые веса | сборка по digest `OLLAMA_MODEL_DIGEST` в `.env.example`, из реестра Ollama | Apache-2.0 | разбирает свободный текст клиента, когда словарь не справился: подходит ли цель кредита продукту, похожа ли она на деятельность компании по ОКВЭД, какую причину падения выручки назвал клиент. Опционально — сводка андеррайтеру (по умолчанию выключена) |

Модель работает локально в Ollama (профиль compose `llm`) и по умолчанию выключена (`LLM_ENABLED=false`):
без неё решение работает на правилах и словарях. Кредитного решения модель не принимает
(`docs/ai-policy.md`).

## Данные

Данные о клиентах, компаниях, заявках и финансах — синтетические, созданы командой; реальных компаний,
людей и покупных данных нет (ограничение ТЗ 03). Единственный внешний справочник — общедоступный
классификатор ОКВЭД.

| Набор | Где | Что это |
|---|---|---|
| Демо-персонажи | `docs/synthetic-data.md`, `mocks/personas.py`, миграция `V6__seed_demo.sql` | пять компаний, их ответы внешних источников и андеррайтер |
| Золотые наборы для оценки ML | `ml/eval/data/` | размеченные цели кредита, ответы на аномалии выручки, цели × ОКВЭД компании |
| Записанные ответы модели | `ml/eval/replay/llm_answers.jsonl` | ответы `qwen3:4b` на золотые наборы для режима replay и тестов без Ollama |
| Синтетическая популяция заявителей | спецификация `docs/synthetic-population.md`, генератор `ml/calibration/` | модельная популяция для калибровки правил скоринга |
| Классы ОКВЭД 2 (ОК 029-2014) | `ml/okved.yaml` | общедоступный государственный классификатор: первые две цифры кода → вид деятельности; нужен для сверки цели кредита с деятельностью компании |

## Смоделированные интеграции

Все внешние проверки выполняет локальный сервис `mocks` (ограничение ТЗ 04); контракт —
`docs/openapi-mocks.yaml`, данные и правила — `docs/day1_backend_external-mocks.md`.

| Реальная система | Что заменяет мок | Эндпоинты мока |
|---|---|---|
| ЕГРЮЛ / ЕГРИП (ФНС) | регистрационные данные: статус, дата регистрации, директор и дата назначения, адрес и признак массовости, основной ОКВЭД | `GET /mock/egrul/v1/company` |
| ФНС (налоговая задолженность) | долг по налогам, несданная отчётность, система налогообложения; погашение долга | `GET /mock/fns/v1/debts`, `POST /mock/fns/v1/debts/pay` |
| Бюро кредитных историй | кредитный балл, открытые и закрытые кредиты, просрочки | `GET /mock/bki/v1/report` |
| ОФД (онлайн-касса) | помесячная выручка по кассе до 24 месяцев, по согласию клиента | `POST /mock/ofd/v1/connect`, `GET /mock/ofd/v1/revenue` |
| Маркетплейс (Wildberries) | продажи и выплаты продавцу за 6 месяцев, доля возвратов, рейтинг, по согласию клиента | `POST /mock/marketplace/v1/connect`, `GET /mock/marketplace/v1/sales` |
| Эквайринг | помесячный оборот по картам до 24 месяцев, по согласию клиента | `POST /mock/acquiring/v1/connect`, `GET /mock/acquiring/v1/turnover` |

Электронная подпись договора и зачисление денег смоделированы в бэкенде без внешних систем.
