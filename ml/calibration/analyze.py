import json
import math
from collections import defaultdict
from dataclasses import dataclass

import numpy as np

from calibration.acceptance import score_request
from scoring.engine import Engine
from scoring.schemas import ScoreRequest, ScoreResponse

SCORECARD_FACTORS = ("credit_history", "business_age", "tax_discipline", "debt_load", "revenue_stability")
POLICY_FACTORS = ("purpose", "collateral")
TRAIN_SHARE = 7
L2 = 1e-3


@dataclass(frozen=True)
class Scored:
    row: dict
    observed: ScoreResponse
    full: ScoreResponse
    decision: str
    full_decision: str


def full_request(row: dict) -> ScoreRequest:
    request = score_request(row)
    return request.model_copy(
        update={
            "monthly_revenue": row["true_monthly_revenue"],
            "stability": row["true_stability"],
            "purpose": row["true_purpose"],
            "collateral": row["true_collateral"],
        }
    )


def decide(engine: Engine, request: ScoreRequest, score: ScoreResponse) -> str:
    if any(not stop.removable for stop in score.stop_factors) or score.stop_factors:
        return "REJECTED"
    approve_max = score.thresholds.approve_max
    if score.policy.ok and score.rate_to <= approve_max:
        return "APPROVED"
    if not score.policy.ok or score.rate_from > approve_max:
        return "COUNTER_OFFER" if engine.offer_grid(request).offers else "REJECTED"
    return "UNDERWRITING"


def score_population(rows: list[dict], engine: Engine) -> list[Scored]:
    scored = []
    for row in rows:
        observed_request, complete_request = score_request(row), full_request(row)
        observed, full = engine.score(observed_request), engine.score(complete_request)
        scored.append(
            Scored(
                row=row,
                observed=observed,
                full=full,
                decision=decide(engine, observed_request, observed),
                full_decision=decide(engine, complete_request, full),
            )
        )
    return scored


def auc(scores: np.ndarray, labels: np.ndarray) -> float:
    order = scores.argsort(kind="mergesort")
    ranks = np.empty(len(scores))
    sorted_scores = scores[order]
    start = 0
    while start < len(scores):
        end = start
        while end + 1 < len(scores) and sorted_scores[end + 1] == sorted_scores[start]:
            end += 1
        ranks[order[start : end + 1]] = (start + end) / 2 + 1
        start = end + 1
    positives = labels.sum()
    negatives = len(labels) - positives
    return float((ranks[labels == 1].sum() - positives * (positives + 1) / 2) / (positives * negatives))


def logistic_regression(features: np.ndarray, labels: np.ndarray) -> np.ndarray:
    x = np.hstack([np.ones((len(features), 1)), features])
    weights = np.zeros(x.shape[1])
    penalty = L2 * np.eye(x.shape[1])
    penalty[0, 0] = 0
    for _ in range(50):
        probabilities = 1 / (1 + np.exp(-x @ weights))
        gradient = x.T @ (probabilities - labels) + penalty @ weights
        hessian = (x * (probabilities * (1 - probabilities))[:, None]).T @ x + penalty
        step = np.linalg.solve(hessian, gradient)
        weights -= step
        if np.abs(step).max() < 1e-8:
            break
    return weights


def factor_key(score: ScoreResponse, code: str) -> str | None:
    factor = next(item for item in score.factors if item.code == code)
    if not factor.applicable:
        return None
    if not factor.known:
        return "неизвестно"
    prefix = f"{score.policy.metric}:" if code == "debt_load" and score.policy.metric else ""
    return f"{prefix}{factor.value:+.1f}"


def bins_table(scored: list[Scored], labels: np.ndarray) -> tuple[list[dict], np.ndarray, list[tuple[str, str]]]:
    columns: list[tuple[str, str]] = []
    keys_by_factor: dict[str, list[str | None]] = {}
    for code in SCORECARD_FACTORS + POLICY_FACTORS:
        keys = [factor_key(item.full, code) for item in scored]
        keys_by_factor[code] = keys
        present = sorted({key for key in keys if key is not None})
        counts = {key: sum(k == key for k in keys) for key in present}
        reference = max(present, key=lambda key: counts[key])
        columns += [(code, key) for key in present if key != reference]
    stops = sorted({stop.code for item in scored for stop in item.full.stop_factors})
    matrix = np.zeros((len(scored), len(columns) + len(stops)))
    for row_index in range(len(scored)):
        for column_index, (code, key) in enumerate(columns):
            matrix[row_index, column_index] = keys_by_factor[code][row_index] == key
        codes = {stop.code for stop in scored[row_index].full.stop_factors}
        for offset, stop in enumerate(stops):
            matrix[row_index, len(columns) + offset] = stop in codes
    weights = logistic_regression(matrix, labels)
    coefficient = {column: weights[1 + index] for index, column in enumerate(columns)}

    table = []
    total_good, total_bad = (labels == 0).sum(), labels.sum()
    for code in SCORECARD_FACTORS + POLICY_FACTORS:
        keys = keys_by_factor[code]
        for key in sorted({key for key in keys if key is not None}):
            mask = np.array([k == key for k in keys])
            bad, good = labels[mask].sum(), (labels[mask] == 0).sum()
            woe = math.log(((good + 0.5) / total_good) / ((bad + 0.5) / total_bad))
            yaml_value = None if key == "неизвестно" else float(key.split(":")[-1])
            table.append(
                {
                    "factor": code,
                    "bin": key,
                    "yaml": yaml_value,
                    "n": int(mask.sum()),
                    "default_rate": float(labels[mask].mean()),
                    "woe": woe,
                    "logit": float(coefficient.get((code, key), 0.0)),
                }
            )
    return table, weights, stops


def suggested_pp(table: list[dict]) -> dict[tuple[str, str], float]:
    by_factor = defaultdict(list)
    for row in table:
        if row["yaml"] is not None and row["factor"] in SCORECARD_FACTORS:
            by_factor[row["factor"]].append(row)
    centered_yaml, centered_logit = [], []
    for rows in by_factor.values():
        yaml_mean = np.mean([row["yaml"] for row in rows])
        logit_mean = np.mean([row["logit"] for row in rows])
        centered_yaml += [row["yaml"] - yaml_mean for row in rows]
        centered_logit += [row["logit"] - logit_mean for row in rows]
    scale = float(np.dot(centered_yaml, centered_logit) / np.dot(centered_logit, centered_logit))
    result = {}
    for rows in by_factor.values():
        yaml_mean = np.mean([row["yaml"] for row in rows])
        logit_mean = np.mean([row["logit"] for row in rows])
        for row in rows:
            result[(row["factor"], row["bin"])] = round(2 * (yaml_mean + scale * (row["logit"] - logit_mean))) / 2
    result[("_scale", "pp_per_logit")] = scale
    return result


def fine_bins(rows: list[dict], labels: np.ndarray, field: str, edges: list[float]) -> list[dict]:
    values = np.array([row[field] for row in rows], dtype=float)
    result = []
    for low, high in zip(edges, edges[1:], strict=False):
        mask = (values >= low) & (values < high)
        if mask.sum():
            result.append(
                {"range": f"{low:g}–{high:g}", "n": int(mask.sum()), "default_rate": float(labels[mask].mean())}
            )
    return result


def analyze(rows: list[dict], engine: Engine) -> dict:
    scored = score_population(rows, engine)
    labels = np.array([item.row["default_12m"] for item in scored])
    true_pd = np.array([item.row["true_pd_12m"] for item in scored])
    observed_mid = np.array([(item.observed.rate_from + item.observed.rate_to) / 2 for item in scored])
    full_mid = np.array([(item.full.rate_from + item.full.rate_to) / 2 for item in scored])
    clean = np.array([not item.full.stop_factors for item in scored])

    table, _, stops = bins_table(scored, labels)
    train = np.array([int(item.row["company_id"][-1]) < TRAIN_SHARE for item in scored])
    columns_matrix = []
    for item in scored:
        columns_matrix.append(
            [factor.value if factor.known and factor.applicable else 0.0 for factor in item.full.factors]
        )
    features = np.array(columns_matrix)
    weights = logistic_regression(features[train], labels[train])
    learned = 1 / (1 + np.exp(-(np.hstack([np.ones((len(features), 1)), features]) @ weights)))

    outcomes = {}
    for name, attribute in (("как подана", "decision"), ("полная информация", "full_decision")):
        groups = defaultdict(list)
        for item, label in zip(scored, labels, strict=True):
            groups[getattr(item, attribute)].append(label)
        outcomes[name] = {
            decision: {"share": len(values) / len(scored), "default_rate": float(np.mean(values))}
            for decision, values in sorted(groups.items())
        }

    rate_buckets = defaultdict(list)
    for mid, label, is_clean in zip(full_mid, labels, clean, strict=True):
        if is_clean:
            rate_buckets[math.floor(mid)].append(label)

    return {
        "size": len(rows),
        "default_rate": float(labels.mean()),
        "auc": {
            "истинная PD (потолок)": auc(true_pd, labels),
            "вилка, полная информация": auc(full_mid, labels),
            "вилка, полная информация, без стоп-факторов": auc(full_mid[clean], labels[clean]),
            "вилка как подана": auc(observed_mid, labels),
            "логрегрессия на факторах карты (тест 30 %)": auc(learned[~train], labels[~train]),
        },
        "outcomes": outcomes,
        "bins": table,
        "suggested": {f"{k[0]}|{k[1]}": v for k, v in suggested_pp(table).items()},
        "stop_factors": {
            code: {
                "n": int(sum(any(s.code == code for s in item.full.stop_factors) for item in scored)),
                "default_rate": float(
                    np.mean(
                        [
                            label
                            for item, label in zip(scored, labels, strict=True)
                            if any(s.code == code for s in item.full.stop_factors)
                        ]
                    )
                ),
            }
            for code in stops
        },
        "rate_buckets": {
            f"{bucket}–{bucket + 1} %": {"n": len(values), "default_rate": float(np.mean(values))}
            for bucket, values in sorted(rate_buckets.items())
        },
        "credit_score_fine": fine_bins(
            rows, labels, "credit_score", [300, 500, 550, 600, 625, 650, 675, 700, 725, 750, 851]
        ),
        "load_fine": fine_bins(
            [row for row in rows if row["product_code"] != "OVERDRAFT"],
            np.array([row["default_12m"] for row in rows if row["product_code"] != "OVERDRAFT"]),
            "requested_load",
            [0, 0.025, 0.05, 0.075, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 2],
        ),
    }


if __name__ == "__main__":
    from calibration.generate import OUT_DIR, RULES_PATH

    population = [json.loads(line) for line in (OUT_DIR / "population.jsonl").read_text(encoding="utf-8").splitlines()]
    print(json.dumps(analyze(population, Engine.from_yaml(RULES_PATH)), ensure_ascii=False, indent=1))
