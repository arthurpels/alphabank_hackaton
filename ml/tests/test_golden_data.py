import json
import re
from collections import Counter
from pathlib import Path

import yaml

from main import RULES_PATH, purpose_classifier
from scoring.schemas import ProductCode, PurposeRequest

DATA_DIR = Path(__file__).resolve().parents[1] / "eval" / "data"
ANOMALY_ANSWERS = DATA_DIR / "anomaly_answers_golden.jsonl"
PURPOSE = DATA_DIR / "purpose_golden.jsonl"
PURPOSE_FIELDS = {"id", "productCode", "text", "expected", "kind", "note", "disputed"}
PURPOSE_ANSWERS = {"MATCH", "MISMATCH", "UNCLEAR", "NOT_APPLICABLE"}
FIELDS = {"id", "anomaly", "text", "expected", "kind", "note", "disputed"}
KINDS = {"simple", "hard", "trap"}
OTHER = "OTHER"


def load(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def test_anomaly_answers_format() -> None:
    rules = yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))["anomalies"]
    codes = {option["code"] for option in rules["REVENUE_DROP"]["answer_options"]} | {OTHER}
    rows = load(ANOMALY_ANSWERS)

    for row in rows:
        assert set(row) == FIELDS, row["id"]
        assert re.fullmatch(r"A\d{3}", row["id"]), row["id"]
        assert row["anomaly"]["code"] in rules, row["id"]
        assert re.fullmatch(r"\d{4}-\d{2}", row["anomaly"]["month"]), row["id"]
        assert (row["anomaly"]["changePct"] < 0) == (row["anomaly"]["code"] == "REVENUE_DROP"), row["id"]
        assert row["text"].strip() and row["note"].strip(), row["id"]
        assert row["expected"] in codes, row["id"]
        assert row["kind"] in KINDS, row["id"]
        assert isinstance(row["disputed"], bool), row["id"]
    assert [row["id"] for row in rows] == [f"A{n:03d}" for n in range(1, len(rows) + 1)]
    assert len({row["text"].casefold() for row in rows}) == len(rows)


def test_anomaly_answers_coverage() -> None:
    rows = load(ANOMALY_ANSWERS)
    by_code = Counter(row["expected"] for row in rows)
    kinds = Counter(row["kind"] for row in rows)

    assert len(rows) >= 60
    assert min(by_code.values()) >= 15 and len(by_code) == 4
    assert all(kinds[kind] >= len(rows) // 5 for kind in KINDS)
    assert {row["anomaly"]["code"] for row in rows} == {"REVENUE_DROP", "REVENUE_SPIKE"}
    assert any(row["expected"] == OTHER and row["kind"] == "trap" and "инструкц" in row["text"] for row in rows)
    assert sum(row["disputed"] for row in rows) <= len(rows) // 10


def purpose_answer(row: dict) -> str:
    response = purpose_classifier.classify(PurposeRequest(product_code=row["productCode"], goal=row["text"]))
    return response.purpose.value if response.status.value == "DECIDED" else response.status.value


def test_purpose_format() -> None:
    rows = load(PURPOSE)

    for row in rows:
        assert set(row) == PURPOSE_FIELDS, row["id"]
        assert row["productCode"] in {code.value for code in ProductCode}, row["id"]
        assert row["expected"] in PURPOSE_ANSWERS, row["id"]
        assert row["kind"] in KINDS, row["id"]
        assert row["text"].strip() and row["note"].strip(), row["id"]
        assert isinstance(row["disputed"], bool), row["id"]
    assert [row["id"] for row in rows] == [f"P{n:03d}" for n in range(1, len(rows) + 1)]
    assert len({(row["productCode"], row["text"].casefold()) for row in rows}) == len(rows)


def test_purpose_coverage() -> None:
    rows = load(PURPOSE)
    kinds = Counter(row["kind"] for row in rows)
    keyword_products = set(yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))["purpose_keywords"])

    assert len(rows) >= 150
    assert all(kinds[kind] >= len(rows) // 4 for kind in KINDS)
    assert {row["expected"] for row in rows} == PURPOSE_ANSWERS
    for product in keyword_products:
        answers = {row["expected"] for row in rows if row["productCode"] == product}
        assert {"MATCH", "MISMATCH", "UNCLEAR"} <= answers, product
    assert all(row["expected"] == "NOT_APPLICABLE" for row in rows if row["productCode"] not in keyword_products)
    assert sum(row["disputed"] for row in rows) <= len(rows) // 10


def test_purpose_simple_rows_are_decided_by_keywords() -> None:
    for row in load(PURPOSE):
        if row["kind"] == "simple":
            assert purpose_answer(row) == row["expected"], row["id"]

