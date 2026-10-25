from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI

from scoring.engine import Engine
from scoring.llm import LlmClient, LlmSettings, OllamaClient, load_prompts
from scoring.purpose import PurposeClassifier
from scoring.schemas import (
    AnomaliesRequest,
    AnomaliesResponse,
    LlmHealth,
    OfferGridResponse,
    PurposeRequest,
    PurposeResponse,
    ScoreRequest,
    ScoreResponse,
    SummaryRequest,
    UnderwritingSummary,
)
from scoring.summary import Summarizer
from scoring.verdicts import CachedLlm, open_cache
from scoring.version import engine_version
from scoring.warmup import LlmWarmup

BASE_DIR = Path(__file__).resolve().parent
RULES_PATH = BASE_DIR / "scoring_rules.yaml"
PROMPTS_PATH = BASE_DIR / "llm.yaml"
ENGINE_VERSION = engine_version(BASE_DIR)

engine = Engine.from_yaml(RULES_PATH)
llm_settings = LlmSettings.from_env()
ollama = OllamaClient(llm_settings) if llm_settings.enabled else None
verdict_cache = open_cache(llm_settings.cache_path) if ollama is not None else None
llm: LlmClient | None = CachedLlm(ollama, verdict_cache) if ollama and verdict_cache else ollama
warmup = LlmWarmup(ollama, verdict_cache)
prompts = load_prompts(PROMPTS_PATH)
purpose_classifier = PurposeClassifier(engine.purpose_keywords, llm, prompts)
summarizer = Summarizer(llm if llm_settings.summary_enabled else None, prompts)


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    warmup.start()
    yield
    warmup.stop()


app = FastAPI(title="ML Scoring Service", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "engineVersion": ENGINE_VERSION, "rulesVersion": engine.rules_version}


@app.get("/health/llm", response_model=LlmHealth, tags=["llm"])
def llm_health() -> LlmHealth:
    return warmup.health()


@app.post("/score", response_model=ScoreResponse, tags=["scoring"])
def score(request: ScoreRequest) -> ScoreResponse:
    return engine.score(request)


@app.post("/offers/grid", response_model=OfferGridResponse, tags=["scoring"])
def offer_grid(request: ScoreRequest) -> OfferGridResponse:
    return engine.offer_grid(request)


@app.post("/anomalies", response_model=AnomaliesResponse, tags=["scoring"])
def detect_anomalies(request: AnomaliesRequest) -> AnomaliesResponse:
    return engine.detect_anomalies(request)


@app.post("/purpose", response_model=PurposeResponse, tags=["llm"])
def classify_purpose(request: PurposeRequest) -> PurposeResponse:
    return purpose_classifier.classify(request)


@app.post("/summary", response_model=UnderwritingSummary, tags=["llm"])
def summarize(request: SummaryRequest) -> UnderwritingSummary:
    return summarizer.summarize(request)


if __name__ == "__main__":
    import os

    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8001")))
