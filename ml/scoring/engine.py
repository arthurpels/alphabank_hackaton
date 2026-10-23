from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any

import yaml

from scoring.conditions import Condition, ConditionError
from scoring.schemas import (
    Collateral,
    Factor,
    Policy,
    Purpose,
    ScoreRequest,
    ScoreResponse,
    Stability,
    StopFactor,
    Thresholds,
)

KOPECKS_PER_RUBLE = 100

CONTEXT_NAMES = frozenset(
    {
        "credit_score",
        "delinquencies_12m",
        "age_months",
        "director_months",
        "unfiled_reports",
        "egrul.status",
        "egrul.address.is_mass",
        "fns.tax_debt.amount",
        "bki.delinquencies.current",
        "pti",
        "limit_share",
        "series",
        "no_anomalies",
        "anomaly_explained_accepted",
        "only_document",
        "anomaly_explanation_rejected",
        "purpose_matches_product",
        "guarantee_or_pledge",
    }
)


class RulesError(ValueError):
    pass


def months_between(start: date, end: date) -> int:
    return (end.year - start.year) * 12 + (end.month - start.month) - (1 if end.day < start.day else 0)


def annuity_payment(amount: float, term_months: int, rate_pct: float) -> float:
    monthly_rate = rate_pct / 100 / 12
    return amount * monthly_rate / (1 - (1 + monthly_rate) ** -term_months)


@dataclass(frozen=True)
class Rule:
    condition: Condition | None
    value: float

    def matches(self, context: dict[str, Any]) -> bool:
        return self.condition is None or self.condition.evaluate(context)


@dataclass(frozen=True)
class FactorSpec:
    code: str
    title: str
    unknown: tuple[float, float] | None
    rules: tuple[Rule, ...]
    limit_share_rules: tuple[Rule, ...]

    def value(self, context: dict[str, Any], metric: str | None) -> float:
        rules = self.limit_share_rules if metric == "LIMIT_SHARE" and self.limit_share_rules else self.rules
        for rule in rules:
            if rule.matches(context):
                return rule.value
        raise RulesError(f"для фактора {self.code} не сработало ни одно правило")


@dataclass(frozen=True)
class StopFactorSpec:
    code: str
    condition: Condition
    removable: bool
    close_method: str | None


@dataclass(frozen=True)
class ProductSpec:
    code: str
    base_rate: float
    approve_max: float
    reject_min: float
    amount_min: int
    amount_max: int
    metric: str
    cap: float
    skip_factors: frozenset[str]


def _condition(source: str) -> Condition:
    condition = Condition(source)
    unknown_names = condition.names - CONTEXT_NAMES
    if unknown_names:
        raise RulesError(f"в условии «{source}» неизвестные имена: {', '.join(sorted(unknown_names))}")
    return condition


def _rules(raw: list[dict[str, Any]] | None) -> tuple[Rule, ...]:
    rules = []
    for item in raw or []:
        if "else" in item:
            rules.append(Rule(None, float(item["else"])))
        else:
            rules.append(Rule(_condition(item["if"]), float(item["value"])))
    return tuple(rules)


def _product(code: str, raw: dict[str, Any]) -> ProductSpec:
    if "limit_cap" in raw:
        metric, cap = "LIMIT_SHARE", raw["limit_cap"]
    else:
        metric, cap = "PTI", raw["pti_cap"]
    amount_min, amount_max = raw["amount"]
    return ProductSpec(
        code=code,
        base_rate=float(raw["base_rate"]),
        approve_max=float(raw["approve_max"]),
        reject_min=float(raw["reject_min"]),
        amount_min=amount_min * KOPECKS_PER_RUBLE,
        amount_max=amount_max * KOPECKS_PER_RUBLE,
        metric=metric,
        cap=float(cap),
        skip_factors=frozenset(raw.get("skip_factors", [])),
    )


def _factor(code: str, raw: dict[str, Any]) -> FactorSpec:
    unknown = raw.get("unknown")
    return FactorSpec(
        code=code,
        title=raw["title"],
        unknown=None if unknown is None else (float(unknown[0]), float(unknown[1])),
        rules=_rules(raw.get("rules")),
        limit_share_rules=_rules(raw.get("overdraft_rules")),
    )


class Engine:
    def __init__(self, raw: dict[str, Any]) -> None:
        try:
            self.rules_version = str(raw["rules_version"])
            self.calc_rate_pct = float(raw["calc_rate_pct"])
            self.products = {code: _product(code, spec) for code, spec in raw["products"].items()}
            self.factors = tuple(_factor(code, spec) for code, spec in raw["factors"].items())
            self.stop_factors = tuple(
                StopFactorSpec(
                    code=item["code"],
                    condition=_condition(item["if"]),
                    removable=bool(item["removable"]),
                    close_method=item.get("close"),
                )
                for item in raw["stop_factors"]
            )
        except (KeyError, TypeError, ConditionError) as exc:
            raise RulesError(f"некорректный файл правил: {exc}") from exc

    @classmethod
    def from_yaml(cls, path: Path) -> "Engine":
        with path.open(encoding="utf-8") as file:
            return cls(yaml.safe_load(file))

    def score(self, request: ScoreRequest) -> ScoreResponse:
        product = self.products[request.product_code.value]
        metric_value = self._metric_value(request, product)
        context = self._context(request, metric_value)

        rate_from = rate_to = product.base_rate
        factors = []
        for spec in self.factors:
            factor = self._factor(spec, request, product, context)
            factors.append(factor)
            if not factor.applicable:
                continue
            if factor.known:
                rate_from += factor.value
                rate_to += factor.value
            else:
                rate_from += factor.best
                rate_to += factor.worst

        amount_in_range = product.amount_min <= request.amount <= product.amount_max
        within_cap = metric_value is None or metric_value <= product.cap
        policy = Policy(
            metric=None if metric_value is None else product.metric,
            value=None if metric_value is None else round(metric_value, 3),
            cap=None if metric_value is None else product.cap,
            amount_in_range=amount_in_range,
            ok=amount_in_range and within_cap,
        )
        rate_from, rate_to = round(rate_from, 2), round(rate_to, 2)
        return ScoreResponse(
            rules_version=self.rules_version,
            product_code=request.product_code,
            rate_from=rate_from,
            rate_to=rate_to,
            thresholds=Thresholds(approve_max=product.approve_max, reject_min=product.reject_min),
            factors=factors,
            stop_factors=[
                StopFactor(code=spec.code, removable=spec.removable, close_method=spec.close_method)
                for spec in self.stop_factors
                if spec.condition.evaluate(context)
            ],
            policy=policy,
            passes=policy.ok and rate_to <= product.approve_max,
        )

    def _metric_value(self, request: ScoreRequest, product: ProductSpec) -> float | None:
        if request.monthly_revenue is None:
            return None
        if product.metric == "LIMIT_SHARE":
            return request.amount / request.monthly_revenue
        payment = annuity_payment(request.amount, request.term_months, self.calc_rate_pct)
        return payment / request.monthly_revenue

    def _context(self, request: ScoreRequest, metric_value: float | None) -> dict[str, Any]:
        return {
            "credit_score": request.credit_score,
            "delinquencies_12m": request.delinq_12m,
            "age_months": months_between(request.registered_at, request.as_of),
            "director_months": months_between(request.director_since, request.as_of),
            "unfiled_reports": request.unfiled_reports,
            "egrul.status": request.egrul_status,
            "egrul.address.is_mass": request.mass_address,
            "fns.tax_debt.amount": request.tax_debt,
            "bki.delinquencies.current": request.bki_current_overdue,
            "pti": metric_value,
            "limit_share": metric_value,
            "series": request.stability not in (None, Stability.DOCUMENT),
            "no_anomalies": request.stability == Stability.CLEAN,
            "anomaly_explained_accepted": request.stability == Stability.EXPLAINED,
            "only_document": request.stability == Stability.DOCUMENT,
            "anomaly_explanation_rejected": request.stability == Stability.REJECTED,
            "purpose_matches_product": request.purpose == Purpose.MATCH,
            "guarantee_or_pledge": request.collateral in (Collateral.GUARANTEE, Collateral.PLEDGE),
        }

    def _is_known(self, code: str, request: ScoreRequest) -> bool:
        if code == "debt_load":
            return request.monthly_revenue is not None
        if code == "revenue_stability":
            return request.stability not in (None, Stability.ANOMALY_OPEN)
        if code == "purpose":
            return request.purpose is not None
        if code == "collateral":
            return request.collateral is not None
        return True

    def _factor(self, spec: FactorSpec, request: ScoreRequest, product: ProductSpec, context: dict[str, Any]) -> Factor:
        if spec.code in product.skip_factors:
            return Factor(code=spec.code, title=spec.title, applicable=False, known=False)
        if spec.unknown is None or self._is_known(spec.code, request):
            return Factor(
                code=spec.code,
                title=spec.title,
                applicable=True,
                known=True,
                value=spec.value(context, product.metric),
            )
        best, worst = spec.unknown
        return Factor(
            code=spec.code,
            title=spec.title,
            applicable=True,
            known=False,
            best=best,
            worst=worst,
            spot_price_pp=round(worst - best, 2),
        )
