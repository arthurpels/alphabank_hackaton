from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal
from typing import Any

from scoring.conditions import Condition, ConditionError
from scoring.schemas import AnomaliesRequest, AnomaliesResponse, Anomaly, AnswerOption, Series

ANOMALY_CONTEXT_NAMES = frozenset({"change_pct", "ratio"})

MONTHS_PREPOSITIONAL = (
    "январе",
    "феврале",
    "марте",
    "апреле",
    "мае",
    "июне",
    "июле",
    "августе",
    "сентябре",
    "октябре",
    "ноябре",
    "декабре",
)


def _round_half_up(value: Decimal, places: str = "1") -> Decimal:
    return value.quantize(Decimal(places), rounding=ROUND_HALF_UP)


def _next_month(month: str) -> str:
    year, number = int(month[:4]), int(month[5:])
    return f"{year + number // 12}-{number % 12 + 1:02d}"


def _times(ratio: Decimal) -> str:
    rounded = _round_half_up(ratio, "0.1")
    if rounded != rounded.to_integral_value():
        return f"{str(rounded).replace('.', ',')} раза"
    whole = int(rounded)
    if whole % 10 in (2, 3, 4) and whole % 100 not in (12, 13, 14):
        return f"{whole} раза"
    return f"{whole} раз"


@dataclass(frozen=True)
class AnomalyRule:
    code: str
    condition: Condition
    title: str
    client_hint: str
    answer_options: tuple[AnswerOption, ...]


def _condition(source: str) -> Condition:
    condition = Condition(source)
    unknown_names = condition.names - ANOMALY_CONTEXT_NAMES
    if unknown_names:
        raise ConditionError(f"в условии «{source}» неизвестные имена: {', '.join(sorted(unknown_names))}")
    return condition


class AnomalyDetector:
    def __init__(self, raw: dict[str, Any]) -> None:
        self.rules = tuple(
            AnomalyRule(
                code=code,
                condition=_condition(spec["if"]),
                title=spec["title"],
                client_hint=spec["client_hint"],
                answer_options=tuple(AnswerOption(**option) for option in spec.get("answer_options", [])),
            )
            for code, spec in raw.items()
        )

    def detect(self, request: AnomaliesRequest) -> AnomaliesResponse:
        return AnomaliesResponse(anomalies=[anomaly for series in request.series for anomaly in self._detect(series)])

    def _detect(self, series: Series) -> list[Anomaly]:
        points = sorted(series.points, key=lambda point: point.month)
        anomalies = []
        for previous, current in zip(points, points[1:], strict=False):
            if current.month != _next_month(previous.month) or previous.value == 0:
                continue
            ratio = Decimal(current.value) / Decimal(previous.value)
            change = (ratio - 1) * 100
            context = {"change_pct": float(change), "ratio": float(ratio)}
            for rule in self.rules:
                if rule.condition.evaluate(context):
                    anomalies.append(self._anomaly(rule, series, current.month, change, ratio))
                    break
        return anomalies

    def _anomaly(self, rule: AnomalyRule, series: Series, month: str, change: Decimal, ratio: Decimal) -> Anomaly:
        change_pct = int(_round_half_up(change))
        title = rule.title.format(
            month=MONTHS_PREPOSITIONAL[int(month[5:]) - 1],
            pct=abs(change_pct),
            times=_times(ratio),
        )
        return Anomaly(
            code=rule.code,
            source=series.source,
            month=month,
            change_pct=change_pct,
            title=title,
            client_hint=rule.client_hint,
            answer_options=list(rule.answer_options),
        )
