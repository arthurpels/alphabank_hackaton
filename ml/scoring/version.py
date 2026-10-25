import hashlib
from pathlib import Path

ENGINE_FILES = ("scoring/engine.py", "scoring/conditions.py", "scoring/schemas.py", "scoring_rules.yaml")
VERSION_LENGTH = 12


def engine_version(base_dir: Path) -> str:
    digest = hashlib.sha256()
    for name in ENGINE_FILES:
        digest.update(name.encode("utf-8"))
        digest.update(b"\0")
        digest.update((base_dir / name).read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()[:VERSION_LENGTH]
