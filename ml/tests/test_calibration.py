from functools import cache

import numpy as np
import pytest
import yaml

from calibration.acceptance import check
from calibration.analyze import auc, decide, logistic_regression, score_population
from calibration.candidate import ORDERED_BINS, candidate_rules, fit
from calibration.generate import company, generate, serialize
from calibration.report import CANDIDATE_PATH, persona_requests
from main import RULES_PATH
from scoring.engine import Engine

CURRENT = yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))
ENGINE = Engine(CURRENT)


@cache
def population() -> tuple[dict, ...]:
    return tuple(generate(engine=ENGINE))


def test_generator_is_byte_deterministic() -> None:
    first = "".join(serialize(row) + "\n" for row in generate(200, engine=ENGINE))
    second = "".join(serialize(row) + "\n" for row in generate(200, engine=ENGINE))
    other_seed = "".join(serialize(row) + "\n" for row in generate(200, seed=1, engine=ENGINE))
    assert first == second
    assert first != other_seed


def test_row_does_not_depend_on_population_size() -> None:
    assert company(5, ENGINE) == generate(10, engine=ENGINE)[4] == generate(5, engine=ENGINE)[4]


def test_full_population_passes_acceptance() -> None:
    failed = [(name, detail) for name, ok, detail in check(list(population()), ENGINE) if not ok]
    assert failed == []


def test_true_fields_never_leak_into_score_request() -> None:
    row = population()[0]
    request = score_population([row], ENGINE)[0]
    assert request.row is row
    from calibration.acceptance import OBSERVED_FIELDS

    assert not any(field.startswith("true_") or field == "default_12m" for field in OBSERVED_FIELDS)


def test_auc_matches_pairwise_definition_with_ties() -> None:
    scores = np.array([0.1, 0.4, 0.4, 0.8, 0.3, 0.9, 0.4])
    labels = np.array([0, 0, 1, 1, 0, 1, 0])
    pairs = [(p, n) for p in scores[labels == 1] for n in scores[labels == 0]]
    expected = sum(1.0 if p > n else 0.5 if p == n else 0.0 for p, n in pairs) / len(pairs)
    assert auc(scores, labels) == pytest.approx(expected)


def test_logistic_regression_recovers_known_coefficients() -> None:
    rng = np.random.Generator(np.random.PCG64(7))
    features = rng.normal(size=(20_000, 2))
    probabilities = 1 / (1 + np.exp(-(-1.0 + 1.5 * features[:, 0] - 0.7 * features[:, 1])))
    labels = (rng.random(20_000) < probabilities).astype(float)
    assert logistic_regression(features, labels) == pytest.approx([-1.0, 1.5, -0.7], abs=0.08)


@cache
def candidate() -> dict:
    return candidate_rules(CURRENT, fit(list(population())))


def test_candidate_weights_are_monotone_in_risk() -> None:
    fitted = fit(list(population()))
    for factor, ordered in ORDERED_BINS.items():
        values = [fitted["values"][factor][label] for label in ordered]
        assert values == sorted(values), factor


def test_candidate_keeps_persona_outcomes() -> None:
    new_engine = Engine(candidate())
    for name, request in persona_requests().items():
        old_decision = decide(ENGINE, request, ENGINE.score(request))
        new_decision = decide(new_engine, request, new_engine.score(request))
        assert new_decision == old_decision, name


def test_candidate_ranks_risk_at_least_as_well() -> None:
    rows = list(population())
    labels = np.array([row["default_12m"] for row in rows])

    def observed_auc(engine: Engine) -> float:
        scored = score_population(rows, engine)
        return auc(np.array([(item.observed.rate_from + item.observed.rate_to) / 2 for item in scored]), labels)

    assert observed_auc(Engine(candidate())) >= observed_auc(ENGINE)


def test_committed_candidate_file_matches_fit() -> None:
    committed = yaml.safe_load(CANDIDATE_PATH.read_text(encoding="utf-8"))
    assert committed == candidate()
    assert committed["rules_version"] != CURRENT["rules_version"]
