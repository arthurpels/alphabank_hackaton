import hashlib
import json
import logging
import sqlite3
import threading
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from scoring.llm import DECODING_OPTIONS, LlmAnswer, OllamaClient

logger = logging.getLogger("ml.verdicts")


def cache_key(model: str, digest: str, system: str, user: str, schema: dict[str, Any]) -> str:
    canonical = json.dumps(
        {
            "model": model,
            "digest": digest,
            "system": system,
            "user": user,
            "schema": schema,
            "options": DECODING_OPTIONS,
        },
        ensure_ascii=False,
        sort_keys=True,
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


class VerdictCache:
    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()
        self._connection = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self._connection.execute("PRAGMA journal_mode=WAL")
        self._connection.execute(
            "CREATE TABLE IF NOT EXISTS verdicts ("
            "key TEXT PRIMARY KEY, content TEXT NOT NULL, model TEXT NOT NULL, "
            "digest TEXT NOT NULL, created_at TEXT NOT NULL)"
        )

    def get(self, key: str) -> LlmAnswer | None:
        with self._lock:
            row = self._connection.execute(
                "SELECT content, model, digest FROM verdicts WHERE key = ?", (key,)
            ).fetchone()
        if row is None:
            return None
        content, model, digest = row
        return LlmAnswer(content=json.loads(content), model=model, digest=digest, cached=True)

    def put(self, key: str, answer: LlmAnswer) -> None:
        with self._lock:
            self._connection.execute(
                "INSERT OR IGNORE INTO verdicts (key, content, model, digest, created_at) VALUES (?, ?, ?, ?, ?)",
                (
                    key,
                    json.dumps(answer.content, ensure_ascii=False, sort_keys=True),
                    answer.model,
                    answer.digest,
                    datetime.now(UTC).isoformat(),
                ),
            )

    def count(self) -> int:
        with self._lock:
            return self._connection.execute("SELECT COUNT(*) FROM verdicts").fetchone()[0]


def open_cache(path: str) -> VerdictCache | None:
    try:
        return VerdictCache(Path(path))
    except (OSError, sqlite3.Error) as exc:
        logger.error("Кеш вердиктов LLM недоступен (%s): %s — ответы не запоминаются", path, exc)
        return None


class CachedLlm:
    def __init__(self, client: OllamaClient, cache: VerdictCache) -> None:
        self.client = client
        self.cache = cache

    def chat_json(self, system: str, user: str, schema: dict[str, Any]) -> LlmAnswer | None:
        digest = self.client.settings.model_digest or self.client.model_digest()
        if digest is None:
            return None
        key = cache_key(self.client.settings.model, digest, system, user, schema)
        cached = self.cache.get(key)
        if cached is not None:
            return cached
        answer = self.client.chat_json(system, user, schema)
        if answer is None:
            return None
        self.cache.put(key, answer)
        stored = self.cache.get(key)
        if stored is None or stored.content == answer.content:
            return answer
        return stored
