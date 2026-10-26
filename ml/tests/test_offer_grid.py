import pytest

from tests.contract import GRID_EXAMPLES, SCORE_EXAMPLES, client, persona_request, post


def _grid(payload: dict) -> list[dict]:
    return post("/offers/grid", payload)["offers"]


def _rescore(request: dict, offer: dict) -> dict:
    variant = {
        **request,
        "productCode": offer["productCode"],
        "amount": offer["amount"],
        "termMonths": offer["termMonths"],
        "collateral": offer["collateralType"] or "DECLINED",
    }
    return post("/score", variant)


@pytest.mark.parametrize("name", sorted(GRID_EXAMPLES))
def test_grid_matches_contract_example(name: str) -> None:
    request, expected = GRID_EXAMPLES[name]
    assert post("/offers/grid", request) == expected


@pytest.mark.parametrize("name", sorted(GRID_EXAMPLES))
def test_grid_is_deterministic(name: str) -> None:
    request = GRID_EXAMPLES[name][0]
    assert post("/offers/grid", request) == post("/offers/grid", request)


@pytest.mark.parametrize("name", sorted({*SCORE_EXAMPLES, *GRID_EXAMPLES}))
def test_every_offer_passes_when_scored(name: str) -> None:
    request = (SCORE_EXAMPLES.get(name) or GRID_EXAMPLES[name])[0]
    for offer in _grid(request):
        scored = _rescore(request, offer)
        assert scored["passes"] is True
        assert (scored["rateFrom"], scored["rateTo"], scored["policy"]) == (
            offer["rateFrom"],
            offer["rateTo"],
            offer["policy"],
        )


@pytest.mark.parametrize("name", sorted(SCORE_EXAMPLES))
def test_one_offer_per_product_and_requested_amount_is_cut(name: str) -> None:
    request = SCORE_EXAMPLES[name][0]
    offers = _grid(request)
    products = [offer["productCode"] for offer in offers]
    assert len(products) == len(set(products))
    for offer in offers:
        if offer["productCode"] == request["productCode"]:
            assert offer["amount"] < request["amount"]


def test_wb_gets_smaller_loan_and_overdraft() -> None:
    offers = _grid(persona_request("wb"))
    assert [(offer["productCode"], offer["amount"], offer["termMonths"]) for offer in offers] == [
        ("WORKING_CAPITAL", 120_000_000, 36),
        ("OVERDRAFT", 12_000_000, 12),
    ]


def test_guarantee_is_offered_only_when_nothing_passes_without_it() -> None:
    request = persona_request("wb", amount=300_000_000, creditScore=520)
    offers = _grid(request)
    assert [(offer["productCode"], offer["collateralType"]) for offer in offers] == [("WORKING_CAPITAL", "GUARANTEE")]
    without_collateral = _rescore(request, {**offers[0], "collateralType": None})
    assert without_collateral["passes"] is False


def test_no_overdraft_without_confirmed_revenue() -> None:
    offers = _grid(persona_request("builder"))
    assert all(offer["productCode"] != "OVERDRAFT" for offer in offers)


def test_overdraft_limit_is_rounded_down_to_10000_rub() -> None:
    offers = _grid(persona_request("coffee"))
    overdraft = next(offer for offer in offers if offer["productCode"] == "OVERDRAFT")
    assert overdraft["amount"] == 42_000_000
    assert overdraft["amount"] % 1_000_000 == 0


def test_other_products_never_offer_guarantee_for_overdraft() -> None:
    request = persona_request("wb", amount=300_000_000, creditScore=520)
    assert all(not (offer["productCode"] == "OVERDRAFT" and offer["collateralType"]) for offer in _grid(request))


def test_grid_rejects_invalid_request() -> None:
    assert client.post("/offers/grid", json=persona_request("wb", amount=0)).status_code == 422


def test_grid_cuts_the_amount_when_existing_payments_take_the_load() -> None:
    request = persona_request("builder", monthlyRevenue=80_000_000, stability="DOCUMENT")
    plain = _grid(request)
    loaded = _grid({**request, "bankPayment": 1_000_000})
    assert plain and loaded
    assert loaded[0]["amount"] < plain[0]["amount"]
    for offer in loaded:
        assert offer["policy"]["existingPayment"] == 1_000_000 + 557_454
        assert offer["policy"]["ok"]


def test_grid_is_empty_when_existing_payments_alone_exceed_the_cap() -> None:
    request = persona_request("builder", monthlyRevenue=80_000_000, stability="DOCUMENT")
    offers = _grid({**request, "bankPayment": 100_000_000})
    assert [offer for offer in offers if offer["productCode"] != "OVERDRAFT"] == []


def test_grid_is_deterministic_with_obligations() -> None:
    request = persona_request("wb", bankPayment=2_000_000, bkiCredits=[{"type": "CREDIT_CARD", "debt": 4_000_000}])
    assert _grid(request) == _grid(request)
