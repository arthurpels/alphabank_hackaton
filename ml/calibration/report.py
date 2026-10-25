from pathlib import Path

import numpy as np
import yaml

from calibration.acceptance import check
from calibration.analyze import analyze, auc, decide, logistic_regression
from calibration.candidate import ORDERED_BINS, bins, candidate_rules, fit
from calibration.generate import OUT_DIR, RULES_PATH, SEED, generate, write
from scoring.engine import Engine
from scoring.schemas import ScoreRequest

ML_DIR = Path(__file__).resolve().parents[1]
REPORT_PATH = ML_DIR.parent / "docs" / "calibration.md"
CANDIDATE_PATH = Path(__file__).resolve().parent / "candidate_rules.yaml"
CONTRACT_PATH = ML_DIR.parent / "docs" / "openapi-ml.yaml"
PERSONAS = ("coffeeStart", "coffee", "builder", "wb", "fns", "shell")
DECISIONS = ("APPROVED", "COUNTER_OFFER", "UNDERWRITING", "REJECTED")


def pct(value: float, digits: int = 1) -> str:
    return f"{100 * value:.{digits}f} %".replace(".", ",")


def num(value: float) -> str:
    return f"{value:+.1f}".replace(".", ",")


def table(headers: list[str], rows: list[list]) -> str:
    lines = ["| " + " | ".join(headers) + " |", "|" + "---|" * len(headers)]
    lines += ["| " + " | ".join(str(cell) for cell in row) + " |" for row in rows]
    return "\n".join(lines)


def persona_requests() -> dict[str, ScoreRequest]:
    contract = yaml.safe_load(CONTRACT_PATH.read_text(encoding="utf-8"))
    examples = contract["paths"]["/score"]["post"]["requestBody"]["content"]["application/json"]["examples"]
    return {name: ScoreRequest.model_validate(examples[name]["value"]) for name in PERSONAS}


def bins_auc(rows: list[dict]) -> float:
    labels = np.array([row["default_12m"] for row in rows])
    columns = [(factor, label) for factor, labels_ in ORDERED_BINS.items() for label in labels_]
    matrix = np.array([[bins(row).get(factor) == label for factor, label in columns] for row in rows], dtype=float)
    train = np.array([int(row["company_id"][-1]) < 7 for row in rows])
    weights = logistic_regression(matrix[train][:, 1:], labels[train])
    scores = np.hstack([np.ones((len(rows), 1)), matrix[:, 1:]]) @ weights
    return auc(scores[~train], labels[~train])


def auc_decomposition(rows: list[dict], engine: Engine) -> dict[str, float]:
    from calibration.analyze import full_request

    labels = np.array([row["default_12m"] for row in rows])
    midpoint, factors, core = [], [], []
    for row in rows:
        score = engine.score(full_request(row))
        values = {
            factor.code: (factor.value if factor.known else (factor.best + factor.worst) / 2)
            if factor.applicable
            else 0.0
            for factor in score.factors
        }
        midpoint.append((score.rate_from + score.rate_to) / 2)
        factors.append(sum(values.values()))
        core.append(sum(values[code] for code in ("credit_history", "business_age", "tax_discipline", "debt_load")))
    return {
        "midpoint": auc(np.array(midpoint), labels),
        "factors": auc(np.array(factors), labels),
        "core": auc(np.array(core), labels),
        "bins": bins_auc(rows),
    }


def build() -> tuple[str, dict]:
    current = yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))
    engine = Engine(current)
    rows = generate(engine=engine)
    metadata = write(rows, OUT_DIR, SEED, engine)
    acceptance = check(rows, engine)
    base = analyze(rows, engine)
    fitted = fit(rows)
    candidate = candidate_rules(current, fitted)
    CANDIDATE_PATH.write_text(
        "# Кандидат правил по итогам калибровки M20 — НЕ действующие правила.\n"
        "# Собирается командой python -m calibration.report; действующие правила — ml/scoring_rules.yaml.\n"
        + yaml.safe_dump(candidate, allow_unicode=True, sort_keys=False, width=120),
        encoding="utf-8",
    )
    candidate_engine = Engine(candidate)
    proposed = analyze(rows, candidate_engine)
    gap = auc_decomposition(rows, candidate_engine)

    personas = []
    for name, request in persona_requests().items():
        old, new = engine.score(request), candidate_engine.score(request)
        personas.append(
            [
                name,
                f"{num(old.rate_from)[1:]}–{num(old.rate_to)[1:]}",
                decide(engine, request, old),
                f"{num(new.rate_from)[1:]}–{num(new.rate_to)[1:]}",
                decide(candidate_engine, request, new),
            ]
        )

    current_values = {
        "credit_history": {
            "750+": -1.0,
            "700–749": -1.0,
            "600–699": 0.0,
            "550–599": 1.0,
            "просрочки или <550": "1,0 / 2,0",
        },
        "business_age": {"36+": -0.5, "12–35": 0.5, "6–11": 1.5},
        "tax_discipline": {"сдана": 0.0, "есть несданные": 1.0},
        "debt_load_pti": {"≤0,05": -1.5, "0,05–0,10": -0.5, "0,10–0,25": 0.5, ">0,25": 1.5},
        "debt_load_limit": {"≤0,15": -0.5, ">0,15": 0.5},
    }
    titles = {
        "credit_history": "Кредитная история (рейтинг БКИ)",
        "business_age": "Возраст бизнеса, мес.",
        "tax_discipline": "Налоговая отчётность",
        "debt_load_pti": "Нагрузка PTI (кредиты)",
        "debt_load_limit": "Доля лимита (овердрафт)",
    }
    weight_rows = []
    for factor, ordered in ORDERED_BINS.items():
        for label in ordered:
            current_value = current_values[factor][label]
            weight_rows.append(
                [
                    titles[factor],
                    label,
                    fitted["counts"][factor][label],
                    pct(fitted["rates"][factor].get(label, 0.0)),
                    current_value if isinstance(current_value, str) else num(current_value),
                    num(fitted["values"][factor][label]),
                ]
            )

    def outcome_rows(result: dict, name: str) -> list[list]:
        return [
            [
                decision,
                pct(result["outcomes"][name].get(decision, {"share": 0})["share"]),
                pct(result["outcomes"][name].get(decision, {"default_rate": 0})["default_rate"]),
            ]
            for decision in DECISIONS
        ]

    unknown_low, unknown_high = candidate["factors"]["debt_load"]["unknown"]

    def share_and_rate(result: dict, decision: str) -> str:
        outcome = result["outcomes"]["как подана"][decision]
        return f"{pct(outcome['share'])} / {pct(outcome['default_rate'])}"

    bin_rows = [
        [b["factor"], b["bin"], b["n"], pct(b["default_rate"]), f"{b['woe']:+.2f}", f"{b['logit']:+.2f}"]
        for b in base["bins"]
    ]
    lines = [
        "# Калибровка балльной карты (M20)",
        "",
        "> Сгенерировано `python -m calibration.report` (из `ml/`). Не правьте руками — перегенерируйте.",
        "",
        "Проверка весов `ml/scoring_rules.yaml` на синтетической популяции из",
        "[`synthetic-population.md`](synthetic-population.md). **Это проверка поведения правил на заданной",
        "синтетической модели риска, а не доказательство точности на реальных заёмщиках** (раздел 11",
        "спецификации). В production остаётся детерминированная балльная карта: «обучили, заморозили,",
        "версионировали» — калибровка даёт кандидата весов, решение о переходе принимает команда.",
        "",
        "## Как воспроизвести",
        "",
        "```bash",
        "cd ml && pip install -r requirements-dev.txt",
        "python -m calibration.report",
        "```",
        "",
        "Генератор детерминированный: тот же seed и те же версии дают тот же файл побайтово.",
        "",
        table(
            ["Параметр", "Значение"],
            [
                ["спецификация", metadata["spec_version"] + f" (sha256 `{metadata['spec_sha256'][:16]}…`)"],
                [
                    "популяция",
                    f"{metadata['population_size']} компаний, seed {metadata['seed']}, на {metadata['as_of']}",
                ],
                ["sha256 population.jsonl", f"`{metadata['population_sha256']}`"],
                ["правила", metadata["rules_version"]],
                ["Python / NumPy", f"{metadata['python']} / {metadata['numpy']}"],
            ],
        ),
        "",
        "## 1. Приёмка генератора (раздел 10 спецификации)",
        "",
        table(["Проверка", "Итог", "Детали"], [[name, "✓" if ok else "✗", detail] for name, ok, detail in acceptance]),
        "",
        f"Средняя доля дефолтов — {pct(base['default_rate'])}.",
        "",
        "## 2. Как работают действующие правила",
        "",
        "### Различающая способность (AUC: 0,5 — случайно, 1 — идеально)",
        "",
        table(["Что ранжирует", "AUC"], [[k, f"{v:.3f}".replace(".", ",")] for k, v in base["auc"].items()]),
        "",
        "«Как подана» — с белыми пятнами, «полная информация» — если клиент закрыл все пятна. Потолок —",
        "истинная PD генератора, недостижимый для любых правил: в ней есть шум и признаки, которых нет в",
        "карте (уровень выручки, волатильность). Логрегрессия на тех же факторах показывает, сколько можно",
        "выиграть одним перевесом.",
        "",
        "### Исходы автопроверки (правила R1–R6 из `decision-table.md`)",
        "",
        "Как подана:",
        "",
        table(["Исход", "Доля заявок", "Дефолт в группе"], outcome_rows(base, "как подана")),
        "",
        "Полная информация (все пятна закрыты):",
        "",
        table(["Исход", "Доля заявок", "Дефолт в группе"], outcome_rows(base, "полная информация")),
        "",
        "Порядок групп верный: одобренные дефолтят реже всех, отказы — чаще. Закрытие пятен переводит",
        "заявки из ручной очереди в автоодобрение при той же доле дефолтов — это и есть ценность",
        "«данных вместо залога» в цифрах.",
        "",
        "### Ставка и дефолт (полная информация, без стоп-факторов)",
        "",
        table(
            ["Середина вилки", "Заявок", "Дефолт"],
            [[k, v["n"], pct(v["default_rate"])] for k, v in base["rate_buckets"].items()],
        ),
        "",
        "### Ступени факторов: доля дефолтов, WoE и вес логрегрессии",
        "",
        "WoE > 0 — ступень лучше среднего, < 0 — хуже. Вес логрегрессии — вклад ступени в логарифм шансов",
        "дефолта при прочих равных (относительно самой массовой ступени фактора). Цель и обеспечение в",
        "синтетике на риск не влияют по построению (раздел 6.4) — это политика продукта, данными она не",
        "проверяется.",
        "",
        table(["Фактор", "Ступень (вклад, п.п.)", "Заявок", "Дефолт", "WoE", "Логит"], bin_rows),
        "",
        "### Обрывы ступеней на мелкой сетке",
        "",
        table(
            ["Рейтинг БКИ", "Заявок", "Дефолт"],
            [[b["range"], b["n"], pct(b["default_rate"])] for b in base["credit_score_fine"]],
        ),
        "",
        table(
            ["Нагрузка PTI", "Заявок", "Дефолт"],
            [[b["range"], b["n"], pct(b["default_rate"])] for b in base["load_fine"]],
        ),
        "",
        "### Стоп-факторы",
        "",
        table(
            ["Стоп-фактор", "Заявок", "Дефолт"],
            [[k, v["n"], pct(v["default_rate"])] for k, v in base["stop_factors"].items()],
        ),
        "",
        "## 3. Выводы по весам",
        "",
        "1. **Кредитная история недооценена на краях.** Рейтинг 500–549 даёт десятки процентов дефолтов,",
        "   550–599 — около 15 %, а в карте это одна ступень +1. Рейтинг 750+ втрое надёжнее 700–749, но",
        "   карта их не различает. Обрыв на 700 данными не подтверждается — риск снижается плавно.",
        "2. **Возраст бизнеса переоценён.** После стоп-фактора «моложе 6 месяцев» возраст почти не влияет",
        "   на дефолт, а карта даёт +1,5 п.п. за 6–11 месяцев.",
        "3. **Нагрузка ≤ 5 % и 5–10 % по риску не различаются**, разрыв 1 п.п. между ними — лишний.",
        "   Рост риска реальный выше 25 %.",
        "4. **Стоп-фактор «текущая просрочка в БКИ» оправдан** — две трети таких компаний дефолтят.",
        "   «Молодой бизнес» в синтетике по риску близок к среднему: это допущение генератора, а не повод",
        "   снимать стоп-фактор.",
        "",
        "## 4. Кандидат правил " + candidate["rules_version"],
        "",
        "Веса подобраны логистической регрессией по ступеням с границами там, где меняется риск, переведены",
        "в п.п. шкалой 1,25 п.п. на единицу логита, сделаны монотонными и округлены до 0,5 п.п., как в YAML.",
        "Файл — `ml/calibration/candidate_rules.yaml`.",
        "",
        "Откуда разрыв между вилкой кандидата и логрегрессией на тех же ступенях (полная информация):",
        "",
        table(
            ["Что ранжирует", "AUC"],
            [
                ["середина вилки кандидата", f"{gap['midpoint']:.3f}".replace(".", ",")],
                ["сумма вкладов факторов (без базовой ставки продукта)", f"{gap['factors']:.3f}".replace(".", ",")],
                ["только кредитная история, возраст, налоги, нагрузка", f"{gap['core']:.3f}".replace(".", ",")],
                ["логрегрессия на тех же ступенях без округления (тест 30 %)", f"{gap['bins']:.3f}".replace(".", ",")],
            ],
        ),
        "",
        "Почти весь разрыв — не веса: базовая ставка продукта добавляется к вилке, но с риском не связана, а",
        "цель, обеспечение и «выручка только по договору» в синтетике на риск не влияют по построению — для",
        "ранжирования это шум. Это осознанные продуктовые правила, их калибровка не трогает. Округление до",
        "0,5 п.п. почти ничего не стоит. Разрыв с потолком — признаки, которых в карте нет: уровень выручки и",
        "её волатильность.",
        "",
        table(["Фактор", "Ступень", "Заявок", "Дефолт", "Сейчас, п.п.", "Кандидат, п.п."], weight_rows),
        "",
        f"Неизвестная выручка (пятно «подтвердите выручку»): сейчас диапазон [−1,5; +2,0] (цена 3,5 п.п.), "
        f"у кандидата — [{num(unknown_low)}; {num(unknown_high)}] (цена {num(unknown_high - unknown_low)[1:]} п.п.).",
        "",
        table(
            ["Метрика", "Сейчас " + current["rules_version"], "Кандидат " + candidate["rules_version"]],
            [
                [k, f"{base['auc'][k]:.3f}".replace(".", ","), f"{proposed['auc'][k]:.3f}".replace(".", ",")]
                for k in ("вилка как подана", "вилка, полная информация")
            ]
            + [
                [
                    f"{decision}, как подана: доля / дефолт",
                    share_and_rate(base, decision),
                    share_and_rate(proposed, decision),
                ]
                for decision in DECISIONS
            ],
        ),
        "",
        "### Персонажи демо",
        "",
        table(["Персонаж", "Вилка сейчас", "Исход сейчас", "Вилка кандидата", "Исход кандидата"], personas),
        "",
        "## 5. Решение о переходе",
        "",
        "Кандидат лучше ранжирует риск и одобряет больше заявок автоматически при не большей доле",
        "дефолтов среди одобренных, исходы всех персонажей не меняются. Против перехода:",
        "",
        "- **Продуктовая история.** Пятно «подтвердите выручку» дешевеет: в синтетике нагрузка",
        "  предсказывает дефолт слабее кредитной истории. Заглавный пример концепции («касса — до",
        "  −3,5 п.п.») придётся пересказать. Это допущение генератора, а не реальная статистика.",
        "- **Цифры вилок персонажей меняются** — их проверяют чужие тесты и e2e.",
        "",
        "Если команда решит переходить, в одном PR обновляются: `ml/scoring_rules.yaml` (содержимое",
        "кандидата), примеры `docs/openapi-ml.yaml` и `docs/openapi.yaml`, `docs/decision-table.json`",
        "(`rulesVersion`), `docs/scoring-model.md`, `docs/synthetic-data.md` и `ml/synthetic_data.py`",
        "(вилки персонажей), e2e фронта `client/frontend/e2e/personas.spec.ts` (ожидаемые вилки), эталоны",
        "golden-master бэкенда (M27). Пока решения нет, действуют правила " + current["rules_version"] + ".",
        "",
    ]
    return "\n".join(lines), {"base": base, "proposed": proposed, "fitted": fitted, "acceptance": acceptance}


def main() -> None:
    text, _ = build()
    REPORT_PATH.write_text(text, encoding="utf-8")
    print(f"Отчёт: {REPORT_PATH}\nКандидат: {CANDIDATE_PATH}")


if __name__ == "__main__":
    main()
