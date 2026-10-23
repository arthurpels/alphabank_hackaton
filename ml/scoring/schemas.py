from datetime import date
from enum import Enum

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ProductCode(str, Enum):
    WORKING_CAPITAL = "WORKING_CAPITAL"
    EQUIPMENT = "EQUIPMENT"
    OVERDRAFT = "OVERDRAFT"


class Stability(str, Enum):
    CLEAN = "CLEAN"
    ANOMALY_OPEN = "ANOMALY_OPEN"
    EXPLAINED = "EXPLAINED"
    DOCUMENT = "DOCUMENT"
    REJECTED = "REJECTED"


class Purpose(str, Enum):
    MATCH = "MATCH"
    MISMATCH = "MISMATCH"


class Collateral(str, Enum):
    GUARANTEE = "GUARANTEE"
    PLEDGE = "PLEDGE"
    DECLINED = "DECLINED"


class ScoreRequest(CamelModel):
    as_of: date
    product_code: ProductCode
    amount: int = Field(gt=0)
    term_months: int = Field(gt=0)
    egrul_status: str
    registered_at: date
    director_since: date
    mass_address: bool
    credit_score: int
    delinq_12m: int = Field(ge=0, alias="delinq12m")
    bki_current_overdue: int = Field(ge=0)
    tax_debt: int = Field(ge=0)
    unfiled_reports: int = Field(ge=0)
    monthly_revenue: int | None = Field(default=None, gt=0)
    stability: Stability | None = None
    purpose: Purpose | None = None
    collateral: Collateral | None = None


class Thresholds(CamelModel):
    approve_max: float
    reject_min: float


class Factor(CamelModel):
    code: str
    title: str
    applicable: bool
    known: bool
    value: float | None = None
    best: float | None = None
    worst: float | None = None
    spot_price_pp: float | None = None


class StopFactor(CamelModel):
    code: str
    removable: bool
    close_method: str | None = None


class Policy(CamelModel):
    metric: str | None = None
    value: float | None = None
    cap: float | None = None
    amount_in_range: bool
    ok: bool


class ScoreResponse(CamelModel):
    rules_version: str
    product_code: ProductCode
    rate_from: float
    rate_to: float
    thresholds: Thresholds
    factors: list[Factor]
    stop_factors: list[StopFactor]
    policy: Policy
    passes: bool


class Offer(CamelModel):
    product_code: ProductCode
    amount: int
    term_months: int
    collateral_type: str | None = None
    rate_from: float
    rate_to: float
    policy: Policy


class OfferGridResponse(CamelModel):
    rules_version: str
    offers: list[Offer]
