import json
import re
from collections import Counter
from pathlib import Path

import yaml

from main import RULES_PATH

DATA_DIR = Path(__file__).resolve().parents[1] / "eval" / "data"
ANOMALY_ANSWERS = DATA_DIR / "anomaly_answers_golden.jsonl"
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
