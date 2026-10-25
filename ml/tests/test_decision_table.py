import json
from pathlib import Path

import pytest
import yaml

from main import RULES_PATH
from scoring_calc import decision

TABLE_PATH = Path(__file__).resolve().parents[2] / "docs" / "decision-table.json"
TABLE = json.loads(TABLE_PATH.read_text(encoding="utf-8"))


@pytest.mark.parametrize("row", TABLE["rows"], ids=[row["id"] for row in TABLE["rows"]])
def test_decision_follows_table(row) -> None:
    given, expected = row["input"], row["expected"]

    status, reason_spot, rule = decision(given["rateFrom"], given["rateTo"], given["approveMax"],
                                         given["policyOk"], given["gridOffers"], given["spots"])

    assert (status, reason_spot, rule) == (expected["status"], expected["reasonSpotId"], row["rule"])


def test_table_thresholds_match_rules() -> None:
    products = yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))["products"]
    assert TABLE["rulesVersion"] == yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))["rules_version"]
    for row in TABLE["rows"]:
        product = products[row["input"]["productCode"]]
        assert (row["input"]["approveMax"], row["input"]["rejectMin"]) == (product["approve_max"],
                                                                           product["reject_min"]), row["id"]
    assert all(product["approve_max"] < product["reject_min"] for product in products.values())


def test_rejection_without_spots_is_impossible() -> None:
    with pytest.raises(ValueError):
        decision(25.0, 26.0, 20.0, True, 0, [])
