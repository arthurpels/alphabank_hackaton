import re
from dataclasses import dataclass
from typing import Any

from scoring.anomalies import MONTHS_PREPOSITIONAL, AnomalyDetector
from scoring.answer_rules import ANSWER_CODES, AnswerRules, find_phrase, is_negated, normalize_text, phrase
from scoring.llm import LlmClient, prompt_version
from scoring.schemas import (
    AnomaliesRequest,
    AnomalyCode,
    AnomalyRef,
    AnswerClassifyRequest,
    AnswerClassifyResponse,
    AnswerCode,
    AnswerStatus,
    AnswerVerifyRequest,
    AnswerVerifyResponse,
    LlmMeta,
    Series,
)

CYRILLIC = re.compile(r"[а-я]")

LLM_SCHEMA = {
    "type": "object",
    "properties": {
        "reason": {"type": "string"},
        "evidence": {"type": "string"},
        "code": {"type": "string", "enum": list(ANSWER_CODES)},
    },
    "required": ["reason", "evidence", "code"],
}


@dataclass(frozen=True)
class KeywordDecision:
    code: AnswerCode | None
    matched: list[str]
    manipulation: bool = False


class AnswerKeywords:
    def __init__(self, raw: dict[str, Any]) -> None:
        self.negations = tuple(normalize_text(word) for word in raw["negations"])
        self.hedges = tuple(phrase(word) for word in raw["hedges"])
        self.manipulation = tuple(phrase(word) for word in raw["manipulation"])
        self.codes: dict[str, dict[str, Any]] = {}
        for code, spec in raw["codes"].items():
            if code not in ANSWER_CODES:
                raise ValueError(f"в answer_keywords неизвестный код: {code}")
            groups = [spec["any"]] if "any" in spec else spec["all"]
            self.codes[code] = {
                "groups": [tuple(phrase(word) for word in group) for group in groups],
                "defer_if": tuple(phrase(word) for word in spec.get("defer_if", [])),
            }

    def decide(self, text: str) -> KeywordDecision:
        words = normalize_text(text).split()
        manipulation = [" ".join(parts) for parts in self.manipulation if find_phrase(words, parts)]
        if manipulation:
            return KeywordDecision(AnswerCode.OTHER, manipulation, manipulation=True)
        if not words or not CYRILLIC.search(" ".join(words)):
            return KeywordDecision(AnswerCode.OTHER, [])
        hedges = [" ".join(parts) for parts in self.hedges if find_phrase(words, parts)]
        if hedges:
            return KeywordDecision(AnswerCode.OTHER, hedges)

        hits: dict[str, list[str]] = {}
        deferred = False
        for code, spec in self.codes.items():
            matched = [self._group_match(words, group) for group in spec["groups"]]
            if all(matched):
                hits[code] = [word for group in matched for word in group]
                deferred = deferred or any(find_phrase(words, parts) for parts in spec["defer_if"])
        if len(hits) == 1 and not deferred:
            code, matched_words = next(iter(hits.items()))
            return KeywordDecision(AnswerCode(code), matched_words)
        return KeywordDecision(None, [word for matched_words in hits.values() for word in matched_words])

    def _group_match(self, words: list[str], group: tuple[tuple[str, ...], ...]) -> list[str]:
        found = []
        for parts in group:
            for start in find_phrase(words, parts):
                end = start + len(parts)
                if not is_negated(words[:start], words[end:], self.negations):
                    found.append(" ".join(parts))
                    break
        return found


def _describe(anomaly: AnomalyRef | None) -> str:
    if anomaly is None:
        return "не указано"
    month = MONTHS_PREPOSITIONAL[int(anomaly.month[5:]) - 1]
    if anomaly.code == AnomalyCode.REVENUE_DROP:
        return f"выручка упала на {abs(anomaly.change_pct)} % в {month} {anomaly.month[:4]}"
    return f"выручка выросла на {anomaly.change_pct} % в {month} {anomaly.month[:4]}"


class AnswerClassifier:
    def __init__(self, keywords: dict[str, Any], llm: LlmClient | None, prompts: dict[str, Any]) -> None:
        self.keywords = AnswerKeywords(keywords)
        self.llm = llm
        self.prompts = prompts["answers"]
        self.prompt_version = prompt_version(self.prompts, LLM_SCHEMA)
        self.rules = AnswerRules.from_config(self.prompts["rules"], keywords["negations"])

    def classify(self, request: AnswerClassifyRequest) -> AnswerClassifyResponse:
        decision = self.keywords.decide(request.text)
        if decision.code is not None:
            return AnswerClassifyResponse(
                status=AnswerStatus.DECIDED,
                code=decision.code,
                generated_by="KEYWORDS",
                matched_keywords=decision.matched,
                violation="MANIPULATION" if decision.manipulation else None,
            )
        unavailable = AnswerClassifyResponse(
            status=AnswerStatus.UNAVAILABLE, code=None, generated_by="KEYWORDS", matched_keywords=decision.matched
        )
        if self.llm is None:
            return unavailable
        user = self.prompts["user"].format(anomaly=_describe(request.anomaly), text=" ".join(request.text.split()))
        answer = self.llm.chat_json(self.prompts["system"], user, LLM_SCHEMA)
        if answer is None:
            return unavailable
        verdict = self.rules.check(answer.content, request.text)
        return AnswerClassifyResponse(
            status=AnswerStatus.UNAVAILABLE if verdict.code is None else AnswerStatus.DECIDED,
            code=None if verdict.code is None else AnswerCode(verdict.code),
            generated_by="LLM",
            matched_keywords=decision.matched,
            reason=verdict.reason,
            evidence=verdict.evidence,
            violation=verdict.violation,
            llm=LlmMeta(
                model=answer.model, digest=answer.digest, prompt_version=self.prompt_version, cached=answer.cached
            ),
        )


def shift_month(month: str, delta: int) -> str:
    index = int(month[:4]) * 12 + int(month[5:]) - 1 + delta
    return f"{index // 12}-{index % 12 + 1:02d}"


def _month_name(month: str, with_year: bool = False) -> str:
    name = MONTHS_PREPOSITIONAL[int(month[5:]) - 1]
    return f"{name} {month[:4]}" if with_year else name


def _signed(value: float) -> str:
    rounded = round(value)
    return f"{'−' if rounded < 0 else '+'}{abs(rounded)}"


class AnswerVerifier:
    def __init__(self, checks: dict[str, Any], detector: AnomalyDetector) -> None:
        self.checks = {code: spec for code, spec in checks.items() if code in ANSWER_CODES}
        self.no_check = checks["no_check"]
        self.no_data = checks["no_data"]
        self.detector = detector

    def verify(self, request: AnswerVerifyRequest) -> AnswerVerifyResponse:
        code = request.answer_code.value
        check = self.checks.get(code)
        if check is None or request.anomaly.code.value not in check["anomalies"]:
            return AnswerVerifyResponse(verdict="REVIEW", rule=None, explanation=self.no_check)
        series = self._series(request)
        if series is None:
            return AnswerVerifyResponse(verdict="REVIEW", rule=check["rule"], explanation=self.no_data)
        values = {point.month: point.value for point in series.points}
        if code == "RENOVATION":
            return self._renovation(check, request.anomaly, series, values)
        return self._seasonal(check, request.anomaly, values)

    def _series(self, request: AnswerVerifyRequest) -> Series | None:
        if request.anomaly.source is not None:
            return next((item for item in request.series if item.source == request.anomaly.source), None)
        return request.series[0] if len(request.series) == 1 else None

    def _review(self, check: dict[str, Any], explanation: str | None = None) -> AnswerVerifyResponse:
        return AnswerVerifyResponse(verdict="REVIEW", rule=check["rule"], explanation=explanation or check["review"])

    def _renovation(
        self, check: dict[str, Any], anomaly: AnomalyRef, series: Series, values: dict[str, int]
    ) -> AnswerVerifyResponse:
        drops = [
            item
            for item in self.detector.detect(AnomaliesRequest(series=[series])).anomalies
            if item.code == AnomalyCode.REVENUE_DROP.value
        ]
        before, after = shift_month(anomaly.month, -1), shift_month(anomaly.month, 1)
        if len(drops) != 1 or drops[0].month != anomaly.month:
            return self._review(check)
        if not values.get(before) or after not in values:
            return self._review(check, self.no_data)
        recovery = values[after] / values[before]
        if recovery < float(check["recovery_share"]):
            return self._review(check)
        explanation = check["accepted"].format(next_month=_month_name(after), recovery=round(recovery * 100))
        return AnswerVerifyResponse(verdict="ACCEPTED", rule=check["rule"], explanation=explanation)

    def _seasonal(self, check: dict[str, Any], anomaly: AnomalyRef, values: dict[str, int]) -> AnswerVerifyResponse:
        previous = shift_month(anomaly.month, -1)
        last_year, last_year_previous = shift_month(anomaly.month, -12), shift_month(anomaly.month, -13)
        if not values.get(last_year_previous) or last_year not in values:
            return self._review(check, self.no_data)
        if values.get(previous) and anomaly.month in values:
            change = (values[anomaly.month] / values[previous] - 1) * 100
        else:
            change = float(anomaly.change_pct)
        last_year_change = (values[last_year] / values[last_year_previous] - 1) * 100
        same_direction = (change < 0) == (last_year_change < 0)
        if not same_direction or abs(last_year_change) < float(check["min_share"]) * abs(change):
            return self._review(check)
        explanation = check["accepted"].format(
            last_year_month=_month_name(last_year, with_year=True),
            last_year_change=_signed(last_year_change),
            change=_signed(change),
        )
        return AnswerVerifyResponse(verdict="ACCEPTED", rule=check["rule"], explanation=explanation)
