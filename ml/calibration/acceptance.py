from collections import Counter
from datetime import date

import numpy as np

from calibration.generate import (
    AGE_P,
    AGE_RANGES,
    INDUSTRIES,
    INDUSTRY_P,
    MONTHS,
    POPULATION_SIZE,
    PRODUCT_P,
    PRODUCTS,
    PROFILES,
    REVENUE,
    SOURCES,
)
from scoring.engine import Engine, months_between
from scoring.schemas import ScoreRequest

LOAD_BINS = ((0, 0.05), (0.05, 0.10), (0.10, 0.25), (0.25, 0.30), (0.30, 1e9))
BKI_GROUPS = ((">=700", 700, 851), ("600–699", 600, 700), ("500–599", 500, 600), ("<500", 0, 500))
OBSERVED_FIELDS = (
    "as_of",
    "product_code",
    "amount",
    "term_months",
    "egrul_status",
    "registered_at",
    "director_since",
    "mass_address",
    "credit_score",
    "delinq_12m",
    "bki_current_overdue",
    "tax_debt",
    "unfiled_reports",
    "monthly_revenue",
    "stability",
    "purpose",
    "collateral",
)


def score_request(row: dict) -> ScoreRequest:
    return ScoreRequest.model_validate({field: row[field] for field in OBSERVED_FIELDS})


def _share_check(name: str, values: list, expected: dict) -> tuple[str, bool, str]:
    counts = Counter(values)
    worst = []
    ok = True
    for key, probability in expected.items():
        actual = counts.get(key, 0) / len(values)
        tolerance = 0.02 if probability >= 0.05 else 0.01
        ok = ok and abs(actual - probability) <= tolerance
        worst.append(f"{key} {actual:.3f}/{probability:.3f}")
    return name, ok, "; ".join(worst)


def _weighted(industry_rows: dict[str, int], table: dict[str, tuple], items: tuple) -> dict:
    total = sum(industry_rows.values())
    return {
        item: sum(industry_rows[industry] * table[industry][index] for industry in industry_rows) / total
        for index, item in enumerate(items)
    }


def check(rows: list[dict], engine: Engine) -> list[tuple[str, bool, str]]:
    checks = []
    as_of = date.fromisoformat(rows[0]["as_of"])
    ids = [row["company_id"] for row in rows]
    checks.append(
        ("размер и уникальность id", len(rows) == POPULATION_SIZE and len(set(ids)) == len(ids), f"{len(rows)}")
    )

    product_ok = all(
        engine.products[row["product_code"]].amount_min
        <= row["amount"]
        <= engine.products[row["product_code"]].amount_max
        and row["term_months"] in engine.products[row["product_code"]].terms
        for row in rows
    )
    checks.append(("суммы и сроки в правилах продуктов", product_ok, ""))
    money_ok = all(
        isinstance(row[key], int) and row[key] >= 0
        for row in rows
        for key in ("amount", "bki_current_overdue", "tax_debt", "true_monthly_revenue")
    )
    checks.append(("деньги целые и неотрицательные", money_ok, ""))
    series_ok = all(
        len(series["points"]) == 24
        and [point["month"] for point in series["points"]] == MONTHS
        and all(point["value"] >= 0 for point in series["points"])
        and abs(sum(point["value"] for point in series["points"]) / 24 - row["true_monthly_revenue"]) <= 1
        for row in rows
        for series in row["series"]
    )
    empty_ok = all(
        bool(row["series"]) == (row["revenue_source"] in ("OFD", "ACQUIRING", "MARKETPLACE") and row["consent_given"])
        for row in rows
    )
    checks.append(("ряды: 24 месяца до 2026-09, среднее = выручке", series_ok and empty_ok, ""))
    try:
        for row in rows:
            score_request(row)
        convertible = True
    except ValueError:
        convertible = False
    checks.append(("наблюдаемые поля → ScoreRequest", convertible, ""))
    pd = np.array([row["true_pd_12m"] for row in rows])
    defaults = np.array([row["default_12m"] for row in rows])
    checks.append(
        (
            "PD строго в (0; 1), оба класса",
            bool((pd > 0).all() and (pd < 1).all() and 0 < defaults.sum() < len(rows)),
            "",
        )
    )
    checks.append(("средняя PD 6–10 %", 0.06 <= pd.mean() <= 0.10, f"{pd.mean():.4f}"))
    checks.append(
        (
            "доля дефолтов 5,5–10,5 %, не меньше 500",
            0.055 <= defaults.mean() <= 0.105 and defaults.sum() >= 500,
            f"{defaults.mean():.4f}, {defaults.sum()}",
        )
    )

    checks.append(
        _share_check("отрасли", [row["okved_group"] for row in rows], dict(zip(INDUSTRIES, INDUSTRY_P, strict=False)))
    )
    checks.append(_share_check("форма ИП/ООО", [row["legal_form"] for row in rows], {"IP": 0.65, "OOO": 0.35}))
    checks.append(_share_check("ликвидация", [row["egrul_status"] for row in rows], {"LIQUIDATED": 0.01}))
    checks.append(_share_check("массовый адрес", [row["mass_address"] for row in rows], {True: 0.015}))
    ages = [months_between(date.fromisoformat(row["registered_at"]), as_of) for row in rows]
    age_groups = [next(f"{lo}–{hi}" for lo, hi in AGE_RANGES if lo <= age <= hi) for age in ages]
    checks.append(
        _share_check(
            "возраст бизнеса", age_groups, {f"{lo}–{hi}": p for (lo, hi), p in zip(AGE_RANGES, AGE_P, strict=False)}
        )
    )
    by_industry = Counter(row["okved_group"] for row in rows)
    checks.append(
        _share_check("продукты", [row["product_code"] for row in rows], _weighted(by_industry, PRODUCT_P, PRODUCTS))
    )
    from calibration.generate import SOURCE_P

    checks.append(
        _share_check(
            "каналы выручки",
            [row["revenue_source"] for row in rows],
            _weighted(by_industry, SOURCE_P, SOURCES),
        )
    )
    checks.append(_share_check("аномалии", [row["shock_type"] for row in rows], {"DROP": 0.08, "SPIKE": 0.03}))

    medians = []
    median_ok = True
    for industry, (median, _) in REVENUE.items():
        values = [row["true_monthly_revenue"] / 100 for row in rows if row["okved_group"] == industry]
        actual = float(np.median(values))
        median_ok = median_ok and abs(actual / median - 1) <= 0.10
        medians.append(f"{industry} {actual:,.0f}/{median:,}".replace(",", " "))
    checks.append(("медиана выручки по отраслям ±10 %", median_ok, "; ".join(medians)))

    loads = np.array([row["requested_load"] for row in rows])
    load_shares = [((loads > lo) & (loads <= hi)).mean() for lo, hi in LOAD_BINS]
    checks.append(
        (
            "диапазоны нагрузки ≥ 3 %",
            all(share >= 0.03 for share in load_shares),
            " ".join(f"{s:.3f}" for s in load_shares),
        )
    )
    load_pd = [pd[(loads > lo) & (loads <= hi)].mean() for lo, hi in LOAD_BINS]
    checks.append(
        ("PD не убывает с ростом нагрузки", all(np.diff(load_pd) >= 0), " ".join(f"{v:.3f}" for v in load_pd))
    )
    scores = np.array([row["credit_score"] for row in rows])
    bki_shares = [((scores >= lo) & (scores < hi)).mean() for _, lo, hi in BKI_GROUPS]
    bki_pd = [pd[(scores >= lo) & (scores < hi)].mean() for _, lo, hi in BKI_GROUPS]
    checks.append(
        ("группы БКИ заполнены", all(share > 0 for share in bki_shares), " ".join(f"{s:.3f}" for s in bki_shares))
    )
    checks.append(("PD не убывает при ухудшении БКИ", all(np.diff(bki_pd) >= 0), " ".join(f"{v:.3f}" for v in bki_pd)))

    missing_revenue = np.mean([row["monthly_revenue"] is None for row in rows])
    checks.append(("нет наблюдаемой выручки 30–50 %", 0.30 <= missing_revenue <= 0.50, f"{missing_revenue:.3f}"))
    loans = [row for row in rows if row["product_code"] != "OVERDRAFT"]
    checks.append(_share_check("purpose = null у кредитов", [row["purpose"] is None for row in loans], {True: 0.15}))
    checks.append(
        _share_check("collateral = null у кредитов", [row["collateral"] is None for row in loans], {True: 0.35})
    )
    coverage = (
        any(row["bki_current_overdue"] > 0 or row["tax_debt"] > 0 for row in rows)
        and {"DROP", "SPIKE"} <= {row["shock_type"] for row in rows}
        and set(PROFILES) <= {row["seasonality_profile"] for row in rows}
        and set(SOURCES) <= {row["revenue_source"] for row in rows}
        and set(PRODUCTS) <= {row["product_code"] for row in rows}
    )
    checks.append(("покрыты стоп-факторы, аномалии, профили, каналы, продукты", coverage, ""))
    return checks
