import copy
from pathlib import Path

import yaml
from fastapi.testclient import TestClient

from main import app

CONTRACT_PATH = Path(__file__).resolve().parents[2] / "docs" / "openapi-ml.yaml"

client = TestClient(app)


def load_examples(endpoint: str) -> dict[str, tuple[dict, dict]]:
    contract = yaml.safe_load(CONTRACT_PATH.read_text(encoding="utf-8"))
    operation = contract["paths"][endpoint]["post"]
    requests = operation["requestBody"]["content"]["application/json"]["examples"]
    responses = operation["responses"]["200"]["content"]["application/json"]["examples"]
    return {name: (example["value"], responses[name]["value"]) for name, example in requests.items()}


SCORE_EXAMPLES = load_examples("/score")
GRID_EXAMPLES = load_examples("/offers/grid")
ANOMALY_EXAMPLES = load_examples("/anomalies")
PURPOSE_EXAMPLES = load_examples("/purpose")
SUMMARY_EXAMPLES = load_examples("/summary")
CLASSIFY_EXAMPLES = load_examples("/answers/classify")
VERIFY_EXAMPLES = load_examples("/answers/verify")


def post(endpoint: str, payload: dict) -> dict:
    response = client.post(endpoint, json=payload)
    assert response.status_code == 200, response.text
    return response.json()


def persona_request(name: str, **changes) -> dict:
    payload = copy.deepcopy(SCORE_EXAMPLES[name][0])
    payload.update(changes)
    return payload
