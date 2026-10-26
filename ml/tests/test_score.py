import re

import pytest
import yaml

from main import BASE_DIR, RULES_PATH
from scoring.conditions import Condition, ConditionError
from scoring.engine import Engine, RulesError, annuity_payment
from scoring.schemas import ScoreRequest
from scoring.version import ENGINE_FILES, engine_version
from tests.contract import SCORE_EXAMPLES as CONTRACT_EXAMPLES
from tests.contract import client, persona_request, post


def _post(payload: dict) -> dict:
    return post("/score", payload)


def _factor(body: dict, code: str) -> dict:
    return next(factor for factor in body["factors"] if factor["code"] == code)


def _request(name: str, **changes) -> dict:
    return persona_request(name, **changes)


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


def test_bki_overdue_is_not_removable() -> None:
    body = _post(_request("fns", taxDebt=0, bkiCurrentOverdue=1))
    assert body["stopFactors"] == [{"code": "BKI_OVERDUE", "removable": False, "closeMethod": None}]


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
    assert body["policy"] == {
        "metric": "LIMIT_SHARE",
        "value": 0.3,
        "cap": 0.3,
        "existingPayment": 0,
        "amountInRange": True,
        "ok": True,
    }
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


def test_health_reports_engine_and_rules_versions() -> None:
    first = client.get("/health").json()
    second = client.get("/health").json()
    assert first == second
    assert first["status"] == "ok"
    assert re.fullmatch(r"[0-9a-f]{12}", first["engineVersion"])
    assert first["rulesVersion"] == yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))["rules_version"]


def test_engine_version_changes_with_rules(tmp_path) -> None:
    for name in ENGINE_FILES:
        target = tmp_path / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((BASE_DIR / name).read_bytes())
    before = engine_version(tmp_path)
    rules = tmp_path / "scoring_rules.yaml"
    rules.write_text(rules.read_text(encoding="utf-8") + "\n# правка\n", encoding="utf-8")
    assert engine_version(tmp_path) != before
    assert before == engine_version(BASE_DIR)


def test_request_without_obligations_equals_request_with_zero_obligations() -> None:
    plain = _request("coffee")
    explicit = _request("coffee", bankPayment=0, bkiCredits=[])
    assert _post(plain) == _post(explicit)
    assert _post(plain)["policy"]["existingPayment"] == 0


def test_bank_payment_is_added_to_the_new_payment_in_load() -> None:
    base = _post(_request("coffee"))
    revenue = _request("coffee")["monthlyRevenue"]
    loaded = _post(_request("coffee", bankPayment=5_000_000))
    assert loaded["policy"]["existingPayment"] == 5_000_000
    assert loaded["policy"]["value"] == pytest.approx(base["policy"]["value"] + 5_000_000 / revenue, abs=0.0011)


@pytest.mark.parametrize(
    ("credit", "expected"),
    [
        ({"type": "LOAN", "debt": 15_000_000}, round(annuity_payment(15_000_000, 36, 20))),
        ({"type": "LEASING", "debt": 15_000_000}, round(annuity_payment(15_000_000, 36, 20))),
        ({"type": "CREDIT_CARD", "debt": 4_000_000}, 200_000),
        ({"type": "OVERDRAFT", "debt": 10_000_000}, 500_000),
        ({"type": "LOAN", "debt": 0}, 0),
        ({"type": "OVERDRAFT", "debt": 0}, 0),
    ],
)
def test_bki_credit_payment_is_estimated_by_rules(credit: dict, expected: int) -> None:
    body = _post(_request("wb", bkiCredits=[credit]))
    assert body["policy"]["existingPayment"] == expected


def test_bki_credits_and_bank_payment_are_summed() -> None:
    credits = [{"type": "CREDIT_CARD", "debt": 4_000_000}, {"type": "OVERDRAFT", "debt": 10_000_000}]
    body = _post(_request("wb", bankPayment=1_000_000, bkiCredits=credits))
    assert body["policy"]["existingPayment"] == 1_000_000 + 200_000 + 500_000


def test_existing_payments_can_break_the_policy_cap() -> None:
    before = _post(_request("fns"))
    assert before["policy"]["ok"] and before["policy"]["value"] < before["policy"]["cap"]
    after = _post(_request("fns", bankPayment=40_000_000))
    assert not after["policy"]["ok"] and after["passes"] is False


def test_existing_payments_can_move_load_to_a_worse_band() -> None:
    base = _post(_request("fns"))
    loaded = _post(_request("fns", bankPayment=9_000_000))
    assert _factor(loaded, "debt_load")["value"] > _factor(base, "debt_load")["value"]
    assert loaded["rateTo"] > base["rateTo"]


def test_overdraft_ignores_existing_payments() -> None:
    plain = _post(_request("wb", productCode="OVERDRAFT", amount=12_000_000, termMonths=12))
    loaded = _post(
        _request(
            "wb",
            productCode="OVERDRAFT",
            amount=12_000_000,
            termMonths=12,
            bankPayment=5_000_000,
            bkiCredits=[{"type": "LOAN", "debt": 15_000_000}],
        )
    )
    assert loaded == plain


def test_existing_payments_do_not_matter_while_revenue_is_unknown() -> None:
    plain = _post(_request("builder"))
    loaded = _post(_request("builder", bankPayment=5_000_000))
    assert (loaded["rateFrom"], loaded["rateTo"]) == (plain["rateFrom"], plain["rateTo"])
    assert loaded["policy"]["value"] is None
    assert loaded["policy"]["existingPayment"] == plain["policy"]["existingPayment"] + 5_000_000


@pytest.mark.parametrize("payload", [{"bankPayment": -1}, {"bkiCredits": [{"type": "LOAN", "debt": -1}]}])
def test_negative_obligations_are_rejected(payload: dict) -> None:
    assert client.post("/score", json=_request("coffee", **payload)).status_code == 422
