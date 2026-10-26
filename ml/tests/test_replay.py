from pathlib import Path
from typing import Any

from main import engine, prompts
from scoring.llm import LlmAnswer
from scoring.purpose import PurposeClassifier
from scoring.replay import RecordingLlm, ReplayLlm, open_replay
from scoring.schemas import ProductCode, PurposeRequest, PurposeStatus

DIGEST = "359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7"


class FakeOllama:
    def __init__(self) -> None:
        self.calls = 0

    def chat_json(self, system: str, user: str, schema: dict[str, Any]) -> LlmAnswer | None:
        self.calls += 1
        return LlmAnswer(content={"reason": "Стиральные машины — оборудование прачечной",
                                  "evidence": "Стиральные машины", "purpose": "MATCH"},
                         model="qwen3:4b", digest=DIGEST)


def classify(llm, goal: str):
    classifier = PurposeClassifier(engine.purpose_keywords, llm, prompts, dictionary=engine.purpose_dictionary)
    return classifier.classify(PurposeRequest(product_code=ProductCode.EQUIPMENT, goal=goal))


def test_recorded_answer_replays_without_model(tmp_path: Path) -> None:
    fake = FakeOllama()
    recorder = RecordingLlm(fake)
    live = classify(recorder, "Стиральные машины для прачечной")
    path = tmp_path / "answers.jsonl"
    recorder.write(path)

    replayed = classify(ReplayLlm(path), "Стиральные машины для прачечной")

    assert fake.calls == 1
    assert replayed.model_dump(exclude={"llm"}) == live.model_dump(exclude={"llm"})
    assert replayed.llm.cached is True
    assert (replayed.llm.model, replayed.llm.digest) == ("qwen3:4b", DIGEST)


def test_missing_answer_is_unavailable(tmp_path: Path) -> None:
    recorder = RecordingLlm(FakeOllama())
    classify(recorder, "Стиральные машины для прачечной")
    path = tmp_path / "answers.jsonl"
    recorder.write(path)
    replay = ReplayLlm(path)

    response = classify(replay, "Швейные машинки в ателье")

    assert response.status == PurposeStatus.UNAVAILABLE
    assert len(replay.misses) == 1


def test_record_file_is_sorted_and_stable(tmp_path: Path) -> None:
    recorder = RecordingLlm(FakeOllama())
    for goal in ("Швейные машинки в ателье", "Стиральные машины для прачечной"):
        classify(recorder, goal)
    first, second = tmp_path / "a.jsonl", tmp_path / "b.jsonl"
    recorder.write(first)
    ReplayLlm(first)
    recorder.write(second)

    lines = first.read_text(encoding="utf-8").splitlines()
    assert lines == sorted(lines, key=lambda line: line[line.index('"key"'):])
    assert first.read_bytes() == second.read_bytes()


def test_open_replay_without_file_disables_llm(tmp_path: Path) -> None:
    assert open_replay("") is None
    assert open_replay(str(tmp_path / "missing.jsonl")) is None
