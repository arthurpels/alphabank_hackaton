import json
import logging
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

import httpx
import yaml

logger = logging.getLogger("ml.llm")

SEED = 42
MAX_TOKENS = 600


class LlmClient(Protocol):
    def chat_json(self, system: str, user: str, schema: dict[str, Any]) -> dict[str, Any] | None: ...


@dataclass(frozen=True)
class LlmSettings:
    enabled: bool
    url: str
    model: str
    timeout_seconds: float
    summary_enabled: bool = False

    @classmethod
    def from_env(cls) -> "LlmSettings":
        enabled = _flag("LLM_ENABLED")
        return cls(
            enabled=enabled,
            url=os.getenv("OLLAMA_URL", "http://ollama:11434").rstrip("/"),
            model=os.getenv("OLLAMA_MODEL", "qwen3:4b"),
            timeout_seconds=float(os.getenv("LLM_TIMEOUT_SECONDS", "60")),
            summary_enabled=enabled and _flag("LLM_SUMMARY_ENABLED"),
        )


def _flag(name: str) -> bool:
    return os.getenv(name, "false").strip().lower() in {"1", "true", "yes"}


def load_prompts(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as file:
        return yaml.safe_load(file)


class OllamaClient:
    def __init__(self, settings: LlmSettings, transport: httpx.BaseTransport | None = None) -> None:
        self.settings = settings
        self._client = httpx.Client(base_url=settings.url, timeout=settings.timeout_seconds, transport=transport)

    def chat_json(self, system: str, user: str, schema: dict[str, Any]) -> dict[str, Any] | None:
        payload = {
            "model": self.settings.model,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
            "format": schema,
            "stream": False,
            "think": False,
            "options": {"temperature": 0, "seed": SEED, "num_predict": MAX_TOKENS},
        }
        try:
            response = self._client.post("/api/chat", json=payload)
            response.raise_for_status()
            content = json.loads(response.json()["message"]["content"])
        except (httpx.HTTPError, KeyError, TypeError, ValueError) as exc:
            logger.warning("LLM недоступна или ответила не по формату: %s", exc)
            return None
        if not isinstance(content, dict):
            logger.warning("LLM вернула не объект JSON")
            return None
        return content
