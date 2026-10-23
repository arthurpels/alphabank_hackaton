import copy
from pathlib import Path

import pytest
import yaml
from fastapi.testclient import TestClient

from main import RULES_PATH, app
from scoring.conditions import Condition, ConditionError
from scoring.engine import Engine, RulesError
from scoring.schemas import ScoreRequest

CONTRACT_PATH = Path(__file__).resolve().parents[2] / "docs" / "openapi-ml.yaml"


def _load_contract_examples() -> dict[str, tuple[dict, dict]]:
    contract = yaml.safe_load(CONTRACT_PATH.read_text(encoding="utf-8"))
    operation = contract["paths"]["/score"]["post"]
    requests = operation["requestBody"]["content"]["application/json"]["examples"]
    responses = operation["responses"]["200"]["content"]["application/json"]["examples"]
    return {name: (example["value"], responses[name]["value"]) for name, example in requests.items()}


CONTRACT_EXAMPLES = _load_contract_examples()

client = TestClient(app)


def _post(payload: dict) -> dict:
    response = client.post("/score", json=payload)
    assert response.status_code == 200, response.text
    return response.json()


def _factor(body: dict, code: str) -> dict:
    return next(factor for factor in body["factors"] if factor["code"] == code)


def _request(name: str, **changes) -> dict:
    payload = copy.deepcopy(CONTRACT_EXAMPLES[name][0])
    payload.update(changes)
    return payload


def test_contract_covers_all_personas() -> None:
    assert {"coffee", "coffeeStart", "builder", "wb", "fns", "shell"} <= CONTRACT_EXAMPLES.keys()


@pytest.mark.parametrize("name", sorted(CONTRACT_EXAMPLES))
def test_score_matches_contract_example(name: str) -> None:
    request, expected = CONTRACT_EXAMPLES[name]
    assert _post(request) == expected


@pytest.mark.parametrize("name", sorted(CONTRACT_EXAMPLES))
def test_score_is_deterministic(name: str) -> None:
    request = CONTRACT_EXAMPLES[name][0]
    fresh_engine = Engine.from_yaml(RULES_PATH)
    assert _post(request) == _post(request)
    direct = fresh_engine.score(ScoreRequest.model_validate(request))
    assert _post(request) == direct.model_dump(by_alias=True, mode="json")


def test_persona_outcomes_follow_scoring_model() -> None:
    coffee = _post(_request("coffee"))
    assert coffee["passes"] and coffee["rateTo"] <= coffee["thresholds"]["approveMax"]

    builder = _post(_request("builder"))
    assert builder["rateFrom"] < builder["thresholds"]["approveMax"] < builder["rateTo"]

    wb = _post(_request("wb"))
    assert not wb["policy"]["ok"] and wb["policy"]["value"] > wb["policy"]["cap"]

    fns = _post(_request("fns"))
    assert [stop["code"] for stop in fns["stopFactors"]] == ["TAX_DEBT"]
    assert fns["stopFactors"][0] == {"code": "TAX_DEBT", "removable": True, "closeMethod": "PAY"}

    shell = _post(_request("shell"))
    assert {stop["code"] for stop in shell["stopFactors"]} == {"NEW_DIRECTOR", "MASS_ADDRESS"}
    assert not any(stop["removable"] for stop in shell["stopFactors"])


def test_closing_spots_narrows_fork() -> None:
    start = _post(_request("coffeeStart"))
    finish = _post(_request("coffee"))
    assert start["rateTo"] - start["rateFrom"] > finish["rateTo"] - finish["rateFrom"]
    assert finish["rateFrom"] >= start["rateFrom"] and finish["rateTo"] <= start["rateTo"]


@pytest.mark.parametrize("collateral", ["GUARANTEE", "PLEDGE"])
def test_guarantee_and_pledge_give_same_discount(collateral: str) -> None:
    body = _post(_request("coffee", collateral=collateral))
    assert _factor(body, "collateral")["value"] == -1.5


def test_declined_collateral_is_neutral() -> None:
    assert _factor(_post(_request("coffee", collateral="DECLINED")), "collateral")["value"] == 0.0


def test_open_anomaly_keeps_stability_unknown() -> None:
    factor = _factor(_post(_request("coffee", stability="ANOMALY_OPEN")), "revenue_stability")
    assert factor["known"] is False and factor["spotPricePp"] == 2.5


def test_overdraft_uses_limit_share_and_skips_purpose_and_collateral() -> None:
    body = _post(_request("wb", productCode="OVERDRAFT", amount=12_000_000, termMonths=12))
    assert body["policy"] == {"metric": "LIMIT_SHARE", "value": 0.3, "cap": 0.3, "amountInRange": True, "ok": True}
    for code in ("purpose", "collateral"):
        factor = _factor(body, code)
        assert (factor["applicable"], factor["known"], factor["value"], factor["spotPricePp"]) == (
            False,
            False,
            None,
            None,
        )
    assert body["rateFrom"] == body["rateTo"] == 22.0


def test_amount_outside_product_range_fails_policy() -> None:
    body = _post(_request("coffee", amount=20_000_000))
    assert body["policy"]["amountInRange"] is False and body["passes"] is False


@pytest.mark.parametrize(
    "changes",
    [
        {"amount": 0},
        {"termMonths": 0},
        {"monthlyRevenue": 0},
        {"productCode": "MORTGAGE"},
        {"stability": "UNKNOWN"},
        {"asOf": "23.10.2026"},
    ],
)
def test_invalid_request_is_rejected(changes: dict) -> None:
    assert client.post("/score", json=_request("coffee", **changes)).status_code == 422


def test_health() -> None:
    assert client.get("/health").json() == {"status": "ok"}


@pytest.mark.parametrize(
    "source",
    ["__import__('os').system('ls')", "credit_score.__class__", "[1, 2]", "credit_score + 1 > 2", "lambda: 1"],
)
def test_condition_rejects_anything_but_comparisons(source: str) -> None:
    with pytest.raises(ConditionError):
        Condition(source).evaluate({"credit_score": 700})


def test_condition_supports_dotted_names_and_chains() -> None:
    condition = Condition("egrul.status != 'ACTIVE' or 0 < age_months < 6")
    assert condition.evaluate({"egrul.status": "ACTIVE", "age_months": 3})
    assert not condition.evaluate({"egrul.status": "ACTIVE", "age_months": 7})


def test_rules_with_unknown_name_fail_on_load() -> None:
    raw = yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))
    raw["factors"]["credit_history"]["rules"][0]["if"] = "credit_rating > 1"
    with pytest.raises(RulesError, match="credit_rating"):
        Engine(raw)
