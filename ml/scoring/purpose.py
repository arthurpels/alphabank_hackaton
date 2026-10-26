from typing import Any

from scoring.answer_rules import PURPOSE_ANSWERS, PurposeRules
from scoring.llm import LlmClient, prompt_version
from scoring.profile import ProfileChecker
from scoring.schemas import LlmMeta, ProductCode, Purpose, PurposeRequest, PurposeResponse, PurposeStatus

LLM_SCHEMA = {
    "type": "object",
    "properties": {
        "reason": {"type": "string"},
        "evidence": {"type": "string"},
        "purpose": {"type": "string", "enum": list(PURPOSE_ANSWERS)},
    },
    "required": ["reason", "evidence", "purpose"],
}


def _normalize(text: str) -> str:
    return " ".join(text.lower().replace("ё", "е").split())


class PurposeClassifier:
    def __init__(
        self,
        keywords: dict[str, list[str]],
        llm: LlmClient | None,
        prompts: dict[str, Any],
        profile: ProfileChecker | None = None,
        *,
        dictionary: dict[str, Any],
    ) -> None:
        unknown = keywords.keys() - {code.value for code in ProductCode}
        if unknown:
            raise ValueError(f"в purpose_keywords неизвестные продукты: {', '.join(sorted(unknown))}")
        self.keywords = {product: tuple(_normalize(word) for word in words) for product, words in keywords.items()}
        self.llm = llm
        self.prompts = prompts
        self.prompt_version = prompt_version(prompts["purpose"], LLM_SCHEMA)
        self.rules = PurposeRules.from_config(prompts["purpose"]["rules"])
        self.profile = profile
        self.weak = tuple(_normalize(word) for word in dictionary.get("weak", []))
        self.spending = tuple(_normalize(word) for word in dictionary.get("spending", []))
        self.not_keywords = tuple(_normalize(word) for word in dictionary.get("not_keywords", []))
        self.question = tuple(_normalize(word) for word in dictionary.get("question", []))
        self.manipulation = tuple(_normalize(word) for word in dictionary.get("manipulation", []))

    def classify(self, request: PurposeRequest) -> PurposeResponse:
        response = self._classify(request)
        if request.okved is None or self.profile is None:
            return response
        if response.status != PurposeStatus.DECIDED or response.purpose != Purpose.MATCH:
            return response.model_copy(update={"profile": self.profile.not_checked(request.okved)})
        return response.model_copy(update={"profile": self.profile.check(request.okved, request.goal or "")})

    def _classify(self, request: PurposeRequest) -> PurposeResponse:
        product = request.product_code.value
        if product not in self.keywords:
            return self._keywords(PurposeStatus.NOT_APPLICABLE, None, applicable=False, matched=[])
        goal = _normalize(request.goal or "")
        if not goal:
            return self._keywords(PurposeStatus.UNCLEAR, None, applicable=True, matched=[])

        words = [word for word in goal.replace("?", " ? ").split() if not word.startswith(self.not_keywords)]
        manipulation = self._hits(words, self.manipulation)
        own = self._hits(words, self.keywords[product])
        other = [word for code, stems in self.keywords.items() if code != product for word in self._hits(words, stems)]
        explicit = not manipulation or bool(self._hits(words, self.spending))
        if own and not other and explicit and self._strong(words, own):
            return self._keywords(PurposeStatus.DECIDED, Purpose.MATCH, applicable=True, matched=own)
        if other and not own and explicit and self._strong(words, other):
            return self._keywords(PurposeStatus.DECIDED, Purpose.MISMATCH, applicable=True, matched=other)
        if manipulation:
            return self._keywords(PurposeStatus.UNCLEAR, None, applicable=True, matched=manipulation).model_copy(
                update={"violation": "MANIPULATION"}
            )
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

    @staticmethod
    def _hits(words: list[str], stems: tuple[str, ...]) -> list[str]:
        return [stem for stem in stems if any(word.startswith(stem) for word in words)]

    def _strong(self, words: list[str], matched: list[str]) -> bool:
        if any(phrase in " ".join(words) for phrase in self.question):
            return False
        weak_only = all(stem in self.weak for stem in matched)
        return not weak_only or len(matched) >= 2 or bool(self._hits(words, self.spending))

    def _ask_llm(self, product: str, goal: str, matched: list[str]) -> PurposeResponse:
        if self.llm is None:
            return self._keywords(PurposeStatus.UNAVAILABLE, None, applicable=True, matched=matched)
        prompts = self.prompts["purpose"]
        user = prompts["user"].format(
            product=product, product_purpose=prompts["products"].get(product, product), goal=goal
        )
        answer = self.llm.chat_json(prompts["system"], user, LLM_SCHEMA)
        if answer is None:
            return self._keywords(PurposeStatus.UNAVAILABLE, None, applicable=True, matched=matched)
        verdict = self.rules.check(answer.content, goal)
        if verdict.answer is None:
            status, purpose = PurposeStatus.UNAVAILABLE, None
        elif verdict.answer == "UNCLEAR":
            status, purpose = PurposeStatus.UNCLEAR, None
        else:
            status, purpose = PurposeStatus.DECIDED, Purpose(verdict.answer)
        return PurposeResponse(
            status=status,
            purpose=purpose,
            applicable=True,
            generated_by="LLM",
            matched_keywords=matched,
            reason=verdict.reason,
            evidence=verdict.evidence,
            violation=verdict.violation,
            llm=LlmMeta(
                model=answer.model, digest=answer.digest, prompt_version=self.prompt_version, cached=answer.cached
            ),
        )
