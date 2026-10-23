from pathlib import Path

from fastapi import FastAPI

from scoring.engine import Engine
from scoring.schemas import ScoreRequest, ScoreResponse

RULES_PATH = Path(__file__).resolve().parent / "scoring_rules.yaml"

engine = Engine.from_yaml(RULES_PATH)

app = FastAPI(title="ML Scoring Service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/score", response_model=ScoreResponse, tags=["scoring"])
def score(request: ScoreRequest) -> ScoreResponse:
    return engine.score(request)


if __name__ == "__main__":
    import os

    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8001")))
