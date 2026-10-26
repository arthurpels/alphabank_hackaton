import copy
from datetime import date

import numpy as np

from calibration.analyze import logistic_regression
from scoring.engine import months_between

CANDIDATE_VERSION = "2026-10-25.cal1"
PP_PER_LOGIT = 1.25

ORDERED_BINS = {
    "credit_history": ["750+", "700–749", "600–699", "550–599", "просрочки или <550"],
    "business_age": ["36+", "12–35", "6–11"],
    "tax_discipline": ["сдана", "есть несданные"],
    "debt_load_pti": ["≤0,05", "0,05–0,10", "0,10–0,25", ">0,25"],
    "debt_load_limit": ["≤0,15", ">0,15"],
}
REFERENCE = {
    "credit_history": ("600–699", 0.0),
    "business_age": ("36+", -0.5),
    "tax_discipline": ("сдана", 0.0),
    "debt_load_pti": ("0,10–0,25", 0.5),
    "debt_load_limit": (">0,15", 0.5),
}
STOPS = ("BKI_OVERDUE", "TAX_DEBT", "LIQUIDATION", "MASS_ADDRESS", "NEW_DIRECTOR", "YOUNG_BUSINESS")


def bins(row: dict) -> dict[str, str]:
    as_of = date.fromisoformat(row["as_of"])
    age = months_between(date.fromisoformat(row["registered_at"]), as_of)
    score = row["credit_score"]
    if row["delinq_12m"] > 0 or score < 550:
        credit = "просрочки или <550"
    elif score < 600:
        credit = "550–599"
    elif score < 700:
        credit = "600–699"
    elif score < 750:
        credit = "700–749"
    else:
        credit = "750+"
    load = row["requested_load"]
    result = {
        "credit_history": credit,
        "business_age": "6–11" if age < 12 else "12–35" if age < 36 else "36+",
        "tax_discipline": "есть несданные" if row["unfiled_reports"] > 0 else "сдана",
    }
    if row["product_code"] == "OVERDRAFT":
        result["debt_load_limit"] = "≤0,15" if load <= 0.15 else ">0,15"
    else:
        result["debt_load_pti"] = (
            "≤0,05" if load <= 0.05 else "0,05–0,10" if load <= 0.10 else "0,10–0,25" if load <= 0.25 else ">0,25"
        )
    return result


def stops(row: dict) -> set[str]:
    as_of = date.fromisoformat(row["as_of"])
    found = set()
    if row["bki_current_overdue"] > 0:
        found.add("BKI_OVERDUE")
    if row["tax_debt"] > 0:
        found.add("TAX_DEBT")
    if row["egrul_status"] != "ACTIVE":
        found.add("LIQUIDATION")
    if row["mass_address"]:
        found.add("MASS_ADDRESS")
    if months_between(date.fromisoformat(row["director_since"]), as_of) < 3:
        found.add("NEW_DIRECTOR")
    if months_between(date.fromisoformat(row["registered_at"]), as_of) < 6:
        found.add("YOUNG_BUSINESS")
    return found


def _monotone(values: list[float]) -> list[float]:
    blocks = [[value] for value in values]
    merged = True
    while merged:
        merged = False
        for index in range(len(blocks) - 1):
            if np.mean(blocks[index]) > np.mean(blocks[index + 1]):
                blocks[index : index + 2] = [blocks[index] + blocks[index + 1]]
                merged = True
                break
    return [float(np.mean(block)) for block in blocks for _ in block]


def fit(rows: list[dict]) -> dict:
    columns = [
        (factor, label) for factor, labels in ORDERED_BINS.items() for label in labels if label != REFERENCE[factor][0]
    ]
    row_bins = [bins(row) for row in rows]
    row_stops = [stops(row) for row in rows]
    matrix = np.zeros((len(rows), len(columns) + len(STOPS)))
    for index, (assigned, found) in enumerate(zip(row_bins, row_stops, strict=True)):
        for column, (factor, label) in enumerate(columns):
            matrix[index, column] = assigned.get(factor) == label
        for offset, code in enumerate(STOPS):
            matrix[index, len(columns) + offset] = code in found
    labels = np.array([row["default_12m"] for row in rows])
    weights = logistic_regression(matrix, labels)
    logit = {column: weights[1 + index] for index, column in enumerate(columns)}

    values = {}
    for factor, ordered in ORDERED_BINS.items():
        reference_label, reference_value = REFERENCE[factor]
        raw = [reference_value + PP_PER_LOGIT * logit.get((factor, label), 0.0) for label in ordered]
        values[factor] = {label: round(2 * value) / 2 for label, value in zip(ordered, _monotone(raw), strict=True)}
    counts = {
        factor: {label: sum(assigned.get(factor) == label for assigned in row_bins) for label in ordered}
        for factor, ordered in ORDERED_BINS.items()
    }
    rates = {
        factor: {
            label: float(labels[[assigned.get(factor) == label for assigned in row_bins]].mean())
            for label in ordered
            if counts[factor][label]
        }
        for factor, ordered in ORDERED_BINS.items()
    }
    return {
        "values": values,
        "logit": {f"{k[0]}|{k[1]}": float(v) for k, v in logit.items()},
        "counts": counts,
        "rates": rates,
    }


def candidate_rules(current: dict, fitted: dict) -> dict:
    rules = copy.deepcopy(current)
    rules["rules_version"] = CANDIDATE_VERSION
    credit = fitted["values"]["credit_history"]
    rules["factors"]["credit_history"]["rules"] = [
        {"if": "delinquencies_12m > 0 or credit_score < 550", "value": credit["просрочки или <550"]},
        {"if": "credit_score >= 750", "value": credit["750+"]},
        {"if": "credit_score >= 700", "value": credit["700–749"]},
        {"if": "credit_score >= 600", "value": credit["600–699"]},
        {"else": credit["550–599"]},
    ]
    age = fitted["values"]["business_age"]
    rules["factors"]["business_age"]["rules"] = [
        {"if": "age_months < 12", "value": age["6–11"]},
        {"if": "age_months < 36", "value": age["12–35"]},
        {"else": age["36+"]},
    ]
    tax = fitted["values"]["tax_discipline"]
    rules["factors"]["tax_discipline"]["rules"] = [
        {"if": "unfiled_reports > 0", "value": tax["есть несданные"]},
        {"else": tax["сдана"]},
    ]
    pti = fitted["values"]["debt_load_pti"]
    limit = fitted["values"]["debt_load_limit"]
    debt = rules["factors"]["debt_load"]
    debt["rules"] = [
        {"if": "pti <= 0.05", "value": pti["≤0,05"]},
        {"if": "pti <= 0.10", "value": pti["0,05–0,10"]},
        {"if": "pti <= 0.25", "value": pti["0,10–0,25"]},
        {"else": pti[">0,25"]},
    ]
    debt["overdraft_rules"] = [
        {"if": "limit_share <= 0.15", "value": limit["≤0,15"]},
        {"else": limit[">0,15"]},
    ]
    known = list(pti.values()) + list(limit.values())
    debt["unknown"] = [min(known), max(known)]
    return rules
