from eval.run import PURPOSE_BASELINE_ACCURACY, REPLAY_PATH, evaluate, local_runner
from scoring.replay import ReplayLlm


def test_golden_sets_replay_without_ollama() -> None:
    replay = ReplayLlm(REPLAY_PATH)

    results = evaluate(local_runner(replay), runs=2)
    purpose = results[0]

    assert replay.misses == []
    assert all(result.unstable() == [] for result in results)
    correct, total = purpose.accuracy()
    assert correct >= PURPOSE_BASELINE_ACCURACY * total
