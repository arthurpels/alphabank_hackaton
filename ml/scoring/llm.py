import hashlib
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
CONTEXT_TOKENS = 4096
MIN_DIGEST_PREFIX = 12

DECODING_OPTIONS = {
    "temperature": 0,
    "top_k": 1,
    "top_p": 1,
    "seed": SEED,
    "num_ctx": CONTEXT_TOKENS,
    "num_predict": MAX_TOKENS,
}


@dataclass(frozen=True)
class LlmAnswer:
    content: dict[str, Any]
    model: str
    digest: str


class LlmClient(Protocol):
    def chat_json(self, system: str, user: str, schema: dict[str, Any]) -> LlmAnswer | None: ...


@dataclass(frozen=True)
class LlmSettings:
    enabled: bool
    url: str
    model: str
    timeout_seconds: float
    summary_enabled: bool = False
    model_digest: str | None = None

    @classmethod
    def from_env(cls) -> "LlmSettings":
        enabled = _flag("LLM_ENABLED")
        return cls(
            enabled=enabled,
            url=_env("OLLAMA_URL", "http://ollama:11434").rstrip("/"),
            model=_env("OLLAMA_MODEL", "qwen3:4b"),
            timeout_seconds=float(_env("LLM_TIMEOUT_SECONDS", "60")),
            summary_enabled=enabled and _flag("LLM_SUMMARY_ENABLED"),
            model_digest=_env("OLLAMA_MODEL_DIGEST", "").lower() or None,
        )


def _env(name: str, default: str) -> str:
    return os.getenv(name, "").strip() or default


def _flag(name: str) -> bool:
    return _env(name, "false").lower() in {"1", "true", "yes"}


def load_prompts(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as file:
        return yaml.safe_load(file)


def prompt_version(prompt: dict[str, Any], schema: dict[str, Any]) -> str:
    canonical = json.dumps(
        {"prompt": prompt, "schema": schema, "options": DECODING_OPTIONS}, ensure_ascii=False, sort_keys=True
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:12]


class OllamaClient:
    def __init__(self, settings: LlmSettings, transport: httpx.BaseTransport | None = None) -> None:
        if settings.model_digest is not None and len(settings.model_digest) < MIN_DIGEST_PREFIX:
            raise ValueError(f"OLLAMA_MODEL_DIGEST короче {MIN_DIGEST_PREFIX} символов")
        self.settings = settings
        self._client = httpx.Client(base_url=settings.url, timeout=settings.timeout_seconds, transport=transport)

    def model_digest(self) -> str | None:
        try:
            response = self._client.get("/api/tags")
            response.raise_for_status()
            models = response.json()["models"]
        except (httpx.HTTPError, KeyError, TypeError, ValueError) as exc:
            logger.warning("Ollama недоступна: %s", exc)
            return None
        found = next(
            (item for item in models if self.settings.model in (item.get("name"), item.get("model"))),
            None,
        )
        if found is None or not isinstance(found.get("digest"), str):
            logger.warning("Модель %s не загружена в Ollama", self.settings.model)
            return None
        digest = found["digest"].lower()
        if self.settings.model_digest is None:
            logger.warning("Модель %s не закреплена по digest, сейчас %s", self.settings.model, digest)
        elif not digest.startswith(self.settings.model_digest):
            logger.error(
                "Digest модели %s не совпадает: ждали %s, в Ollama %s — LLM не используется",
                self.settings.model,
                self.settings.model_digest,
                digest,
            )
            return None
        return digest

    def chat_json(self, system: str, user: str, schema: dict[str, Any]) -> LlmAnswer | None:
        digest = self.model_digest()
        if digest is None:
            return None
        payload = {
            "model": self.settings.model,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
            "format": schema,
            "stream": False,
            "think": False,
            "options": DECODING_OPTIONS,
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
        return LlmAnswer(content=content, model=self.settings.model, digest=digest)
