# ml — скоринг-сервис

Детерминированный движок скоринга (`scoring/engine.py`, правила — `scoring_rules.yaml`) и разбор
свободного текста клиента: цель кредита и ответы на аномалии (словарь, затем локальная LLM с
правилами ответов, `llm.yaml`). Контракт — `docs/openapi-ml.yaml`, политика ИИ — `docs/ai-policy.md`.

## Тесты

```bash
docker run --rm -v "$PWD":/repo -w /repo/ml --entrypoint sh alphabank-hackathon-ml -c "pip install -q -r requirements-dev.txt && python -m pytest -q && ruff check scoring tests main.py eval"
```

Тесты не нужна Ollama: ответы модели берутся из записи `eval/replay/llm_answers.jsonl`.

## Eval золотых наборов (M26)

Наборы — `eval/data/` (цель кредита M12, ответы на аномалии M13, цель × деятельность компании M33,
правила разметки в README там же). Один набор — `--sets profile`; запись с `--record` дописывает ответы к
уже записанным.
Одна команда, без Ollama, на записанных ответах модели:

```bash
docker run --rm -v "$PWD":/repo -w /repo/ml --entrypoint python alphabank-hackathon-ml eval/run.py --check --report ../docs/internal/ml-quality.md
```

Печатает точность (всего, без спорных строк, по видам), кто решил (словарь или LLM),
детерминизм между прогонами, матрицу и список ошибок и обновляет блок eval в `docs/internal/ml-quality.md`.
`--check` — код выхода 1, если точность цели ниже 90 %, есть расхождения между прогонами или для
входа нет записанного ответа модели.

### Перезаписать ответы модели

Нужно после правки промптов `llm.yaml`, правил ответов или словарей: старые записи для новых
промптов не подходят — прогон replay покажет «нет записанного ответа модели».

```bash
docker compose --profile llm up -d ollama ollama-pull
```

```bash
docker compose run --rm --no-deps -v "$PWD":/repo -w /repo/ml --entrypoint python ml eval/run.py --mode live --record --runs 2 --report ../docs/internal/ml-quality.md
```

`--mode live` ходит в Ollama напрямую, без кеша вердиктов: два прогона подряд — настоящая проверка
детерминизма модели. Затем перезапустите Ollama (`docker compose restart ollama`) и сверьте с записью:

```bash
docker compose run --rm --no-deps -v "$PWD":/repo -w /repo/ml --entrypoint python ml eval/run.py --mode live --runs 1
```

### Режим replay в сервисе

`LLM_REPLAY_PATH=/app/eval/replay/llm_answers.jsonl` в `.env` — сервис `ml` отвечает записанными
ответами модели без Ollama (для CI и golden-master M27). Вход, которого нет в записи, — как
недоступная LLM (`UNAVAILABLE`).
