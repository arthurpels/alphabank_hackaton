import pytest

from scoring.schemas import Purpose, SourceCode, Stability
from synthetic_data import (
    AS_OF,
    ENGINE,
    M6,
    M12,
    SCENARIOS,
    P,
    anomalies,
    coffee_ofd,
    money,
    score_request,
    shell_acq,
)


@pytest.mark.parametrize(
    ("persona", "expected"),
    [
        ("coffee", [(17.0, 26.0), (18.0, 23.5), (18.0, 21.0), (18.0, 19.5)]),
        ("builder", [(17.0, 26.0), (17.0, 24.5), (21.0, 22.5)]),
        ("wb", [(17.0, 26.0), (20.0, 23.0), (20.0, 21.5)]),
        ("fns", [(15.0, 24.0), (16.0, 17.5), (16.0, 17.5)]),
        ("shell", [(17.0, 26.0)]),
    ],
)
def test_persona_forks_match_documented_values(persona: str, expected: list[tuple[float, float]]) -> None:
    results = [ENGINE.score(score_request(P[persona], updates)) for _, updates in SCENARIOS[persona]]

    assert [(result.rate_from, result.rate_to) for result in results] == expected


def test_score_request_converts_analytical_input_to_engine_contract() -> None:
    request = score_request(P["coffee"], SCENARIOS["coffee"][1][1])

    assert request.as_of == AS_OF
    assert request.amount == 200_000_000
    assert request.monthly_revenue == 140_666_667
    assert request.tax_debt == 0
    assert request.stability == Stability.ANOMALY_OPEN
    assert request.purpose is None


def test_tax_debt_is_converted_to_kopecks() -> None:
    request = score_request(P["fns"], {})

    assert request.tax_debt == 341_200


def test_purpose_is_converted_to_engine_enum() -> None:
    request = score_request(P["coffee"], SCENARIOS["coffee"][-1][1])

    assert request.purpose == Purpose.MATCH


def test_anomalies_use_engine_rules() -> None:
    assert anomalies(coffee_ofd, M12, SourceCode.OFD) == ["Почему в марте выручка упала на 45 %"]
    assert anomalies(shell_acq, M6, SourceCode.ACQUIRING) == ["Почему в сентябре обороты выросли в 5,4 раза"]


@pytest.mark.parametrize(
    ("persona", "amounts"),
    [("builder", [195_000_000]), ("wb", [120_000_000, 12_000_000]), ("shell", [])],
)
def test_offer_grid_uses_engine(persona: str, amounts: list[int]) -> None:
    request = score_request(P[persona], SCENARIOS[persona][-1][1])

    assert [offer.amount for offer in ENGINE.offer_grid(request).offers] == amounts


def test_money_rounds_half_up() -> None:
    assert money(1.005) == 101
