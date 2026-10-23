from typing import Any

from scoring.llm import LlmClient
from scoring.schemas import ProductCode, Purpose, PurposeRequest, PurposeResponse

LLM_ANSWERS = ("MATCH", "MISMATCH", "UNCLEAR")
MAX_REASON_LENGTH = 500

LLM_SCHEMA = {
    "type": "object",
    "properties": {
        "reason": {"type": "string"},
        "purpose": {"type": "string", "enum": list(LLM_ANSWERS)},
    },
    "required": ["reason", "purpose"],
}


def _normalize(text: str) -> str:
    return " ".join(text.lower().replace("ё", "е").split())


class PurposeClassifier:
    def __init__(self, keywords: dict[str, list[str]], llm: LlmClient | None, prompts: dict[str, Any]) -> None:
        unknown = keywords.keys() - {code.value for code in ProductCode}
        if unknown:
            raise ValueError(f"в purpose_keywords неизвестные продукты: {', '.join(sorted(unknown))}")
        self.keywords = {product: tuple(_normalize(word) for word in words) for product, words in keywords.items()}
        self.llm = llm
        self.prompts = prompts

    def classify(self, request: PurposeRequest) -> PurposeResponse:
        product = request.product_code.value
        if product not in self.keywords:
            return self._keywords(None, applicable=False, matched=[])
        goal = _normalize(request.goal or "")
        if not goal:
            return self._keywords(None, applicable=True, matched=[])

        own = [word for word in self.keywords[product] if word in goal]
        other = [word for code, words in self.keywords.items() if code != product for word in words if word in goal]
        if own and not other:
            return self._keywords(Purpose.MATCH, applicable=True, matched=own)
        if other and not own:
            return self._keywords(Purpose.MISMATCH, applicable=True, matched=other)

        answer = self._ask_llm(product, request.goal or "")
        if answer is None:
            return self._keywords(None, applicable=True, matched=own + other)
        llm_purpose, reason = answer
        return PurposeResponse(
            purpose=None if llm_purpose == "UNCLEAR" else Purpose(llm_purpose),
            applicable=True,
            generated_by="LLM",
            matched_keywords=own + other,
            reason=reason,
        )

    def _keywords(self, purpose: Purpose | None, applicable: bool, matched: list[str]) -> PurposeResponse:
        return PurposeResponse(
            purpose=purpose, applicable=applicable, generated_by="KEYWORDS", matched_keywords=matched, reason=None
        )

    def _ask_llm(self, product: str, goal: str) -> tuple[str, str | None] | None:
        if self.llm is None:
            return None
        prompts = self.prompts["purpose"]
        user = prompts["user"].format(
            product=product, product_purpose=prompts["products"].get(product, product), goal=goal
        )
        answer = self.llm.chat_json(prompts["system"], user, LLM_SCHEMA) or {}
        purpose = answer.get("purpose")
        if purpose not in LLM_ANSWERS:
            return None
        reason = answer.get("reason")
        if not isinstance(reason, str) or not reason.strip():
            return purpose, None
        return purpose, " ".join(reason.split())[:MAX_REASON_LENGTH]
