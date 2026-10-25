import yaml

from main import RULES_PATH
from tests.contract import client

RAW_RULES = yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))
OPENAPI_PATH = RULES_PATH.parent.parent / "docs" / "openapi-ml.yaml"


def _get() -> dict:
    response = client.get("/rules")
    assert response.status_code == 200, response.text
    return response.json()


def _by_code(items: list[dict], code: str) -> dict:
    return next(item for item in items if item["code"] == code)


def _rules(raw: list[dict]) -> list[dict]:
    return [{"condition": rule.get("if"), "value": rule.get("value", rule.get("else"))} for rule in raw]


def test_rules_endpoint_is_deterministic_and_exposes_only_public_rules() -> None:
    first = client.get("/rules")
    second = client.get("/rules")
    assert first.status_code == 200, first.text
    assert first.content == second.content
    body = first.json()
    assert set(body) == {"rulesVersion", "calcRatePct", "products", "factors", "stopFactors"}
    assert body["rulesVersion"] == RAW_RULES["rules_version"]
    assert body["calcRatePct"] == RAW_RULES["calc_rate_pct"]


def test_rules_matches_openapi_example() -> None:
    contract = yaml.safe_load(OPENAPI_PATH.read_text(encoding="utf-8"))
    operation = contract["paths"]["/rules"]["get"]
    assert operation["operationId"] == "rules"
    assert operation["tags"] == ["scoring"]
    example = operation["responses"]["200"]["content"]["application/json"]["examples"]["current"]["value"]
    assert _get() == example


def test_products_preserve_yaml_order_and_semantics() -> None:
    products = _get()["products"]
    assert [product["code"] for product in products] == list(RAW_RULES["products"])
    for product in products:
        raw = RAW_RULES["products"][product["code"]]
        metric = "LIMIT_SHARE" if "limit_cap" in raw else "PTI"
        cap = raw["limit_cap"] if metric == "LIMIT_SHARE" else raw["pti_cap"]
        assert product == {
            "code": product["code"],
            "name": raw["name"],
            "baseRate": raw["base_rate"],
            "thresholds": {"approveMax": raw["approve_max"], "rejectMin": raw["reject_min"]},
            "amount": {"min": raw["amount"][0] * 100, "max": raw["amount"][1] * 100},
            "terms": raw["terms"],
            "policy": {"metric": metric, "cap": cap},
            "skipFactors": raw.get("skip_factors", []),
        }


def test_amount_ranges_are_returned_in_kopecks() -> None:
    products = _get()["products"]
    assert _by_code(products, "EQUIPMENT")["amount"] == {"min": 30_000_000, "max": 1_000_000_000}
    assert _by_code(products, "OVERDRAFT")["amount"] == {"min": 5_000_000, "max": 300_000_000}


def test_factors_preserve_rule_order_unknown_ranges_and_spot_prices() -> None:
    factors = _get()["factors"]
    assert [factor["code"] for factor in factors] == list(RAW_RULES["factors"])
    for factor in factors:
        raw = RAW_RULES["factors"][factor["code"]]
        unknown = raw["unknown"]
        expected_unknown = None
        if unknown is not None:
            best, worst = unknown
            expected_unknown = {"best": best, "worst": worst, "spotPricePp": worst - best}
        assert factor == {
            "code": factor["code"],
            "title": raw["title"],
            "unknown": expected_unknown,
            "rules": _rules(raw["rules"]),
            "overdraftRules": _rules(raw.get("overdraft_rules", [])),
        }

    debt_load = _by_code(factors, "debt_load")
    assert debt_load["unknown"] == {"best": -1.5, "worst": 2.0, "spotPricePp": 3.5}
    assert _by_code(factors, "credit_history")["unknown"] is None


def test_overdraft_rules_skip_factors_and_stop_conditions_match_yaml() -> None:
    body = _get()
    debt_load = _by_code(body["factors"], "debt_load")
    assert debt_load["overdraftRules"] == _rules(RAW_RULES["factors"]["debt_load"]["overdraft_rules"])
    assert _by_code(body["products"], "OVERDRAFT")["skipFactors"] == ["purpose", "collateral"]
    assert body["stopFactors"] == [
        {
            "code": stop["code"],
            "condition": stop["if"],
            "removable": stop["removable"],
            "closeMethod": stop.get("close"),
        }
        for stop in RAW_RULES["stop_factors"]
    ]
