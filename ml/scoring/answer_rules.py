import re
from dataclasses import dataclass
from typing import Any, Literal

PURPOSE_ANSWERS = ("MATCH", "MISMATCH", "UNCLEAR")
MAX_REASON_LENGTH = 500
MAX_EVIDENCE_LENGTH = 200

Violation = Literal["FORMAT", "NO_EVIDENCE", "EVIDENCE_NOT_IN_GOAL", "GENERIC_EVIDENCE", "UNCERTAIN_REASON"]
MIN_WORD_CHARS = 3


def normalize_text(text: str) -> str:
    text = text.lower().replace("ё", "е")
    text = re.sub(r"[«»\"'“”„.,!?;:()\[\]—–-]+", " ", text)
    return " ".join(text.split())


def _clean(value: Any, limit: int) -> str | None:
    if not isinstance(value, str):
        return None
    value = " ".join(value.split())
    return value[:limit] or None


@dataclass(frozen=True)
class PurposeVerdict:
    answer: str | None
    reason: str | None
    evidence: str | None
    violation: Violation | None


@dataclass(frozen=True)
class PurposeRules:
    min_evidence_chars: int
    generic_words: tuple[str, ...]
    uncertainty_markers: tuple[str, ...]

    @classmethod
    def from_config(cls, raw: dict[str, Any]) -> "PurposeRules":
        return cls(
            min_evidence_chars=int(raw["min_evidence_chars"]),
            generic_words=tuple(normalize_text(word) for word in raw["generic_words"]),
            uncertainty_markers=tuple(normalize_text(marker) for marker in raw["uncertainty_markers"]),
        )

    def check(self, content: dict[str, Any], goal: str) -> PurposeVerdict:
        answer = content.get("purpose")
        reason = _clean(content.get("reason"), MAX_REASON_LENGTH)
        evidence = _clean(content.get("evidence"), MAX_EVIDENCE_LENGTH)
        if answer not in PURPOSE_ANSWERS:
            return PurposeVerdict(None, reason, evidence, "FORMAT")
        if answer == "UNCLEAR":
            return PurposeVerdict("UNCLEAR", reason, evidence, None)
        normalized_evidence = normalize_text(evidence or "")
        if len(normalized_evidence) < self.min_evidence_chars:
            return PurposeVerdict("UNCLEAR", reason, evidence, "NO_EVIDENCE")
        if normalized_evidence not in normalize_text(goal):
            return PurposeVerdict("UNCLEAR", reason, evidence, "EVIDENCE_NOT_IN_GOAL")
        if self._only_generic(normalized_evidence):
            return PurposeVerdict("UNCLEAR", reason, evidence, "GENERIC_EVIDENCE")
        normalized_reason = normalize_text(reason or "")
        if any(marker in normalized_reason for marker in self.uncertainty_markers):
            return PurposeVerdict("UNCLEAR", reason, evidence, "UNCERTAIN_REASON")
        return PurposeVerdict(answer, reason, evidence, None)

    def _only_generic(self, normalized_evidence: str) -> bool:
        words = [word for word in normalized_evidence.split() if len(word) >= MIN_WORD_CHARS]
        return all(word.startswith(self.generic_words) for word in words)


@dataclass(frozen=True)
class SummaryRules:
    forbidden_phrases: tuple[str, ...]

    @classmethod
    def from_config(cls, raw: dict[str, Any]) -> "SummaryRules":
        return cls(forbidden_phrases=tuple(normalize_text(phrase) for phrase in raw["forbidden_phrases"]))

    def allows(self, text: str) -> bool:
        normalized = normalize_text(text)
        return not any(phrase in normalized for phrase in self.forbidden_phrases)
