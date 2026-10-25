import argparse
import json
import time
import urllib.request
from collections import Counter
from pathlib import Path

GOLDEN = Path(__file__).resolve().parent / "data" / "anomaly_answers_golden.jsonl"


def classify(url: str, row: dict) -> dict:
    body = json.dumps({"text": row["text"], "anomaly": row["anomaly"]}).encode("utf-8")
    request = urllib.request.Request(f"{url}/answers/classify", body, {"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=600) as response:
        return json.load(response)


def comparable(run: dict[str, dict]) -> str:
    stripped = {
        key: {**answer, "llm": answer["llm"] and {**answer["llm"], "cached": None}} for key, answer in run.items()
    }
    return json.dumps(stripped, sort_keys=True, ensure_ascii=False)


def accuracy(rows: list[dict], answers: dict[str, dict]) -> tuple[int, int]:
    correct = sum(answers[row["id"]]["code"] == row["expected"] for row in rows)
    return correct, len(rows)


def percent(pair: tuple[int, int]) -> str:
    correct, total = pair
    return f"{correct}/{total} = {100 * correct / total:.1f} %" if total else "—"


def main() -> None:
    parser = argparse.ArgumentParser(description="Прогон золотого набора ответов на аномалии через /answers/classify")
    parser.add_argument("--url", default="http://localhost:8001")
    parser.add_argument("--runs", type=int, default=2, help="сколько прогонов сравнить на детерминизм")
    args = parser.parse_args()

    rows = [json.loads(line) for line in GOLDEN.read_text(encoding="utf-8").splitlines() if line.strip()]
    runs, started = [], time.time()
    for _ in range(args.runs):
        runs.append({row["id"]: classify(args.url, row) for row in rows})
    answers = runs[0]

    print(f"Строк: {len(rows)}, прогонов: {args.runs}, время: {time.time() - started:.0f} с")
    print(f"Точность: {percent(accuracy(rows, answers))}")
    print(f"Без спорных: {percent(accuracy([r for r in rows if not r['disputed']], answers))}")
    for kind in ("simple", "hard", "trap"):
        print(f"  {kind}: {percent(accuracy([r for r in rows if r['kind'] == kind], answers))}")
    print("Кто решал:", dict(Counter(f"{a['generatedBy']}/{a['status']}" for a in answers.values())))
    same = all(comparable(run) == comparable(answers) for run in runs[1:])
    print("Детерминизм между прогонами:", "100 %" if same else "РАСХОЖДЕНИЯ")
    errors = [r for r in rows if answers[r["id"]]["code"] != r["expected"]]
    confusion = Counter(f"{row['expected']}→{answers[row['id']]['code']}" for row in errors)
    print("Матрица ошибок (ожидали → получили):", dict(confusion))
    for row in errors:
        answer = answers[row["id"]]
        print(
            f"  {row['id']} {row['kind']}{' спорн' if row['disputed'] else ''}: {row['expected']} → {answer['code']}"
            f" ({answer['generatedBy']}, {answer['violation']}) «{row['text']}»"
        )


if __name__ == "__main__":
    main()
