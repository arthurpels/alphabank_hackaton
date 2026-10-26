import json
from pathlib import Path

import yaml

from main import RULES_PATH

TABLE_PATH = Path(__file__).resolve().parents[2] / "docs" / "decision-table.json"
TABLE = json.loads(TABLE_PATH.read_text(encoding="utf-8"))


def test_table_thresholds_match_rules() -> None:
    products = yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))["products"]
    assert TABLE["rulesVersion"] == yaml.safe_load(RULES_PATH.read_text(encoding="utf-8"))["rules_version"]
    for row in TABLE["rows"]:
        product = products[row["input"]["productCode"]]
        assert (row["input"]["approveMax"], row["input"]["rejectMin"]) == (product["approve_max"],
                                                                           product["reject_min"]), row["id"]
    assert all(product["approve_max"] < product["reject_min"] for product in products.values())
