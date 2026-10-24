import logging
import threading
from typing import Literal

from scoring.llm import OllamaClient
from scoring.schemas import LlmHealth
from scoring.verdicts import VerdictCache

logger = logging.getLogger("ml.warmup")

LlmStatus = Literal["disabled", "warming", "ready", "unavailable"]

RETRY_SECONDS = 10.0


class LlmWarmup:
    def __init__(self, client: OllamaClient | None, cache: VerdictCache | None) -> None:
        self.client = client
        self.cache = cache
        self.status: LlmStatus = "disabled" if client is None else "warming"
        self.digest: str | None = None
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def step(self) -> LlmStatus:
        if self.client is None:
            return self.status
        digest = self.client.model_digest()
        if digest is None:
            self.status = "unavailable"
            return self.status
        self.digest = digest
        self.status = "warming"
        self.status = "ready" if self.client.warm_up() else "unavailable"
        if self.status == "ready":
            logger.info("Модель %s загружена в память, digest %s", self.client.settings.model, digest)
        return self.status

    def start(self) -> None:
        if self.client is None or self._thread is not None:
            return
        self._thread = threading.Thread(target=self._run, name="llm-warmup", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()

    def _run(self) -> None:
        while not self._stop.is_set() and self.step() != "ready":
            self._stop.wait(RETRY_SECONDS)

    def health(self) -> LlmHealth:
        return LlmHealth(
            status=self.status,
            model=None if self.client is None else self.client.settings.model,
            digest=self.digest,
            cached_verdicts=None if self.cache is None else self.cache.count(),
        )
