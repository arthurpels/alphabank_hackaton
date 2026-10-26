import json

import httpx
import pytest

from main import PROMPTS_PATH, engine
from scoring.llm import LlmAnswer, LlmSettings, OllamaClient, load_prompts
from scoring.purpose import PurposeClassifier
from scoring.schemas import PurposeRequest
from scoring.verdicts import CachedLlm, VerdictCache, cache_key, open_cache
from scoring.warmup import LlmWarmup
from tests.contract import client

DIGEST = "359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7"
TAGS = {"models": [{"name": "qwen3:4b", "model": "qwen3:4b", "digest": DIGEST}]}
SCHEMA = {"type": "object"}


class FakeOllama:
    def __init__(self) -> None:
        self.chat_calls = 0
        self.down = False
        self.chat_status = 200
        self.content = '{"reason": "оборудование", "evidence": "оборудования", "purpose": "MISMATCH"}'
        self.before_reply = None

    def handler(self, request: httpx.Request) -> httpx.Response:
        if self.down:
            raise httpx.ConnectError("нет связи", request=request)
        if request.url.path == "/api/tags":
            return httpx.Response(200, json=TAGS)
        if request.url.path == "/api/generate":
            return httpx.Response(200, json={"done": True})
        self.chat_calls += 1
        if self.before_reply:
            self.before_reply()
        if self.chat_status != 200:
            return httpx.Response(self.chat_status)
        return httpx.Response(200, json={"message": {"content": self.content}})

    def client(self, pinned: str | None = DIGEST) -> OllamaClient:
        settings = LlmSettings(
            enabled=True, url="http://ollama:11434", model="qwen3:4b", timeout_seconds=5, model_digest=pinned
        )
        return OllamaClient(settings, transport=httpx.MockTransport(self.handler))


@pytest.fixture
def ollama() -> FakeOllama:
    return FakeOllama()


@pytest.fixture
def cache(tmp_path) -> VerdictCache:
    return VerdictCache(tmp_path / "llm" / "verdicts.sqlite")


def test_cache_roundtrip_survives_restart(tmp_path) -> None:
    path = tmp_path / "verdicts.sqlite"
    VerdictCache(path).put("k", LlmAnswer(content={"purpose": "MATCH", "reason": "ё"}, model="qwen3:4b", digest=DIGEST))
    reopened = VerdictCache(path)
    assert reopened.get("k") == LlmAnswer(
        content={"purpose": "MATCH", "reason": "ё"}, model="qwen3:4b", digest=DIGEST, cached=True
    )
    assert reopened.count() == 1
    assert reopened.get("other") is None


def test_first_answer_wins(cache: VerdictCache) -> None:
    cache.put("k", LlmAnswer(content={"purpose": "MATCH"}, model="qwen3:4b", digest=DIGEST))
    cache.put("k", LlmAnswer(content={"purpose": "MISMATCH"}, model="qwen3:4b", digest=DIGEST))
    assert cache.get("k").content == {"purpose": "MATCH"}
    assert cache.count() == 1


def test_repeated_call_is_served_from_cache(ollama: FakeOllama, cache: VerdictCache) -> None:
    llm = CachedLlm(ollama.client(), cache)
    first = llm.chat_json("sys", "user", SCHEMA)
    second = llm.chat_json("sys", "user", SCHEMA)
    assert (first.cached, second.cached) == (False, True)
    assert (first.content, first.digest) == (second.content, second.digest)
    assert ollama.chat_calls == 1


def test_failed_answer_is_not_cached(ollama: FakeOllama, cache: VerdictCache) -> None:
    llm = CachedLlm(ollama.client(), cache)
    ollama.chat_status = 500
    assert llm.chat_json("sys", "user", SCHEMA) is None
    ollama.chat_status = 200
    assert llm.chat_json("sys", "user", SCHEMA).cached is False
    assert (ollama.chat_calls, cache.count()) == (2, 1)


def test_cache_survives_restart_without_calling_model(ollama: FakeOllama, tmp_path) -> None:
    path = tmp_path / "verdicts.sqlite"
    CachedLlm(ollama.client(), VerdictCache(path)).chat_json("sys", "user", SCHEMA)
    after_restart = CachedLlm(ollama.client(), VerdictCache(path)).chat_json("sys", "user", SCHEMA)
    assert after_restart.cached is True
    assert ollama.chat_calls == 1


def test_pinned_model_answers_from_cache_when_ollama_is_down(ollama: FakeOllama, cache: VerdictCache) -> None:
    llm = CachedLlm(ollama.client(), cache)
    llm.chat_json("sys", "user", SCHEMA)
    ollama.down = True
    assert llm.chat_json("sys", "user", SCHEMA).cached is True
    assert llm.chat_json("sys", "другой вход", SCHEMA) is None


def test_unpinned_model_needs_live_digest(ollama: FakeOllama, cache: VerdictCache) -> None:
    llm = CachedLlm(ollama.client(pinned=None), cache)
    llm.chat_json("sys", "user", SCHEMA)
    ollama.down = True
    assert llm.chat_json("sys", "user", SCHEMA) is None


def test_concurrent_duplicate_returns_the_stored_answer(ollama: FakeOllama, cache: VerdictCache) -> None:
    key = cache_key("qwen3:4b", DIGEST, "sys", "user", SCHEMA)
    winner = LlmAnswer(content={"purpose": "MISMATCH"}, model="qwen3:4b", digest=DIGEST)
    ollama.before_reply = lambda: cache.put(key, winner)
    answer = CachedLlm(ollama.client(), cache).chat_json("sys", "user", SCHEMA)
    assert (answer.content, answer.cached) == ({"purpose": "MISMATCH"}, True)


@pytest.mark.parametrize(
    "changed",
    [
        ("qwen3:8b", DIGEST, "sys", "user", SCHEMA),
        ("qwen3:4b", "0" * 64, "sys", "user", SCHEMA),
        ("qwen3:4b", DIGEST, "sys2", "user", SCHEMA),
        ("qwen3:4b", DIGEST, "sys", "user2", SCHEMA),
        ("qwen3:4b", DIGEST, "sys", "user", {"type": "array"}),
    ],
)
def test_cache_key_covers_model_digest_prompt_and_schema(changed: tuple) -> None:
    base = cache_key("qwen3:4b", DIGEST, "sys", "user", SCHEMA)
    assert base == cache_key("qwen3:4b", DIGEST, "sys", "user", dict(SCHEMA))
    assert cache_key(*changed) != base


def test_purpose_reports_cached_verdict(ollama: FakeOllama, cache: VerdictCache) -> None:
    classifier = PurposeClassifier(
        engine.purpose_keywords,
        CachedLlm(ollama.client(), cache),
        load_prompts(PROMPTS_PATH),
        dictionary=engine.purpose_dictionary,
    )
    request = PurposeRequest(product_code="WORKING_CAPITAL", goal="закупка оборудования для цеха")
    first = classifier.classify(request).model_dump(by_alias=True, mode="json")
    second = classifier.classify(request).model_dump(by_alias=True, mode="json")
    assert (first["llm"]["cached"], second["llm"]["cached"]) == (False, True)
    first["llm"]["cached"] = True
    assert first == second
    assert ollama.chat_calls == 1


def test_unwritable_cache_path_disables_cache(tmp_path) -> None:
    blocker = tmp_path / "file"
    blocker.write_text("не каталог")
    assert open_cache(str(blocker / "verdicts.sqlite")) is None


def test_warmup_disabled_without_llm() -> None:
    warmup = LlmWarmup(None, None)
    assert warmup.step() == "disabled"
    assert warmup.health().model_dump(by_alias=True) == {
        "status": "disabled",
        "model": None,
        "digest": None,
        "cachedVerdicts": None,
    }


def test_warmup_becomes_ready_and_reports_cache(ollama: FakeOllama, cache: VerdictCache) -> None:
    llm_client = ollama.client()
    CachedLlm(llm_client, cache).chat_json("sys", "user", SCHEMA)
    warmup = LlmWarmup(llm_client, cache)
    assert warmup.status == "warming"
    assert warmup.step() == "ready"
    assert warmup.health().model_dump(by_alias=True) == {
        "status": "ready",
        "model": "qwen3:4b",
        "digest": DIGEST,
        "cachedVerdicts": 1,
    }


def test_warmup_is_unavailable_while_ollama_is_down(ollama: FakeOllama) -> None:
    ollama.down = True
    warmup = LlmWarmup(ollama.client(), None)
    assert warmup.step() == "unavailable"
    ollama.down = False
    assert warmup.step() == "ready"


def test_health_llm_endpoint_reports_disabled_by_default() -> None:
    assert client.get("/health/llm").json() == {
        "status": "disabled",
        "model": None,
        "digest": None,
        "cachedVerdicts": None,
    }


def test_import_records_fills_cache_once(tmp_path) -> None:
    from scoring.verdicts import import_records

    records = tmp_path / "answers.jsonl"
    entry = {"key": "k1", "model": "qwen3:4b", "digest": DIGEST, "content": {"purpose": "MATCH"}}
    records.write_text(json.dumps(entry) + "\n\n", encoding="utf-8")
    cache = VerdictCache(tmp_path / "verdicts.sqlite")
    assert import_records(cache, records) == 1
    assert import_records(cache, records) == 0
    assert cache.get("k1") == LlmAnswer(content={"purpose": "MATCH"}, model="qwen3:4b", digest=DIGEST, cached=True)


def test_imported_replay_answers_are_served_by_live_cache(ollama: FakeOllama, tmp_path) -> None:
    from scoring.verdicts import import_records

    key = cache_key("qwen3:4b", DIGEST, "sys", "user", SCHEMA)
    records = tmp_path / "answers.jsonl"
    records.write_text(
        json.dumps({"key": key, "model": "qwen3:4b", "digest": DIGEST, "content": {"purpose": "MISMATCH"}}) + "\n",
        encoding="utf-8",
    )
    cache = VerdictCache(tmp_path / "verdicts.sqlite")
    import_records(cache, records)
    answer = CachedLlm(ollama.client(), cache).chat_json("sys", "user", SCHEMA)
    assert (answer.content, answer.cached, ollama.chat_calls) == ({"purpose": "MISMATCH"}, True, 0)
