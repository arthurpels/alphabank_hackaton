from eval.run import PURPOSE_BASELINE_ACCURACY, REPLAY_PATH, evaluate, local_runner
from scoring.replay import ReplayLlm


def test_golden_sets_replay_without_ollama() -> None:
    replay = ReplayLlm(REPLAY_PATH)

    purpose, answers = evaluate(local_runner(replay), runs=2)

    assert replay.misses == []
    assert purpose.unstable() == [] and answers.unstable() == []
    correct, total = purpose.accuracy()
    assert correct >= PURPOSE_BASELINE_ACCURACY * total
