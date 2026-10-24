from typing import Any

from scoring.llm import LlmClient, prompt_version
from scoring.schemas import LlmMeta, ProductCode, Purpose, PurposeRequest, PurposeResponse, PurposeStatus

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
        self.prompt_version = prompt_version(prompts["purpose"], LLM_SCHEMA)

    def classify(self, request: PurposeRequest) -> PurposeResponse:
        product = request.product_code.value
        if product not in self.keywords:
            return self._keywords(PurposeStatus.NOT_APPLICABLE, None, applicable=False, matched=[])
        goal = _normalize(request.goal or "")
        if not goal:
            return self._keywords(PurposeStatus.UNCLEAR, None, applicable=True, matched=[])

        own = [word for word in self.keywords[product] if word in goal]
        other = [word for code, words in self.keywords.items() if code != product for word in words if word in goal]
        if own and not other:
            return self._keywords(PurposeStatus.DECIDED, Purpose.MATCH, applicable=True, matched=own)
        if other and not own:
            return self._keywords(PurposeStatus.DECIDED, Purpose.MISMATCH, applicable=True, matched=other)
        return self._ask_llm(product, request.goal or "", own + other)

    def _keywords(
        self, status: PurposeStatus, purpose: Purpose | None, applicable: bool, matched: list[str]
    ) -> PurposeResponse:
        return PurposeResponse(
            status=status,
            purpose=purpose,
            applicable=applicable,
            generated_by="KEYWORDS",
            matched_keywords=matched,
        )

    def _ask_llm(self, product: str, goal: str, matched: list[str]) -> PurposeResponse:
        if self.llm is None:
            return self._keywords(PurposeStatus.UNAVAILABLE, None, applicable=True, matched=matched)
        prompts = self.prompts["purpose"]
        user = prompts["user"].format(
            product=product, product_purpose=prompts["products"].get(product, product), goal=goal
        )
        answer = self.llm.chat_json(prompts["system"], user, LLM_SCHEMA)
        purpose = answer.content.get("purpose") if answer is not None else None
        if purpose not in LLM_ANSWERS:
            return self._keywords(PurposeStatus.UNAVAILABLE, None, applicable=True, matched=matched)
        reason = answer.content.get("reason")
        if isinstance(reason, str) and reason.strip():
            reason = " ".join(reason.split())[:MAX_REASON_LENGTH]
        else:
            reason = None
        return PurposeResponse(
            status=PurposeStatus.UNCLEAR if purpose == "UNCLEAR" else PurposeStatus.DECIDED,
            purpose=None if purpose == "UNCLEAR" else Purpose(purpose),
            applicable=True,
            generated_by="LLM",
            matched_keywords=matched,
            reason=reason,
            llm=LlmMeta(
                model=answer.model, digest=answer.digest, prompt_version=self.prompt_version, cached=answer.cached
            ),
        )
