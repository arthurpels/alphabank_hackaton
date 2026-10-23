#!/usr/bin/env python3
"""Создаёт метки, вехи и задачи на GitHub из docs/kanban.md через gh CLI."""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

KANBAN = Path(__file__).resolve().parent.parent / "docs" / "kanban.md"

ROLES = {"Лид": "lead", "Б1": "b1", "Б2": "b2", "Ф": "front", "А": "analyst"}
ROLE_COLOR = "5319e7"
PRIORITY_COLORS = {"P0": "b60205", "P1": "fbca04", "P2": "c5def5"}
AREA_COLOR = "0e8a16"

HEADER = re.compile(r"^### (T\d+) · (.+)$")
META = re.compile(
    r"^\*\*Кто:\*\* (.+?) · \*\*Метки:\*\* (.+?) · "
    r"\*\*Веха:\*\* (.+?) · \*\*Зависит от:\*\* (.+)$"
)
TASK_REF = re.compile(r"\bT\d{2}\b")


def parse(text):
    tasks, current = [], None
    for line in text.splitlines():
        if m := HEADER.match(line):
            current = {"id": m[1], "title": m[2], "body": []}
            tasks.append(current)
        elif line.startswith("## ") or line == "---":
            current = None
        elif current is not None:
            if m := META.match(line):
                current.update(
                    role=m[1],
                    labels=[l.strip() for l in m[2].split(",")],
                    milestone=m[3].strip(),
                    deps=m[4].strip(),
                )
            else:
                current["body"].append(line)
    for t in tasks:
        if "role" not in t:
            sys.exit(f"{t['id']}: нет строки с метаданными")
        t["body"] = "\n".join(t["body"]).strip()
    return tasks


def render_body(task, numbers):
    body = f"**Роль:** {task['role']} · **Зависит от:** {task['deps']}\n\n{task['body']}"
    if numbers:
        body = TASK_REF.sub(lambda m: f"#{numbers[m[0]]}" if m[0] in numbers else m[0], body)
    return f"{body}\n\n_Источник: `docs/kanban.md`, {task['id']}_"


def gh(*args, dry_run):
    if dry_run:
        print("gh", " ".join(args[:4]), "…")
        return ""
    res = subprocess.run(["gh", *args], capture_output=True, text=True)
    if res.returncode != 0:
        sys.exit(f"gh {' '.join(args[:3])}: {res.stderr.strip()}")
    return res.stdout.strip()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true", help="только показать, ничего не создавать")
    ap.add_argument("--assign", default="", help="роль=логин через запятую: lead=…,b1=…,b2=…,front=…,analyst=…")
    ap.add_argument("--project", help="название GitHub Project (доски), куда добавить задачи")
    ap.add_argument("--only", help="создать только эти задачи: T01,T02")
    args = ap.parse_args()

    tasks = parse(KANBAN.read_text(encoding="utf-8"))
    if args.only:
        wanted = set(args.only.split(","))
        tasks = [t for t in tasks if t["id"] in wanted]
    assignees = dict(pair.split("=", 1) for pair in args.assign.split(",") if pair)
    dry = args.dry_run

    labels = {f"роль:{r}": ROLE_COLOR for r in ROLES}
    for t in tasks:
        for l in t["labels"]:
            labels.setdefault(l, PRIORITY_COLORS.get(l, AREA_COLOR))
    for name, color in labels.items():
        gh("label", "create", name, "--color", color, "--force", dry_run=dry)

    existing = set()
    if not dry:
        out = gh("api", "repos/{owner}/{repo}/milestones?state=all&per_page=100", dry_run=dry)
        existing = {m["title"] for m in json.loads(out)}
    for title in dict.fromkeys(t["milestone"] for t in tasks):
        if title not in existing:
            gh("api", "repos/{owner}/{repo}/milestones", "-f", f"title={title}", dry_run=dry)

    numbers = {}
    for t in tasks:
        cmd = [
            "issue", "create",
            "--title", f"[{t['role']}] {t['id']} · {t['title']}",
            "--body", render_body(t, {}),
            "--milestone", t["milestone"],
            "--label", ",".join([f"роль:{t['role']}", *t["labels"]]),
        ]
        if login := assignees.get(ROLES[t["role"]]):
            cmd += ["--assignee", login]
        if args.project:
            cmd += ["--project", args.project]
        url = gh(*cmd, dry_run=dry)
        if url:
            numbers[t["id"]] = url.rsplit("/", 1)[-1]
            print(f"{t['id']} → {url}")

    # второй проход: Tnn в описаниях → ссылки #N на созданные задачи
    for t in tasks:
        if t["id"] in numbers and TASK_REF.search(t["deps"] + t["body"]):
            gh("issue", "edit", numbers[t["id"]], "--body", render_body(t, numbers), dry_run=dry)

    print(f"Готово: {len(tasks)} задач, {len(labels)} меток.")


if __name__ == "__main__":
    main()
