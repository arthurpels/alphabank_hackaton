from typing import Any

from scoring.answer_rules import PROFILE_ANSWERS, ProfileRules
from scoring.llm import LlmClient, prompt_version
from scoring.schemas import LlmMeta, ProfileCheck, ProfileStatus

LLM_SCHEMA = {
    "type": "object",
    "properties": {
        "reason": {"type": "string"},
        "evidence": {"type": "string"},
        "fit": {"type": "string", "enum": list(PROFILE_ANSWERS)},
    },
    "required": ["reason", "evidence", "fit"],
}
OKVED_CLASS_DIGITS = 2


class ProfileChecker:
    def __init__(self, classes: dict[str, str], llm: LlmClient | None, prompts: dict[str, Any]) -> None:
        self.classes = classes
        self.llm = llm
        self.prompts = prompts["profile"]
        self.prompt_version = prompt_version(self.prompts, LLM_SCHEMA)
        self.rules = ProfileRules.from_config(self.prompts["rules"])

    def activity(self, okved: str) -> str | None:
        code = okved[:OKVED_CLASS_DIGITS]
        name = self.classes.get(code)
        return None if name is None else f"ОКВЭД {okved} — {name}"

    def not_checked(self, okved: str) -> ProfileCheck:
        return ProfileCheck(okved=okved, activity=self.activity(okved), status=ProfileStatus.NOT_CHECKED)

    def check(self, okved: str, goal: str) -> ProfileCheck:
        activity = self.activity(okved)
        if activity is None or self.llm is None:
            return self.not_checked(okved)
        user = self.prompts["user"].format(activity=activity, goal=" ".join(goal.split()))
        answer = self.llm.chat_json(self.prompts["system"], user, LLM_SCHEMA)
        if answer is None:
            return self.not_checked(okved)
        verdict = self.rules.check(answer.content, goal)
        return ProfileCheck(
            okved=okved,
            activity=activity,
            status=ProfileStatus.NOT_CHECKED if verdict.answer is None else ProfileStatus(verdict.answer),
            reason=verdict.reason,
            evidence=verdict.evidence,
            violation=verdict.violation,
            llm=LlmMeta(
                model=answer.model, digest=answer.digest, prompt_version=self.prompt_version, cached=answer.cached
            ),
        )
