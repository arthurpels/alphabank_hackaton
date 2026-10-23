import json
import re
from typing import Any

from scoring.llm import LlmClient
from scoring.schemas import FactRef, SpotStatus, SummaryItem, SummaryRequest, SummarySpot, UnderwritingSummary

MAX_ITEMS = 5
MIN_ITEMS = 3
MAX_TEXT_LENGTH = 500

SOURCE_TITLES = {
    "BKI": "БКИ",
    "EGRUL": "ЕГРЮЛ",
    "FNS": "ФНС",
    "OFD": "ОФД",
    "MARKETPLACE": "Маркетплейс",
    "ACQUIRING": "Эквайринг",
    "REPUTATION": "Репутация",
    "CLIENT": "Клиент",
    "DOCUMENT": "Документ",
}

LLM_SCHEMA = {
    "type": "object",
    "properties": {
        "analysis": {"type": "string"},
        "items": {
            "type": "array",
            "minItems": 1,
            "maxItems": MAX_ITEMS,
            "items": {
                "type": "object",
                "properties": {"text": {"type": "string"}, "facts": {"type": "array", "items": {"type": "string"}}},
                "required": ["text", "facts"],
            },
        },
    },
    "required": ["analysis", "items"],
}


def _number(value: float) -> str:
    return f"{value:.1f}".replace(".", ",")


def _fact_id(index: int) -> str:
    return f"F{index + 1}"


class Summarizer:
    def __init__(self, llm: LlmClient | None, prompts: dict[str, Any]) -> None:
        self.llm = llm
        self.prompts = prompts

    def summarize(self, request: SummaryRequest) -> UnderwritingSummary:
        if self.llm is not None and request.facts:
            items = self._llm_items(request)
            if items:
                return UnderwritingSummary(generated_by="LLM", items=items)
        return UnderwritingSummary(generated_by="TEMPLATE", items=self._template_items(request))

    def _llm_items(self, request: SummaryRequest) -> list[SummaryItem] | None:
        facts = {_fact_id(index): fact for index, fact in enumerate(request.facts)}
        data = {
            "product": request.product_code.value,
            "amount_rub": request.amount // 100,
            "term_months": request.term_months,
            "goal": request.goal,
            "fork": {"rate_from": request.rate_from, "rate_to": request.rate_to, "approve_max": request.approve_max},
            "spots": [
                {
                    "title": spot.title,
                    "type": spot.type.value,
                    "status": spot.status.value,
                    "price_pp": spot.price_pp,
                    "client_answer": spot.answer,
                    "facts": [fact_id for fact_id, fact in facts.items() if fact.spot_id == spot.spot_id],
                }
                for spot in request.spots
            ],
            "facts": [{"id": fact_id, "source": fact.source, "label": fact.label} for fact_id, fact in facts.items()],
        }
        prompts = self.prompts["summary"]
        user = prompts["user"].format(data=json.dumps(data, ensure_ascii=False, indent=2))
        answer = self.llm.chat_json(prompts["system"], user, LLM_SCHEMA)
        if not answer or not isinstance(answer.get("items"), list):
            return None

        items = []
        for raw in answer["items"][:MAX_ITEMS]:
            if not isinstance(raw, dict) or not isinstance(raw.get("text"), str):
                continue
            text = re.sub(r"\s+", " ", raw["text"]).strip()
            fact_ids = raw.get("facts") if isinstance(raw.get("facts"), list) else []
            refs = [
                facts[fact_id] for fact_id in dict.fromkeys(fact_ids) if isinstance(fact_id, str) and fact_id in facts
            ]
            if text and len(text) <= MAX_TEXT_LENGTH and refs:
                items.append(SummaryItem(text=text, facts=refs))
        return items or None

    def _template_items(self, request: SummaryRequest) -> list[SummaryItem]:
        items = [SummaryItem(text=self._fork_text(request), facts=[])]
        open_spots = sorted(
            (spot for spot in request.spots if spot.status != SpotStatus.CLOSED),
            key=lambda spot: (-(spot.price_pp or 0), spot.title),
        )
        for spot in open_spots[: MAX_ITEMS - 1]:
            items.append(
                SummaryItem(
                    text=self._spot_text(spot),
                    facts=[fact for fact in request.facts if fact.spot_id == spot.spot_id],
                )
            )

        unlinked: dict[str, list[FactRef]] = {}
        for fact in request.facts:
            if fact.spot_id is None:
                unlinked.setdefault(fact.source, []).append(fact)
        for source, facts in unlinked.items():
            if len(items) >= MIN_ITEMS:
                break
            title = SOURCE_TITLES.get(source, source)
            items.append(SummaryItem(text=f"{title}: {'; '.join(fact.label for fact in facts)}.", facts=facts))
        return items

    def _fork_text(self, request: SummaryRequest) -> str:
        fork = f"Вилка {_number(request.rate_from)}–{_number(request.rate_to)} %"
        threshold = _number(request.approve_max)
        if request.rate_to <= request.approve_max:
            return f"{fork} целиком в зоне одобрения (порог {threshold} %)."
        if request.rate_from > request.approve_max:
            return f"{fork} целиком выше порога одобрения {threshold} %."
        return f"{fork} пересекает порог одобрения {threshold} % — решение зависит от открытых пятен."

    def _spot_text(self, spot: SummarySpot) -> str:
        state = "ответ ждёт проверки" if spot.status == SpotStatus.ON_REVIEW else "не закрыто"
        price = f", до {_number(spot.price_pp)} п.п." if spot.price_pp else "."
        text = f"«{spot.title}» — {state}{price}"
        if spot.answer:
            text += f" Ответ клиента: «{spot.answer.strip()}»."
        return text
