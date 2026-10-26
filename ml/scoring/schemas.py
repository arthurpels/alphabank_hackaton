from datetime import date
from enum import Enum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator
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


class BkiCredit(CamelModel):
    type: str
    debt: int = Field(ge=0)


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
    bank_payment: int = Field(default=0, ge=0)
    bki_credits: list[BkiCredit] = Field(default_factory=list)


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
    existing_payment: int = 0
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


class SourceCode(str, Enum):
    BKI = "BKI"
    EGRUL = "EGRUL"
    FNS = "FNS"
    OFD = "OFD"
    MARKETPLACE = "MARKETPLACE"
    ACQUIRING = "ACQUIRING"
    REPUTATION = "REPUTATION"


class SeriesPoint(CamelModel):
    month: str = Field(pattern=r"^\d{4}-(0[1-9]|1[0-2])$")
    value: int = Field(ge=0)


class Series(CamelModel):
    source: SourceCode
    points: list[SeriesPoint]

    @model_validator(mode="after")
    def months_are_unique(self) -> "Series":
        months = [point.month for point in self.points]
        if len(months) != len(set(months)):
            raise ValueError("месяцы в ряду повторяются")
        return self


class AnomaliesRequest(CamelModel):
    series: list[Series]


class AnswerOption(CamelModel):
    code: str
    label: str


class Anomaly(CamelModel):
    code: str
    source: SourceCode
    month: str
    change_pct: int
    title: str
    client_hint: str
    answer_options: list[AnswerOption]


class AnomaliesResponse(CamelModel):
    anomalies: list[Anomaly]


class SpotType(str, Enum):
    NO_DATA = "NO_DATA"
    ANOMALY = "ANOMALY"
    DOCUMENT = "DOCUMENT"
    STOP_FACTOR = "STOP_FACTOR"
    COLLATERAL = "COLLATERAL"


class SpotStatus(str, Enum):
    OPEN = "OPEN"
    ON_REVIEW = "ON_REVIEW"
    CLOSED = "CLOSED"


class FactRef(CamelModel):
    source: str = Field(min_length=1, max_length=32)
    label: str = Field(min_length=1, max_length=300)
    spot_id: str | None = None


class SummarySpot(CamelModel):
    spot_id: str
    type: SpotType
    title: str = Field(min_length=1, max_length=300)
    status: SpotStatus
    price_pp: float | None = None
    answer: str | None = Field(default=None, max_length=2000)


class SummaryRequest(CamelModel):
    product_code: ProductCode
    amount: int = Field(gt=0)
    term_months: int = Field(gt=0)
    goal: str | None = Field(default=None, max_length=1000)
    rate_from: float
    rate_to: float
    approve_max: float
    spots: list[SummarySpot] = Field(default_factory=list, max_length=50)
    facts: list[FactRef] = Field(default_factory=list, max_length=50)
    existing_payment: int = Field(default=0, ge=0)
    bank_payment: int = Field(default=0, ge=0)


class SummaryItem(CamelModel):
    text: str
    facts: list[FactRef]


class LlmMeta(CamelModel):
    model: str
    digest: str
    prompt_version: str
    cached: bool = False


class LlmHealth(CamelModel):
    status: Literal["disabled", "warming", "ready", "unavailable"]
    model: str | None = None
    digest: str | None = None
    cached_verdicts: int | None = None


class UnderwritingSummary(CamelModel):
    generated_by: Literal["LLM", "TEMPLATE"]
    items: list[SummaryItem]
    llm: LlmMeta | None = None


class PurposeRequest(CamelModel):
    product_code: ProductCode
    goal: str | None = Field(default=None, max_length=1000)
    okved: str | None = Field(default=None, pattern=r"^\d{2}(\.\d{1,2}){0,2}$")


class PurposeStatus(str, Enum):
    DECIDED = "DECIDED"
    UNCLEAR = "UNCLEAR"
    UNAVAILABLE = "UNAVAILABLE"
    NOT_APPLICABLE = "NOT_APPLICABLE"


class ProfileStatus(str, Enum):
    FITS = "FITS"
    DOES_NOT_FIT = "DOES_NOT_FIT"
    UNCLEAR = "UNCLEAR"
    NOT_CHECKED = "NOT_CHECKED"


class ProfileCheck(CamelModel):
    okved: str
    activity: str | None
    status: ProfileStatus
    reason: str | None = None
    evidence: str | None = None
    violation: (
        Literal["FORMAT", "NO_EVIDENCE", "EVIDENCE_NOT_IN_GOAL", "UNIVERSAL_PURCHASE", "UNCERTAIN_REASON"] | None
    ) = None
    llm: LlmMeta | None = None


class PurposeResponse(CamelModel):
    status: PurposeStatus
    purpose: Purpose | None
    applicable: bool
    generated_by: Literal["KEYWORDS", "LLM"]
    matched_keywords: list[str]
    reason: str | None = None
    evidence: str | None = None
    violation: (
        Literal[
            "FORMAT", "NO_EVIDENCE", "EVIDENCE_NOT_IN_GOAL", "GENERIC_EVIDENCE", "UNCERTAIN_REASON", "MANIPULATION"
        ]
        | None
    ) = None
    llm: LlmMeta | None = None
    profile: ProfileCheck | None = None


class AnswerCode(str, Enum):
    RENOVATION = "RENOVATION"
    SEASONAL = "SEASONAL"
    CLIENT_LOST = "CLIENT_LOST"
    OTHER = "OTHER"


class AnomalyCode(str, Enum):
    REVENUE_DROP = "REVENUE_DROP"
    REVENUE_SPIKE = "REVENUE_SPIKE"


class AnomalyRef(CamelModel):
    code: AnomalyCode
    month: str = Field(pattern=r"^\d{4}-(0[1-9]|1[0-2])$")
    change_pct: int
    source: SourceCode | None = None


class AnswerClassifyRequest(CamelModel):
    text: str = Field(min_length=1, max_length=1000)
    anomaly: AnomalyRef | None = None


class AnswerStatus(str, Enum):
    DECIDED = "DECIDED"
    UNAVAILABLE = "UNAVAILABLE"


class AnswerClassifyResponse(CamelModel):
    status: AnswerStatus
    code: AnswerCode | None
    generated_by: Literal["KEYWORDS", "LLM"]
    matched_keywords: list[str]
    reason: str | None = None
    evidence: str | None = None
    violation: (
        Literal[
            "MANIPULATION",
            "FORMAT",
            "NO_EVIDENCE",
            "EVIDENCE_NOT_IN_TEXT",
            "NEGATED_EVIDENCE",
            "UNCERTAIN_REASON",
            "NO_CUE",
            "EXCLUDED_CONTEXT",
        ]
        | None
    ) = None
    llm: LlmMeta | None = None


class AnswerVerifyRequest(CamelModel):
    anomaly: AnomalyRef
    answer_code: AnswerCode
    series: list[Series] = Field(max_length=10)


class AnswerVerifyResponse(CamelModel):
    verdict: Literal["ACCEPTED", "REVIEW"]
    rule: str | None
    explanation: str
