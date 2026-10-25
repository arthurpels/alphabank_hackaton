import json
import logging
import threading
from pathlib import Path
from typing import Any

from scoring.llm import LlmAnswer, LlmClient, OllamaClient
from scoring.verdicts import cache_key

logger = logging.getLogger("ml.replay")


class ReplayLlm:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.answers: dict[str, dict[str, Any]] = {}
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                entry = json.loads(line)
                self.answers[entry["key"]] = entry
        models = {(entry["model"], entry["digest"]) for entry in self.answers.values()}
        if len(models) > 1:
            raise ValueError(f"в {path} ответы разных моделей: {sorted(models)}")
        self.model, self.digest = next(iter(models)) if models else ("", "")
        self.misses: list[str] = []

    def chat_json(self, system: str, user: str, schema: dict[str, Any]) -> LlmAnswer | None:
        key = cache_key(self.model, self.digest, system, user, schema)
        entry = self.answers.get(key)
        if entry is None:
            self.misses.append(user)
            logger.warning("Replay: нет записанного ответа модели для входа %s", user[:120])
            return None
        return LlmAnswer(content=entry["content"], model=entry["model"], digest=entry["digest"], cached=True)


class RecordingLlm:
    def __init__(self, client: OllamaClient) -> None:
        self.client = client
        self.entries: dict[str, dict[str, Any]] = {}
        self._lock = threading.Lock()

    def chat_json(self, system: str, user: str, schema: dict[str, Any]) -> LlmAnswer | None:
        answer = self.client.chat_json(system, user, schema)
        if answer is None:
            return None
        key = cache_key(answer.model, answer.digest, system, user, schema)
        with self._lock:
            self.entries.setdefault(key, {"key": key, "model": answer.model, "digest": answer.digest,
                                          "content": answer.content})
        return answer

    def write(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        lines = [json.dumps(self.entries[key], ensure_ascii=False, sort_keys=True) for key in sorted(self.entries)]
        path.write_text("".join(line + "\n" for line in lines), encoding="utf-8")


def open_replay(path: str) -> LlmClient | None:
    if not path:
        return None
    replay_path = Path(path)
    if not replay_path.is_file():
        logger.error("Файл replay %s не найден — LLM выключена", path)
        return None
    replay = ReplayLlm(replay_path)
    logger.info("LLM в режиме replay: %s ответов модели %s из %s", len(replay.answers), replay.model, path)
    return replay
