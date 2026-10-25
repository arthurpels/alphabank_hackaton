import json
import sys
import time
import urllib.request
from pathlib import Path

SHOWCASE = Path(__file__).resolve().parent / "demo-ml" / "showcase.json"


def call(url: str, endpoint: str, body: dict) -> tuple[dict, float]:
    headers = {"Content-Type": "application/json"}
    request = urllib.request.Request(url + endpoint, json.dumps(body).encode("utf-8"), headers)
    started = time.time()
    with urllib.request.urlopen(request, timeout=300) as response:
        return json.load(response), time.time() - started


def main() -> int:
    url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8001"
    items = json.loads(SHOWCASE.read_text(encoding="utf-8"))["items"]
    with urllib.request.urlopen(url + "/health/llm", timeout=10) as response:
        health = json.load(response)
    print(f"LLM: {health['status']}, модель {health.get('model')}, запомнено ответов: {health.get('cachedVerdicts')}")
    failed = 0
    print("| Пример | Ждём | Получили | Кто решил | Из кеша | Время |")
    print("|---|---|---|---|---|---|")
    for item in items:
        answer, elapsed = call(url, item["endpoint"], item["body"])
        got = {key: answer.get(key) for key in item["expect"]}
        ok = got == item["expect"]
        failed += not ok
        llm = answer.get("llm") or {}
        text = item["body"].get("goal") or item["body"].get("text")
        expected = "/".join(str(value) for value in item["expect"].values() if value is not None)
        received = "/".join(str(value) for value in got.values() if value is not None)
        print(
            f"| {'✓' if ok else '✗'} {item['id']} «{text}» | {expected} | {received} | {answer.get('generatedBy')} | "
            f"{'да' if llm.get('cached') else '—'} | {elapsed:.2f} с |"
        )
    print(f"\nВитрина: {len(items) - failed} из {len(items)} как ожидалось.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
