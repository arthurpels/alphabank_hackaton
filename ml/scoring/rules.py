from pathlib import Path

import yaml

from scoring.schemas import CamelModel


class Rule(CamelModel):
    condition: str | None
    value: float


class UnknownRange(CamelModel):
    best: float
    worst: float
    spot_price_pp: float


class Thresholds(CamelModel):
    approve_max: float
    reject_min: float


class AmountRange(CamelModel):
    min: int
    max: int


class Policy(CamelModel):
    metric: str
    cap: float


class Product(CamelModel):
    code: str
    name: str
    base_rate: float
    thresholds: Thresholds
    amount: AmountRange
    terms: list[int]
    policy: Policy
    skip_factors: list[str]


class Factor(CamelModel):
    code: str
    title: str
    unknown: UnknownRange | None
    rules: list[Rule]
    overdraft_rules: list[Rule]


class StopFactor(CamelModel):
    code: str
    condition: str
    removable: bool
    close_method: str | None


class RulesResponse(CamelModel):
    rules_version: str
    calc_rate_pct: float
    products: list[Product]
    factors: list[Factor]
    stop_factors: list[StopFactor]

    @classmethod
    def from_yaml(cls, path: Path) -> "RulesResponse":
        raw = yaml.safe_load(path.read_text(encoding="utf-8"))
        return cls(
            rules_version=raw["rules_version"],
            calc_rate_pct=raw["calc_rate_pct"],
            products=[_product(code, product) for code, product in raw["products"].items()],
            factors=[_factor(code, factor) for code, factor in raw["factors"].items()],
            stop_factors=[
                StopFactor(
                    code=stop["code"],
                    condition=stop["if"],
                    removable=stop["removable"],
                    close_method=stop.get("close"),
                )
                for stop in raw["stop_factors"]
            ],
        )


def _rules(raw: list[dict]) -> list[Rule]:
    return [
        Rule(condition=rule["if"] if "if" in rule else None, value=rule.get("value", rule.get("else")))
        for rule in raw
    ]


def _product(code: str, raw: dict) -> Product:
    metric = "LIMIT_SHARE" if "limit_cap" in raw else "PTI"
    cap = raw["limit_cap"] if metric == "LIMIT_SHARE" else raw["pti_cap"]
    return Product(
        code=code,
        name=raw["name"],
        base_rate=raw["base_rate"],
        thresholds=Thresholds(approve_max=raw["approve_max"], reject_min=raw["reject_min"]),
        amount=AmountRange(min=raw["amount"][0] * 100, max=raw["amount"][1] * 100),
        terms=raw["terms"],
        policy=Policy(metric=metric, cap=cap),
        skip_factors=raw.get("skip_factors", []),
    )


def _factor(code: str, raw: dict) -> Factor:
    unknown = raw["unknown"]
    unknown_range = None
    if unknown is not None:
        best, worst = unknown
        unknown_range = UnknownRange(best=best, worst=worst, spot_price_pp=worst - best)
    return Factor(
        code=code,
        title=raw["title"],
        unknown=unknown_range,
        rules=_rules(raw["rules"]),
        overdraft_rules=_rules(raw.get("overdraft_rules", [])),
    )
