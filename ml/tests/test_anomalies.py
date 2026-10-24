import copy

import pytest
import yaml

from main import RULES_PATH
from scoring.conditions import Condition, ConditionError
from scoring.engine import Engine, RulesError
from tests.contract import ANOMALY_EXAMPLES, client, post


def _anomalies(*series: tuple[str, list[tuple[str, int]]]) -> list[dict]:
    payload = {
        "series": [
            {"source": source, "points": [{"month": month, "value": value} for month, value in points]}
            for source, points in series
        ]
    }
    return post("/anomalies", payload)["anomalies"]


def _pair(previous: int, current: int, months: tuple[str, str] = ("2026-02", "2026-03")) -> list[dict]:
    return _anomalies(("OFD", [(months[0], previous), (months[1], current)]))


@pytest.mark.parametrize("name", sorted(ANOMALY_EXAMPLES))
def test_anomalies_match_contract_example(name: str) -> None:
    request, expected = ANOMALY_EXAMPLES[name]
    assert post("/anomalies", request) == expected


@pytest.mark.parametrize("name", sorted(ANOMALY_EXAMPLES))
def test_anomalies_are_deterministic(name: str) -> None:
    request = ANOMALY_EXAMPLES[name][0]
    assert post("/anomalies", request) == post("/anomalies", request)


def test_point_order_does_not_matter() -> None:
    request, expected = copy.deepcopy(ANOMALY_EXAMPLES["coffee"])
    request["series"][0]["points"].reverse()
    assert post("/anomalies", request) == expected


def test_months_with_gap_are_not_compared() -> None:
    request = copy.deepcopy(ANOMALY_EXAMPLES["coffee"][0])
    request["series"][0]["points"] = [p for p in request["series"][0]["points"] if p["month"] != "2026-02"]
    assert post("/anomalies", request) == {"anomalies": []}


def test_december_and_january_are_neighbours() -> None:
    assert [a["month"] for a in _pair(1_000_000, 500_000, ("2025-12", "2026-01"))] == ["2026-01"]


@pytest.mark.parametrize(
    ("previous", "current", "expected"),
    [
        (1_000_000, 700_000, []),
        (1_000_000, 690_000, ["REVENUE_DROP"]),
        (1_000_000, 3_000_000, []),
        (1_000_000, 3_010_000, ["REVENUE_SPIKE"]),
    ],
)
def test_thresholds_are_strict(previous: int, current: int, expected: list[str]) -> None:
    assert [a["code"] for a in _pair(previous, current)] == expected


def test_zero_month_is_not_a_base_for_comparison() -> None:
    assert _pair(0, 5_000_000) == []


@pytest.mark.parametrize(
    ("current", "title"),
    [
        (4_000_000, "Почему в марте обороты выросли в 4 раза"),
        (5_000_000, "Почему в марте обороты выросли в 5 раз"),
        (5_420_000, "Почему в марте обороты выросли в 5,4 раза"),
    ],
)
def test_spike_title_wording(current: int, title: str) -> None:
    assert _pair(1_000_000, current)[0]["title"] == title


def test_drop_rounds_half_away_from_zero() -> None:
    anomaly = _pair(1_000_000, 545_000)[0]
    assert anomaly["changePct"] == -46
    assert anomaly["title"] == "Почему в марте выручка упала на 46 %"


def test_anomalies_keep_series_order() -> None:
    found = _anomalies(
        ("OFD", [("2026-02", 1_000_000), ("2026-03", 400_000)]),
        ("ACQUIRING", [("2026-01", 1_000_000), ("2026-02", 5_000_000)]),
    )
    assert [(a["source"], a["code"]) for a in found] == [("OFD", "REVENUE_DROP"), ("ACQUIRING", "REVENUE_SPIKE")]


@pytest.mark.parametrize(
    "points",
    [
        [{"month": "2026-03", "value": 1}, {"month": "2026-03", "value": 2}],
        [{"month": "2026-13", "value": 1}],
        [{"month": "2026-03", "value": -1}],
    ],
)
def test_invalid_series_is_rejected(points: list[dict]) -> None:
    response = client.post("/anomalies", json={"series": [{"source": "OFD", "points": points}]})
    assert response.status_code == 422


def test_negative_numbers_in_conditions() -> None:
    assert Condition("change_pct < -30").evaluate({"change_pct": -31})
    assert not Condition("change_pct < -30").evaluate({"change_pct": -30})
    with pytest.raises(ConditionError):
        Condition("-change_pct > 30")


def test_anomaly_rule_with_unknown_name_fails_on_load() -> None:
    raw = yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))
    raw["anomalies"]["REVENUE_DROP"]["if"] = "drop > 30"
    with pytest.raises(RulesError, match="drop"):
        Engine(raw)
