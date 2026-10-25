import argparse
import json
import logging
import statistics
import sys
import time
import urllib.request
from collections import Counter
from collections.abc import Callable
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

ML_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ML_DIR))

from scoring.answers import AnswerClassifier  # noqa: E402
from scoring.engine import Engine  # noqa: E402
from scoring.llm import LlmAnswer, LlmClient, LlmSettings, OllamaClient, load_prompts  # noqa: E402
from scoring.profile import ProfileChecker  # noqa: E402
from scoring.purpose import PurposeClassifier  # noqa: E402
from scoring.replay import RecordingLlm, ReplayLlm  # noqa: E402
from scoring.schemas import AnswerClassifyRequest, PurposeRequest  # noqa: E402

DATA_DIR = ML_DIR / "eval" / "data"
REPLAY_PATH = ML_DIR / "eval" / "replay" / "llm_answers.jsonl"
REPORT_START = "<!-- eval:start -->"
REPORT_END = "<!-- eval:end -->"
KINDS = ("simple", "hard", "trap")
PURPOSE_MIN_ACCURACY = 0.9
PURPOSE_BASELINE_ACCURACY = 0.87


@dataclass(frozen=True)
class GoldenSet:
    name: str
    title: str
    path: Path
    endpoint: str

    def rows(self) -> list[dict[str, Any]]:
        return [json.loads(line) for line in self.path.read_text(encoding="utf-8").splitlines() if line.strip()]


PURPOSE = GoldenSet("purpose", "Цель кредита (M12)", DATA_DIR / "purpose_golden.jsonl", "/purpose")
ANSWERS = GoldenSet("answers", "Ответы на аномалии (M13)", DATA_DIR / "anomaly_answers_golden.jsonl",
                    "/answers/classify")
PROFILE = GoldenSet("profile", "Цель × деятельность (M33)", DATA_DIR / "purpose_profile_golden.jsonl", "/purpose")
SETS = (PURPOSE, ANSWERS, PROFILE)


class TimedLlm:
    def __init__(self, inner: LlmClient) -> None:
        self.inner = inner
        self.seconds: list[float] = []

    def chat_json(self, system: str, user: str, schema: dict[str, Any]) -> LlmAnswer | None:
        started = time.perf_counter()
        answer = self.inner.chat_json(system, user, schema)
        self.seconds.append(time.perf_counter() - started)
        return answer


@dataclass
class SetResult:
    golden: GoldenSet
    rows: list[dict[str, Any]]
    runs: list[dict[str, dict[str, Any]]] = field(default_factory=list)

    @property
    def answers(self) -> dict[str, dict[str, Any]]:
        return self.runs[0]

    def accuracy(self, rows: list[dict[str, Any]] | None = None) -> tuple[int, int]:
        rows = self.rows if rows is None else rows
        return sum(self.answers[row["id"]]["value"] == row["expected"] for row in rows), len(rows)

    def errors(self) -> list[dict[str, Any]]:
        return [row for row in self.rows if self.answers[row["id"]]["value"] != row["expected"]]

    def unstable(self) -> list[str]:
        return [row["id"] for row in self.rows
                if any(run[row["id"]]["comparable"] != self.answers[row["id"]]["comparable"] for run in self.runs[1:])]


def purpose_value(response: dict[str, Any]) -> str:
    return response["purpose"] if response["status"] == "DECIDED" else response["status"]


def answer_value(response: dict[str, Any]) -> str:
    return response["code"] or response["status"]


def profile_summary(response: dict[str, Any]) -> dict[str, Any]:
    purpose = purpose_value(response)
    profile = response.get("profile") or {}
    if purpose != "MATCH":
        value, status = f"цель {purpose}", response["status"]
    else:
        value = profile.get("status") or "NOT_CHECKED"
        status = "UNAVAILABLE" if value == "NOT_CHECKED" else "DECIDED"
    return {"value": value, "generatedBy": "LLM" if profile.get("llm") else "KEYWORDS", "status": status,
            "violation": profile.get("violation"), "comparable": comparable(response)}


def comparable(response: dict[str, Any]) -> str:
    llm = response.get("llm")
    stripped = {**response, "llm": llm and {**llm, "cached": None}}
    return json.dumps(stripped, sort_keys=True, ensure_ascii=False)


def summarize(response: dict[str, Any], value: str) -> dict[str, Any]:
    return {"value": value, "generatedBy": response["generatedBy"], "status": response["status"],
            "violation": response.get("violation"), "comparable": comparable(response)}


def local_runner(llm: LlmClient | None) -> dict[str, Callable[[dict[str, Any]], dict[str, Any]]]:
    engine = Engine.from_yaml(ML_DIR / "scoring_rules.yaml")
    prompts = load_prompts(ML_DIR / "llm.yaml")
    okved = yaml.safe_load((ML_DIR / "okved.yaml").read_text(encoding="utf-8"))["classes"]
    purpose = PurposeClassifier(engine.purpose_keywords, llm, prompts, ProfileChecker(okved, llm, prompts))
    answers = AnswerClassifier(engine.answer_keywords, llm, prompts)

    def run_purpose(row: dict[str, Any]) -> dict[str, Any]:
        response = purpose.classify(PurposeRequest(product_code=row["productCode"], goal=row["text"]))
        dumped = response.model_dump(mode="json", by_alias=True)
        return summarize(dumped, purpose_value(dumped))

    def run_answers(row: dict[str, Any]) -> dict[str, Any]:
        response = answers.classify(AnswerClassifyRequest(text=row["text"], anomaly=row["anomaly"]))
        dumped = response.model_dump(mode="json", by_alias=True)
        return summarize(dumped, answer_value(dumped))

    def run_profile(row: dict[str, Any]) -> dict[str, Any]:
        request = PurposeRequest(product_code=row["productCode"], goal=row["text"], okved=row["okved"])
        return profile_summary(purpose.classify(request).model_dump(mode="json", by_alias=True))

    return {PURPOSE.name: run_purpose, ANSWERS.name: run_answers, PROFILE.name: run_profile}


def http_runner(url: str) -> dict[str, Callable[[dict[str, Any]], dict[str, Any]]]:
    def post(endpoint: str, body: dict[str, Any]) -> dict[str, Any]:
        request = urllib.request.Request(f"{url}{endpoint}", json.dumps(body).encode("utf-8"),
                                         {"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=600) as response:
            return json.load(response)

    def run_purpose(row: dict[str, Any]) -> dict[str, Any]:
        response = post(PURPOSE.endpoint, {"productCode": row["productCode"], "goal": row["text"]})
        return summarize(response, purpose_value(response))

    def run_answers(row: dict[str, Any]) -> dict[str, Any]:
        response = post(ANSWERS.endpoint, {"text": row["text"], "anomaly": row["anomaly"]})
        return summarize(response, answer_value(response))

    def run_profile(row: dict[str, Any]) -> dict[str, Any]:
        body = {"productCode": row["productCode"], "goal": row["text"], "okved": row["okved"]}
        return profile_summary(post(PROFILE.endpoint, body))

    return {PURPOSE.name: run_purpose, ANSWERS.name: run_answers, PROFILE.name: run_profile}


def evaluate(runner: dict[str, Callable[[dict[str, Any]], dict[str, Any]]], runs: int,
             sets: tuple[GoldenSet, ...] = SETS) -> list[SetResult]:
    results = [SetResult(golden, golden.rows()) for golden in sets]
    for _ in range(runs):
        for result in results:
            classify = runner[result.golden.name]
            result.runs.append({row["id"]: classify(row) for row in result.rows})
    return results


def percent(pair: tuple[int, int]) -> str:
    correct, total = pair
    return f"{100 * correct / total:.1f} % ({correct}/{total})" if total else "—"


def share(result: SetResult) -> str:
    who = Counter("недоступна" if answer["status"] == "UNAVAILABLE" else
                  "LLM" if answer["generatedBy"] == "LLM" else "словарь" for answer in result.answers.values())
    total = len(result.rows)
    return ", ".join(f"{name} {100 * who[name] / total:.0f} %" for name in ("словарь", "LLM", "недоступна")
                     if who[name])


def latency(seconds: list[float]) -> str:
    if not seconds:
        return "—"
    ordered = sorted(seconds)
    p95 = ordered[min(len(ordered) - 1, round(0.95 * (len(ordered) - 1)))]
    return f"медиана {statistics.median(ordered):.1f} с, p95 {p95:.1f} с, вызовов {len(ordered)}"


def report(results: list[SetResult], mode: str, runs: int, model: str, timing: list[float],
           replay_check: str, misses: int) -> str:
    lines = [
        f"Режим: **{mode}**, прогонов: {runs}, модель: `{model or '—'}`.",
        "",
        "| Набор | Строк | Точность | Без спорных | simple | hard | trap | Кто решил | Детерминизм |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for result in results:
        by_kind = [percent(result.accuracy([row for row in result.rows if row["kind"] == kind])) for kind in KINDS]
        unstable = result.unstable()
        stable = "100 %" if not unstable else f"расхождения: {', '.join(unstable)}"
        lines.append(f"| {result.golden.title} | {len(result.rows)} | {percent(result.accuracy())} | "
                     f"{percent(result.accuracy([row for row in result.rows if not row['disputed']]))} | "
                     f"{' | '.join(by_kind)} | {share(result)} | {stable if runs > 1 else '—'} |")
    lines += ["", f"Задержка вызова LLM: {latency(timing)}.", f"Сверка с записанными ответами: {replay_check}."]
    if misses:
        lines.append(f"Нет записанного ответа модели: {misses} — запишите заново (`--record`).")
    for result in results:
        errors = result.errors()
        lines += ["", f"**{result.golden.title} — ошибки ({len(errors)})**", ""]
        if not errors:
            lines.append("Нет.")
            continue
        confusion = Counter(f"{row['expected']} → {result.answers[row['id']]['value']}" for row in errors)
        lines.append("Матрица ошибок (ожидали → получили): "
                     + ", ".join(f"{pair}: {count}" for pair, count in confusion.most_common()) + ".")
        lines += ["", "| id | Вид | Текст | Ждём | Получили | Кто | Нарушение |", "|---|---|---|---|---|---|---|"]
        for row in errors:
            answer = result.answers[row["id"]]
            kind = row["kind"] + (", спорная" if row["disputed"] else "")
            lines.append(f"| {row['id']} | {kind} | {row['text']} | {row['expected']} | {answer['value']} | "
                         f"{answer['generatedBy']} | {answer['violation'] or '—'} |")
    return "\n".join(lines)


def write_report(path: Path, text: str) -> None:
    current = path.read_text(encoding="utf-8")
    start, end = current.index(REPORT_START) + len(REPORT_START), current.index(REPORT_END)
    path.write_text(current[:start] + "\n" + text + "\n" + current[end:], encoding="utf-8")


def compare_with_replay(recorded: RecordingLlm, path: Path) -> str:
    if not path.is_file():
        return "файла записи нет"
    stored = ReplayLlm(path).answers
    if not stored:
        return "записи ещё не было"
    same = sum(stored.get(key, {}).get("content") == entry["content"] for key, entry in recorded.entries.items())
    return f"{same} из {len(recorded.entries)} ответов модели совпали с записью"


def main() -> int:
    parser = argparse.ArgumentParser(description="Eval золотых наборов: цель кредита и ответы на аномалии (M26)")
    parser.add_argument("--mode", choices=["replay", "live", "http"], default="replay",
                        help="replay — записанные ответы модели без Ollama; live — Ollama напрямую; http — сервис ml")
    parser.add_argument("--ollama", default="http://ollama:11434", help="адрес Ollama для --mode live")
    parser.add_argument("--model", default="qwen3:4b")
    parser.add_argument("--url", default="http://localhost:8001", help="адрес сервиса ml для --mode http")
    parser.add_argument("--runs", type=int, default=2, help="сколько прогонов сравнить на детерминизм")
    parser.add_argument("--replay", type=Path, default=REPLAY_PATH, help="файл записанных ответов модели")
    parser.add_argument("--record", action="store_true", help="live: записать ответы модели в файл replay")
    parser.add_argument("--sets", nargs="+", choices=[golden.name for golden in SETS],
                        default=[golden.name for golden in SETS], help="какие наборы прогнать")
    parser.add_argument("--report", type=Path, help="заменить блок eval в этом файле (docs/ml-quality.md)")
    parser.add_argument("--check", action="store_true",
                        help="код выхода 1, если точность цели < 90 %, есть расхождения или нет записанных ответов")
    args = parser.parse_args()
    logging.getLogger("ml").setLevel(logging.ERROR)

    timed: TimedLlm | None = None
    recorder: RecordingLlm | None = None
    replay: ReplayLlm | None = None
    model = ""
    if args.mode == "replay":
        replay = ReplayLlm(args.replay)
        timed = TimedLlm(replay)
        runner, model = local_runner(timed), replay.model
    elif args.mode == "live":
        settings = LlmSettings(enabled=True, url=args.ollama.rstrip("/"), model=args.model, timeout_seconds=600)
        recorder = RecordingLlm(OllamaClient(settings))
        timed = TimedLlm(recorder)
        runner, model = local_runner(timed), args.model
    else:
        runner = http_runner(args.url.rstrip("/"))

    results = evaluate(runner, args.runs, tuple(golden for golden in SETS if golden.name in args.sets))
    replay_check = "—"
    if recorder is not None:
        replay_check = compare_with_replay(recorder, args.replay)
        if args.record:
            recorded = len(recorder.entries)
            if args.replay.is_file():
                recorder.entries = {**ReplayLlm(args.replay).answers, **recorder.entries}
            recorder.write(args.replay)
            replay_check += f"; записано {recorded} ответов, всего в {args.replay.name} — {len(recorder.entries)}"
    timing = timed.seconds if timed is not None and args.mode == "live" else []
    misses = len(set(replay.misses)) if replay is not None else 0
    text = report(results, args.mode, args.runs, model, timing, replay_check, misses)
    print(text)
    if args.report:
        write_report(args.report, text)

    purpose = next((result for result in results if result.golden is PURPOSE), None)
    correct, total = purpose.accuracy() if purpose is not None else (0, 0)
    failed = correct < PURPOSE_MIN_ACCURACY * total or any(result.unstable() for result in results) or misses
    return 1 if args.check and failed else 0


if __name__ == "__main__":
    sys.exit(main())
