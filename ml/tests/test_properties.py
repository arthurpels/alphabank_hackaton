from datetime import date

from fastapi.testclient import TestClient
from hypothesis import HealthCheck, given, settings
from hypothesis import strategies as st

from main import app, engine
from scoring.schemas import Collateral, ProductCode, Purpose, ScoreRequest, Stability

AS_OF = date(2026, 10, 23)
PROPERTY = settings(max_examples=300, derandomize=True, database=None, deadline=None,
                    suppress_health_check=[HealthCheck.too_slow])
GRID = settings(PROPERTY, max_examples=60)
client = TestClient(app)


REVENUE = st.integers(1_000_000, 10_000_000_000)
ANY_REVENUE = st.one_of(st.none(), REVENUE)


@st.composite
def requests(draw, revenue=ANY_REVENUE):
    product = draw(st.sampled_from(list(ProductCode)))
    spec = engine.products[product.value]
    registered = draw(st.dates(date(2005, 1, 1), AS_OF))
    return ScoreRequest(
        as_of=AS_OF,
        product_code=product,
        amount=draw(st.integers(spec.amount_min // 2, spec.amount_max * 2)),
        term_months=draw(st.sampled_from(spec.terms)),
        egrul_status=draw(st.sampled_from(["ACTIVE", "ACTIVE", "ACTIVE", "LIQUIDATING"])),
        registered_at=registered,
        director_since=draw(st.dates(registered, AS_OF)),
        mass_address=draw(st.booleans()),
        credit_score=draw(st.integers(300, 850)),
        delinq12m=draw(st.integers(0, 3)),
        bki_current_overdue=draw(st.sampled_from([0, 0, 0, 1])),
        tax_debt=draw(st.sampled_from([0, 0, 0, 3412])),
        unfiled_reports=draw(st.integers(0, 2)),
        monthly_revenue=draw(revenue),
        stability=draw(st.one_of(st.none(), st.sampled_from(list(Stability)))),
        purpose=draw(st.one_of(st.none(), st.sampled_from(list(Purpose)))),
        collateral=draw(st.one_of(st.none(), st.sampled_from(list(Collateral)))),
    )


@PROPERTY
@given(requests(revenue=REVENUE), st.integers(1, 10_000_000_000))
def test_more_revenue_never_worsens_rate(request: ScoreRequest, extra: int) -> None:
    before = engine.score(request)
    after = engine.score(request.model_copy(update={"monthly_revenue": request.monthly_revenue + extra}))

    assert after.rate_from <= before.rate_from
    assert after.rate_to <= before.rate_to
    assert after.policy.ok or not before.policy.ok


@PROPERTY
@given(requests(), st.data())
def test_closing_a_spot_never_widens_fork(request: ScoreRequest, data: st.DataObject) -> None:
    closable = {
        "monthly_revenue": (request.monthly_revenue is None, REVENUE),
        "stability": (request.stability in (None, Stability.ANOMALY_OPEN),
                      st.sampled_from([s for s in Stability if s != Stability.ANOMALY_OPEN])),
        "purpose": (request.purpose is None, st.sampled_from(list(Purpose))),
        "collateral": (request.collateral is None, st.sampled_from(list(Collateral))),
    }
    open_fields = [field for field, (is_open, _) in closable.items() if is_open]
    if not open_fields:
        return
    field = data.draw(st.sampled_from(open_fields))
    before = engine.score(request)
    after = engine.score(request.model_copy(update={field: data.draw(closable[field][1])}))

    assert before.rate_from <= after.rate_from <= after.rate_to <= before.rate_to


@PROPERTY
@given(requests())
def test_fork_width_equals_sum_of_open_spot_prices(request: ScoreRequest) -> None:
    result = engine.score(request)
    prices = [factor.spot_price_pp for factor in result.factors if factor.applicable and not factor.known]

    assert all(price is not None and price >= 0 for price in prices)
    assert round(result.rate_to - result.rate_from, 2) == round(sum(prices), 2)
    assert all(factor.spot_price_pp is None for factor in result.factors if factor.known)


@GRID
@given(requests())
def test_every_grid_offer_passes_score(request: ScoreRequest) -> None:
    for offer in engine.offer_grid(request).offers:
        variant = request.model_copy(update={
            "product_code": offer.product_code,
            "amount": offer.amount,
            "term_months": offer.term_months,
            "collateral": Collateral(offer.collateral_type) if offer.collateral_type else Collateral.DECLINED,
        })
        result = engine.score(variant)

        assert result.passes
        assert (result.rate_from, result.rate_to) == (offer.rate_from, offer.rate_to)
        assert result.rate_to <= result.thresholds.approve_max


@PROPERTY
@given(requests())
def test_same_input_gives_same_answer(request: ScoreRequest) -> None:
    payload = request.model_dump(mode="json", by_alias=True)

    first = client.post("/score", json=payload)
    second = client.post("/score", json=payload)

    assert first.status_code == 200
    assert first.content == second.content
    assert engine.score(request) == engine.score(ScoreRequest.model_validate(payload))
