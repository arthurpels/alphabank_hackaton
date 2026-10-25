import argparse
import hashlib
import json
import math
import platform
from datetime import date
from pathlib import Path

import numpy as np

from scoring.engine import Engine
from scoring.schemas import AnomaliesRequest, Series

ML_DIR = Path(__file__).resolve().parents[1]
SPEC_PATH = ML_DIR.parent / "docs" / "synthetic-population.md"
RULES_PATH = ML_DIR / "scoring_rules.yaml"
OUT_DIR = Path(__file__).resolve().parent / "out"

SPEC_VERSION = "1.0.0"
POPULATION_SIZE = 10_000
SEED = 20261023
AS_OF = date(2026, 10, 23)
MONTHS = [f"{2024 + (9 + i) // 12}-{(9 + i) % 12 + 1:02d}" for i in range(24)]

INDUSTRIES = ("TRADE", "PROFESSIONAL", "CONSTRUCTION", "MANUFACTURING", "SERVICES", "TRANSPORT")
INDUSTRY_P = (0.30, 0.15, 0.13, 0.12, 0.20, 0.10)
AGE_RANGES = ((0, 5), (6, 11), (12, 35), (36, 119), (120, 240))
AGE_P = (0.02, 0.08, 0.30, 0.40, 0.20)
REVENUE = {
    "TRADE": (1_400_000, 0.10),
    "PROFESSIONAL": (900_000, 0.12),
    "CONSTRUCTION": (1_600_000, 0.18),
    "MANUFACTURING": (1_800_000, 0.14),
    "SERVICES": (800_000, 0.10),
    "TRANSPORT": (1_300_000, 0.12),
}
PROFILES = ("FLAT", "RETAIL_NY", "SUMMER", "B2B_YEAR_END")
PROFILE_P = {
    "TRADE": (0.35, 0.45, 0.05, 0.15),
    "PROFESSIONAL": (0.55, 0.10, 0.10, 0.25),
    "CONSTRUCTION": (0.40, 0.05, 0.40, 0.15),
    "MANUFACTURING": (0.60, 0.05, 0.10, 0.25),
    "SERVICES": (0.50, 0.15, 0.25, 0.10),
    "TRANSPORT": (0.55, 0.10, 0.20, 0.15),
}
MULTIPLIERS = {
    "FLAT": (1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
    "RETAIL_NY": (0.72, 0.85, 0.95, 0.97, 1.00, 1.02, 0.95, 0.93, 1.03, 1.08, 1.18, 1.32),
    "SUMMER": (0.82, 0.84, 0.92, 1.00, 1.10, 1.20, 1.28, 1.25, 1.08, 0.94, 0.83, 0.74),
    "B2B_YEAR_END": (0.78, 0.85, 1.03, 1.00, 1.02, 1.04, 0.93, 0.90, 1.06, 1.08, 1.14, 1.17),
}
PRODUCTS = ("EQUIPMENT", "WORKING_CAPITAL", "OVERDRAFT")
PRODUCT_P = {
    "TRADE": (0.20, 0.65, 0.15),
    "PROFESSIONAL": (0.25, 0.60, 0.15),
    "CONSTRUCTION": (0.45, 0.50, 0.05),
    "MANUFACTURING": (0.45, 0.50, 0.05),
    "SERVICES": (0.25, 0.60, 0.15),
    "TRANSPORT": (0.45, 0.50, 0.05),
}
SOURCES = ("OFD", "ACQUIRING", "MARKETPLACE", "DOCUMENT", None)
SOURCE_P = {
    "TRADE": (0.30, 0.25, 0.25, 0.05, 0.15),
    "PROFESSIONAL": (0.05, 0.15, 0.05, 0.35, 0.40),
    "CONSTRUCTION": (0.05, 0.10, 0.0, 0.45, 0.40),
    "MANUFACTURING": (0.10, 0.20, 0.05, 0.25, 0.40),
    "SERVICES": (0.25, 0.30, 0.05, 0.10, 0.30),
    "TRANSPORT": (0.05, 0.25, 0.0, 0.25, 0.45),
}
EXTERNAL_SOURCES = ("OFD", "ACQUIRING", "MARKETPLACE")
CALC_RATE = 0.20 / 12


def pick(rng: np.random.Generator, items: tuple, probabilities: tuple) -> object:
    u = rng.random()
    total = 0.0
    for item, probability in zip(items, probabilities, strict=True):
        total += probability
        if u < total:
            return item
    return items[-1]


def sigmoid(x: float) -> float:
    return 1 / (1 + math.exp(-x))


def round_half_up(value: float, step: int = 1) -> int:
    return int(math.floor(value / step + 0.5)) * step


def months_before(day: date, months: int) -> date:
    index = day.year * 12 + day.month - 1 - months
    return date(index // 12, index % 12 + 1, day.day)


def annuity(term: int) -> float:
    return CALC_RATE * (1 + CALC_RATE) ** term / ((1 + CALC_RATE) ** term - 1)


def company(index: int, engine: Engine, seed: int = SEED) -> dict:
    rng = np.random.Generator(np.random.PCG64([seed, index]))
    q = rng.normal()
    legal_form = "IP" if rng.random() < 0.65 else "OOO"
    egrul_status = "LIQUIDATED" if rng.random() < 0.01 else "ACTIVE"
    mass_address = bool(rng.random() < 0.015)

    industry = pick(rng, INDUSTRIES, INDUSTRY_P)
    low, high = pick(rng, AGE_RANGES, AGE_P)
    age_months = int(rng.integers(low, high + 1))
    director_months = age_months
    if legal_form == "OOO":
        if rng.random() < 0.03:
            director_months = int(rng.integers(0, min(2, age_months) + 1))
        elif age_months >= 3 and rng.random() < 0.08:
            director_months = int(rng.integers(3, min(age_months, 60) + 1))

    median, sigma = REVENUE[industry]
    base_revenue = min(max(math.exp(math.log(median) - 0.15 * q + rng.normal(0, 0.75)), 100_000), 15_000_000)
    profile = pick(rng, PROFILES, PROFILE_P[industry])
    raw = [base_revenue * MULTIPLIERS[profile][int(month[5:]) - 1] * math.exp(rng.normal(0, sigma)) for month in MONTHS]
    shock = pick(rng, ("DROP", "SPIKE", "NONE"), (0.08, 0.03, 0.89))
    if shock != "NONE":
        position = int(rng.integers(2, 25))
        raw[position - 1] *= rng.uniform(0.45, 0.70) if shock == "DROP" else rng.uniform(3.2, 5.5)
    scale = base_revenue / (sum(raw) / len(raw))
    rubles = [max(round_half_up(value * scale), 1) for value in raw]
    kopecks = [value * 100 for value in rubles]
    true_monthly_revenue = round_half_up(sum(kopecks) / len(kopecks))
    mean = sum(kopecks) / len(kopecks)
    revenue_cv = min(math.sqrt(sum((value - mean) ** 2 for value in kopecks) / len(kopecks)) / mean, 1.0)

    credit_score = round_half_up(min(max(665 - 55 * q + rng.normal(0, 55), 300), 850))
    current_overdue = 0
    if rng.random() < sigmoid(-5.1 + 0.035 * (650 - credit_score)):
        amount = min(max(math.exp(rng.normal(math.log(60_000), 0.8)), 1_000), 2_000_000)
        current_overdue = round_half_up(amount, 100) * 100
    delinq_12m = 0
    if rng.random() < sigmoid(-3.6 + 0.040 * (650 - credit_score)):
        delinq_12m = pick(rng, (1, 2, 3), (0.60, 0.30, 0.10))
    elif current_overdue > 0:
        delinq_12m = 1
    tax_debt = 0
    if rng.random() < sigmoid(-3.1 + 0.5 * q):
        tax_debt = round_half_up(min(max(math.exp(rng.normal(math.log(25_000), 0.9)), 500), 1_000_000)) * 100
    unfiled_reports = pick(rng, (1, 2, 3), (0.80, 0.15, 0.05)) if rng.random() < 0.06 else 0

    product_code = pick(rng, PRODUCTS, PRODUCT_P[industry])
    product = engine.products[product_code]
    term_months = product.terms[int(rng.integers(0, len(product.terms)))]
    target_load = min(max(0.18 + 0.05 * q + rng.normal(0, 0.12), 0.02), 0.65)
    revenue_rub = true_monthly_revenue / 100
    if product.metric == "LIMIT_SHARE":
        amount_rub = math.floor(target_load * revenue_rub / 10_000) * 10_000
    else:
        amount_rub = round_half_up(target_load * revenue_rub / annuity(term_months), 1_000)
    amount = min(max(amount_rub * 100, product.amount_min), product.amount_max)
    if product.metric == "LIMIT_SHARE":
        requested_load = amount / true_monthly_revenue
    else:
        requested_load = amount * annuity(term_months) / true_monthly_revenue

    true_purpose = true_collateral = None
    if product_code != "OVERDRAFT":
        true_purpose = "MATCH" if rng.random() < 0.90 else "MISMATCH"
        true_collateral = pick(rng, ("GUARANTEE", "PLEDGE", "DECLINED"), (0.20, 0.10, 0.70))

    revenue_source = pick(rng, SOURCES, SOURCE_P[industry])
    if revenue_source in EXTERNAL_SOURCES:
        consent_given = bool(rng.random() < 0.90)
    else:
        consent_given = revenue_source == "DOCUMENT"
    true_series = {
        "source": revenue_source or "OFD",
        "points": [{"month": m, "value": v} for m, v in zip(MONTHS, kopecks, strict=False)],
    }
    if revenue_source == "DOCUMENT":
        true_stability = "DOCUMENT"
    else:
        found = engine.detect_anomalies(AnomaliesRequest(series=[Series.model_validate(true_series)])).anomalies
        true_stability = "ANOMALY_OPEN" if found else "CLEAN"

    director_new = director_months < 3
    z = (
        -4.0
        + 1.10 * (650 - credit_score) / 100
        + 0.45 * min(max((36 - age_months) / 36, 0), 3)
        + 0.35 * min(max((1_000_000 - revenue_rub) / 1_000_000, -2), 2)
        + 2.20 * min(max(requested_load, 0), 1)
        + 0.80 * min(max(revenue_cv, 0), 1)
        + 0.90 * (delinq_12m > 0)
        + 1.80 * (current_overdue > 0)
        + 0.45 * (tax_debt > 0)
        + 0.35 * (unfiled_reports > 0)
        + 0.65 * (shock != "NONE")
        + 0.80 * mass_address
        + 1.20 * (egrul_status != "ACTIVE")
        + 0.50 * director_new
    )
    true_pd = 1 / (1 + math.exp(-z))
    default_12m = int(rng.random() < true_pd)

    purpose = true_purpose
    collateral = true_collateral
    if product_code != "OVERDRAFT":
        if rng.random() < 0.15:
            purpose = None
        if rng.random() < 0.35:
            collateral = None
    revenue_observed = (revenue_source in EXTERNAL_SOURCES and consent_given) or revenue_source == "DOCUMENT"
    series = [true_series] if revenue_source in EXTERNAL_SOURCES and consent_given else []

    return {
        "company_id": f"SYN-{index:06d}",
        "as_of": AS_OF.isoformat(),
        "legal_form": legal_form,
        "okved_group": industry,
        "egrul_status": egrul_status,
        "registered_at": months_before(AS_OF, age_months).isoformat(),
        "director_since": months_before(AS_OF, director_months).isoformat(),
        "mass_address": mass_address,
        "credit_score": credit_score,
        "delinq_12m": delinq_12m,
        "bki_current_overdue": current_overdue,
        "tax_debt": tax_debt,
        "unfiled_reports": unfiled_reports,
        "product_code": product_code,
        "amount": amount,
        "term_months": term_months,
        "requested_load": requested_load,
        "revenue_source": revenue_source,
        "consent_given": consent_given,
        "series": series,
        "true_monthly_revenue": true_monthly_revenue,
        "monthly_revenue": true_monthly_revenue if revenue_observed else None,
        "seasonality_profile": profile,
        "revenue_cv": revenue_cv,
        "shock_type": shock,
        "true_stability": true_stability,
        "stability": true_stability if revenue_observed else None,
        "true_purpose": true_purpose,
        "purpose": purpose,
        "true_collateral": true_collateral,
        "collateral": collateral,
        "true_pd_12m": round(true_pd, 8),
        "default_12m": default_12m,
    }


def serialize(row: dict) -> str:
    return json.dumps(row, ensure_ascii=False, sort_keys=True)


def generate(size: int = POPULATION_SIZE, seed: int = SEED, engine: Engine | None = None) -> list[dict]:
    engine = engine or Engine.from_yaml(RULES_PATH)
    return [company(index, engine, seed) for index in range(1, size + 1)]


def write(rows: list[dict], out_dir: Path, seed: int, engine: Engine) -> dict:
    out_dir.mkdir(parents=True, exist_ok=True)
    population = out_dir / "population.jsonl"
    population.write_text("".join(serialize(row) + "\n" for row in rows), encoding="utf-8", newline="\n")
    metadata = {
        "spec_version": SPEC_VERSION,
        "population_size": len(rows),
        "seed": seed,
        "as_of": AS_OF.isoformat(),
        "last_month": MONTHS[-1],
        "series_length": len(MONTHS),
        "rules_version": engine.rules_version,
        "python": platform.python_version(),
        "numpy": np.__version__,
        "spec_sha256": hashlib.sha256(SPEC_PATH.read_bytes()).hexdigest(),
        "population_sha256": hashlib.sha256(population.read_bytes()).hexdigest(),
    }
    (out_dir / "metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description="Генератор синтетической популяции по docs/synthetic-population.md")
    parser.add_argument("--size", type=int, default=POPULATION_SIZE)
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--out", type=Path, default=OUT_DIR)
    args = parser.parse_args()
    engine = Engine.from_yaml(RULES_PATH)
    metadata = write(generate(args.size, args.seed, engine), args.out, args.seed, engine)
    print(json.dumps(metadata, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
