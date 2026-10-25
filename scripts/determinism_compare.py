import json
import re
import sys
from collections import defaultdict
from pathlib import Path

UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
TIMESTAMP = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}")
SCENARIOS = ("TS-01", "TS-02", "TS-03", "TS-04", "TS-05", "TS-06")


def volatile_key(key: str) -> bool:
    return key == "id" or key.endswith("Id") or key.endswith("At") or key.endswith("Deadline")


def normalize(value: object) -> object:
    if isinstance(value, dict):
        return {key: normalize(item) for key, item in value.items() if not volatile_key(key)}
    if isinstance(value, list):
        return [normalize(item) for item in value]
    if isinstance(value, str):
        return "<время>" if TIMESTAMP.match(value) else UUID.sub("<id>", value)
    return value


def folders(collection: dict) -> dict[str, str]:
    owner = {}
    for folder in collection["item"]:
        for item in folder.get("item", []):
            owner[item["id"]] = folder["name"]
    return owner


def responses(path: Path) -> tuple[dict[tuple[str, str, int], tuple[int, object]], int]:
    report = json.loads(path.read_text(encoding="utf-8"))["run"]
    owner = folders(json.loads(path.read_text(encoding="utf-8"))["collection"])
    seen: dict[tuple[str, str], int] = defaultdict(int)
    result = {}
    for execution in report["executions"]:
        folder = owner.get(execution["item"]["id"], "?")
        name = execution["item"]["name"]
        occurrence = seen[(folder, name)]
        seen[(folder, name)] += 1
        response = execution.get("response")
        if response is None:
            result[(folder, name, occurrence)] = (0, None)
            continue
        text = bytes(response["stream"]["data"]).decode("utf-8")
        try:
            body = normalize(json.loads(text))
        except json.JSONDecodeError:
            body = text
        result[(folder, name, occurrence)] = (response["code"], body)
    assertions = [assertion for execution in report["executions"] for assertion in execution.get("assertions", [])]
    failures = sum(1 for assertion in assertions if assertion.get("error"))
    return result, failures


def differences(left: object, right: object, path: str = "") -> list[str]:
    if isinstance(left, dict) and isinstance(right, dict):
        found = []
        for key in sorted(set(left) | set(right)):
            if key not in left or key not in right:
                found.append(f"{path}/{key}: есть только в одном прогоне")
            else:
                found += differences(left[key], right[key], f"{path}/{key}")
        return found
    if isinstance(left, list) and isinstance(right, list):
        if len(left) != len(right):
            return [f"{path}: длина {len(left)} ≠ {len(right)}"]
        return [item for index, (a, b) in enumerate(zip(left, right)) for item in differences(a, b, f"{path}[{index}]")]
    if left == right:
        return []
    return [f"{path}: {json.dumps(left, ensure_ascii=False)[:80]} ≠ {json.dumps(right, ensure_ascii=False)[:80]}"]


def summary(run: dict[tuple[str, str, int], tuple[int, object]]) -> list[list[str]]:
    rows = []
    for code in SCENARIOS:
        folder = next((key[0] for key in run if key[0].startswith(code)), None)
        if folder is None:
            continue
        card = run.get((folder, "Детерминизм · карточка", 0), (0, {}))[1] or {}
        history = run.get((folder, "Детерминизм · история вилки", 0), (0, []))[1] or []
        verify = run.get((folder, "Детерминизм · verify", 0), (0, {}))[1] or {}
        decision = card.get("decision") or {}
        path = " → ".join(f"{snap['rateFrom']:g}–{snap['rateTo']:g}" for snap in history if isinstance(snap, dict))
        rows.append(
            [
                folder,
                (card.get("application") or {}).get("status", "—"),
                decision.get("type", "—"),
                path or "—",
                (decision.get("requestHash") or verify.get("requestHash") or "—")[:12],
                "да" if verify.get("reproducible") else "нет",
            ]
        )
    return rows


def main() -> int:
    first, first_failures = responses(Path(sys.argv[1]))
    second, second_failures = responses(Path(sys.argv[2]))
    mismatches = []
    for key in sorted(set(first) | set(second)):
        if key not in first or key not in second:
            mismatches.append((key, ["запрос есть только в одном прогоне"]))
            continue
        (code_a, body_a), (code_b, body_b) = first[key], second[key]
        found = [f"HTTP {code_a} ≠ {code_b}"] if code_a != code_b else []
        found += differences(body_a, body_b)
        if found:
            mismatches.append((key, found))

    print("| Сценарий | Статус | Решение | Путь вилки | Хеш входа скоринга | verify |")
    print("|---|---|---|---|---|---|")
    for row in summary(first):
        print("| " + " | ".join(row) + " |")
    print()
    compared = len(set(first) & set(second))
    print(f"Сравнено запросов: {compared}. Упавших проверок коллекции: {first_failures} и {second_failures}.")
    if not mismatches:
        print("Расхождений между прогонами нет: одинаковые действия дают одинаковый результат.")
        return 1 if first_failures or second_failures else 0
    print(f"РАСХОЖДЕНИЯ в {len(mismatches)} запросах:")
    for (folder, name, occurrence), found in mismatches:
        print(f"  {folder} / {name}{f' #{occurrence + 1}' if occurrence else ''}")
        for line in found[:5]:
            print(f"    {line}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
