from typing import Any

import pytest

from main import engine, okved_classes, prompts
from scoring.llm import LlmAnswer
from scoring.profile import ProfileChecker
from scoring.purpose import PurposeClassifier
from scoring.schemas import ProductCode, ProfileStatus, Purpose, PurposeRequest

DIGEST = "359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7"


class FakeLlm:
    def __init__(self, answer: dict[str, Any] | None) -> None:
        self.answer = answer
        self.users: list[str] = []

    def chat_json(self, system: str, user: str, schema: dict[str, Any]) -> LlmAnswer | None:
        self.users.append(user)
        return None if self.answer is None else LlmAnswer(content=self.answer, model="qwen3:4b", digest=DIGEST)


def classify(answer: dict[str, Any] | None, goal: str, okved: str | None = "26.20",
             product: ProductCode = ProductCode.EQUIPMENT):
    llm = FakeLlm(answer)
    classifier = PurposeClassifier(
        engine.purpose_keywords,
        None,
        prompts,
        ProfileChecker(okved_classes, llm, prompts),
        dictionary=engine.purpose_dictionary,
    )
    return classifier.classify(PurposeRequest(product_code=product, goal=goal, okved=okved)), llm


def not_fit(evidence: str, reason: str = "Кофемашины не нужны производителю компьютеров") -> dict[str, str]:
    return {"reason": reason, "evidence": evidence, "fit": "DOES_NOT_FIT"}


def test_purchase_from_other_activity_does_not_fit() -> None:
    response, llm = classify(not_fit("кофемашины"), "кофемашины для второй точки")

    assert (response.status.value, response.purpose) == ("DECIDED", Purpose.MATCH)
    assert response.profile.status == ProfileStatus.DOES_NOT_FIT
    assert response.profile.activity == "ОКВЭД 26.20 — Производство компьютеров, электронных и оптических изделий"
    assert response.profile.evidence == "кофемашины"
    assert response.profile.llm.model == "qwen3:4b"
    assert "Деятельность компании: ОКВЭД 26.20 — Производство компьютеров" in llm.users[0]


def test_purchase_needed_by_any_business_always_fits() -> None:
    response, _ = classify(not_fit("ноутбуки"), "ноутбуки для бухгалтерии", okved="56.30")

    assert response.profile.status == ProfileStatus.FITS
    assert response.profile.violation == "UNIVERSAL_PURCHASE"


def test_universal_word_outside_purchase_does_not_excuse_it() -> None:
    response, _ = classify(not_fit("кофемашины"), "кофемашины, они точно нужны для производства компьютеров")

    assert response.profile.status == ProfileStatus.DOES_NOT_FIT


@pytest.mark.parametrize(
    ("answer", "violation"),
    [
        (not_fit(""), "NO_EVIDENCE"),
        (not_fit("тракторы"), "EVIDENCE_NOT_IN_GOAL"),
        (not_fit("кофемашины", "Возможно, кофемашины не нужны, но неочевидно"), "UNCERTAIN_REASON"),
    ],
)
def test_doubtful_does_not_fit_is_unclear(answer: dict[str, str], violation: str) -> None:
    response, _ = classify(answer, "кофемашины для второй точки")

    assert response.profile.status == ProfileStatus.UNCLEAR
    assert response.profile.violation == violation


@pytest.mark.parametrize("answer", [None, {"reason": "?", "evidence": "", "fit": "MAYBE"}])
def test_no_answer_or_bad_format_is_not_checked(answer: dict[str, str] | None) -> None:
    response, _ = classify(answer, "кофемашины для второй точки")

    assert response.profile.status == ProfileStatus.NOT_CHECKED


def test_fit_answer_is_kept() -> None:
    response, _ = classify({"reason": "Пекарне нужна печь", "evidence": "печь", "fit": "FITS"}, "печь", okved="10.71")

    assert response.profile.status == ProfileStatus.FITS
    assert response.profile.activity == "ОКВЭД 10.71 — Производство пищевых продуктов"


def test_profile_is_checked_only_for_matching_goal() -> None:
    response, llm = classify(not_fit("станок"), "покупка станка", product=ProductCode.WORKING_CAPITAL)

    assert response.purpose == Purpose.MISMATCH
    assert response.profile.status == ProfileStatus.NOT_CHECKED
    assert llm.users == []


def test_unknown_okved_class_is_not_checked() -> None:
    response, llm = classify(not_fit("кофемашины"), "кофемашины", okved="04.11")

    assert response.profile.status == ProfileStatus.NOT_CHECKED
    assert response.profile.activity is None
    assert llm.users == []


def test_without_okved_there_is_no_profile() -> None:
    response, llm = classify(not_fit("кофемашины"), "кофемашины", okved=None)

    assert response.profile is None
    assert llm.users == []


def test_without_llm_profile_is_not_checked() -> None:
    classifier = PurposeClassifier(
        engine.purpose_keywords,
        None,
        prompts,
        ProfileChecker(okved_classes, None, prompts),
        dictionary=engine.purpose_dictionary,
    )

    response = classifier.classify(PurposeRequest(product_code=ProductCode.EQUIPMENT, goal="кофемашины", okved="26.20"))

    assert response.profile.status == ProfileStatus.NOT_CHECKED


def test_okved_reference_covers_all_classes_with_names() -> None:
    assert len(okved_classes) == 88
    assert all(len(code) == 2 and code.isdigit() and name.strip() for code, name in okved_classes.items())
