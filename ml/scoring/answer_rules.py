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


NEGATION_WINDOW = 2

AnswerViolation = Literal[
    "FORMAT",
    "NO_EVIDENCE",
    "EVIDENCE_NOT_IN_TEXT",
    "NEGATED_EVIDENCE",
    "UNCERTAIN_REASON",
    "NO_CUE",
    "EXCLUDED_CONTEXT",
]
ANSWER_CODES = ("RENOVATION", "SEASONAL", "CLIENT_LOST", "OTHER")


def phrase(text: str) -> tuple[str, ...]:
    return tuple(normalize_text(text).split())


def find_phrase(words: list[str], parts: tuple[str, ...]) -> list[int]:
    size = len(parts)
    return [
        start
        for start in range(len(words) - size + 1)
        if all(words[start + offset].startswith(part) for offset, part in enumerate(parts))
    ]


def is_negated(words_before: list[str], words_after: list[str], negations: tuple[str, ...]) -> bool:
    if any(word in negations for word in words_before[-NEGATION_WINDOW:]):
        return True
    return words_after[:1] == ["нет"] or (words_after[:1] == ["не"] and words_after[1:2] in (["был"], ["было"]))


@dataclass(frozen=True)
class AnswerVerdict:
    code: str | None
    reason: str | None
    evidence: str | None
    violation: AnswerViolation | None


@dataclass(frozen=True)
class AnswerRules:
    min_evidence_chars: int
    uncertainty_markers: tuple[str, ...]
    negations: tuple[str, ...]
    cues: dict[str, tuple[tuple[tuple[str, ...], ...], ...]]
    excludes: dict[str, tuple[tuple[str, ...], ...]]

    @classmethod
    def from_config(cls, raw: dict[str, Any], negations: list[str]) -> "AnswerRules":
        cues = {}
        for code, spec in raw["cues"].items():
            groups = [spec["any"]] if "any" in spec else spec["all"]
            cues[code] = tuple(tuple(phrase(word) for word in group) for group in groups)
        return cls(
            min_evidence_chars=int(raw["min_evidence_chars"]),
            uncertainty_markers=tuple(normalize_text(marker) for marker in raw["uncertainty_markers"]),
            negations=tuple(normalize_text(word) for word in negations),
            cues=cues,
            excludes={code: tuple(phrase(word) for word in words) for code, words in raw["excludes"].items()},
        )

    def _has_cue(self, words: list[str], group: tuple[tuple[str, ...], ...]) -> bool:
        return any(
            not is_negated(words[:start], words[start + len(parts) :], self.negations)
            for parts in group
            for start in find_phrase(words, parts)
        )

    def check(self, content: dict[str, Any], text: str) -> AnswerVerdict:
        code = content.get("code")
        reason = _clean(content.get("reason"), MAX_REASON_LENGTH)
        evidence = _clean(content.get("evidence"), MAX_EVIDENCE_LENGTH)
        if code not in ANSWER_CODES:
            return AnswerVerdict(None, reason, evidence, "FORMAT")
        if code == "OTHER":
            return AnswerVerdict("OTHER", reason, evidence, None)
        normalized_evidence = normalize_text(evidence or "")
        normalized_text = normalize_text(text)
        if len(normalized_evidence) < self.min_evidence_chars:
            return AnswerVerdict("OTHER", reason, evidence, "NO_EVIDENCE")
        position = normalized_text.find(normalized_evidence)
        if position < 0:
            return AnswerVerdict("OTHER", reason, evidence, "EVIDENCE_NOT_IN_TEXT")
        before = normalized_text[:position].split()
        after = normalized_text[position + len(normalized_evidence) :].split()
        if is_negated(before, after, self.negations):
            return AnswerVerdict("OTHER", reason, evidence, "NEGATED_EVIDENCE")
        if any(marker in normalize_text(reason or "") for marker in self.uncertainty_markers):
            return AnswerVerdict("OTHER", reason, evidence, "UNCERTAIN_REASON")
        words = normalized_text.split()
        if any(find_phrase(words, parts) for parts in self.excludes.get(code, ())):
            return AnswerVerdict("OTHER", reason, evidence, "EXCLUDED_CONTEXT")
        if not all(self._has_cue(words, group) for group in self.cues.get(code, ())):
            return AnswerVerdict("OTHER", reason, evidence, "NO_CUE")
        return AnswerVerdict(code, reason, evidence, None)
