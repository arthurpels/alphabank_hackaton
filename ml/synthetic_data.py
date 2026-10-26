"""Синтетические данные персонажей — единый источник для synthetic-data.md.

python3 synthetic_data.py render  → печатает JSON-блоки для документа
python3 synthetic_data.py check   → считает производные (выручка, аномалии) и вилки
"""

import json
import re
import sys
from datetime import date
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path

from scoring.engine import Engine, months_between
from scoring.schemas import AnomaliesRequest, ScoreRequest, Series, SeriesPoint, SourceCode

AS_OF = date(2026, 10, 23)
ENGINE = Engine.from_yaml(Path(__file__).with_name("scoring_rules.yaml"))

M12 = ["2025-10", "2025-11", "2025-12", "2026-01", "2026-02", "2026-03",
       "2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09"]
M6 = M12[6:]

# --- ответы моков ---------------------------------------------------------------
coffee_ofd = [1410000, 1380000, 1560000, 1450000, 1520000, 830000,
              1490000, 1470000, 1430000, 1400000, 1460000, 1480000]
fns_acq = [1150000, 1190000, 1260000, 1180000, 1210000, 1230000,
           1170000, 1220000, 1190000, 1240000, 1180000, 1180000]
wb_payout = [360000, 380000, 375000, 390000, 465000, 430000]
wb_returns = [11, 12, 11, 13, 12, 14]
shell_acq = [120000, 110000, 130000, 125000, 118000, 640000]

P = {
    "coffee": {
        "name": "ООО «Кофейня»", "inn": "7701234560",
        "app": {"product": "EQUIPMENT", "amount": 2000000, "term": 36,
                "goal": "2 кофемашины и витрина для второй точки"},
        "egrul": {"source": "EGRUL", "inn": "7701234560", "name": "ООО «Кофейня»", "legal_form": "OOO",
                  "status": "ACTIVE", "registered_at": "2019-04-15",
                  "director": {"fio": "Смирнова А. В.", "appointed_at": "2019-04-15", "is_mass": False},
                  "address": {"value": "г. Москва, ул. Покровка, 12", "is_mass": False},
                  "okved_main": "56.30", "founders_count": 2},
        "bki": {"source": "BKI", "inn": "7701234560", "credit_score": 712,
                "open_credits": [{"type": "OVERDRAFT", "bank": "АО «Банк»", "limit": 300000, "debt": 0,
                                  "opened_at": "2024-03-11"}],
                "closed_credits": 3, "delinquencies": {"current": 0, "max_days_ever": 5, "last_12m": 0},
                "inquiries_6m": 1},
        "fns": {"source": "FNS", "inn": "7701234560",
                "tax_debt": {"amount": 0, "currency": "RUB", "as_of": "2026-10-15"},
                "unfiled_reports": 0, "tax_system": "USN"},
        "ofd": {"source": "OFD", "inn": "7701234560",
                "months": [{"month": m, "revenue": v} for m, v in zip(M12, coffee_ofd, strict=True)],
                "share_of_total_revenue": 0.7},
        "acquiring": {"source": "ACQUIRING", "inn": "7701234560",
                      "months": [{"month": m, "turnover": int(round(v * 0.67, -4)), "tx_count": round(v * 0.67 / 233)}
                                 for m, v in zip(M12, coffee_ofd, strict=True)],
                      "avg_ticket": 233},
    },
    "builder": {
        "name": "ИП Петров С. Н. (строитель)", "inn": "590234567822",
        "app": {"product": "WORKING_CAPITAL", "amount": 3000000, "term": 12,
                "goal": "закупка материалов под договор подряда на склад"},
        "egrul": {"source": "EGRUL", "inn": "590234567822", "name": "ИП Петров Сергей Николаевич",
                  "legal_form": "IP", "status": "ACTIVE", "registered_at": "2024-03-01",
                  "director": {"fio": "Петров С. Н.", "appointed_at": "2024-03-01", "is_mass": False},
                  "address": {"value": "г. Пермь, ул. Ленина, 40", "is_mass": False},
                  "okved_main": "41.20", "founders_count": 1},
        "bki": {"source": "BKI", "inn": "590234567822", "credit_score": 655,
                "open_credits": [{"type": "LOAN", "bank": "ПАО «Банк»", "limit": 400000, "debt": 150000,
                                  "opened_at": "2025-02-20"}],
                "closed_credits": 1, "delinquencies": {"current": 0, "max_days_ever": 12, "last_12m": 0},
                "inquiries_6m": 2},
        "fns": {"source": "FNS", "inn": "590234567822",
                "tax_debt": {"amount": 0, "currency": "RUB", "as_of": "2026-10-15"},
                "unfiled_reports": 0, "tax_system": "USN"},
        "document": {"kind": "CONTRACT_GP", "title": "Договор подряда с ООО «СтройГрупп»",
                     "amount": 9600000, "months": 12, "signed_at": "2026-09-01"},
    },
    "wb": {
        "name": "ИП Кузнецова М. И. (продавец на WB)", "inn": "590987654351",
        "app": {"product": "WORKING_CAPITAL", "amount": 1500000, "term": 12,
                "goal": "закупка товара к сезону распродаж"},
        "egrul": {"source": "EGRUL", "inn": "590987654351", "name": "ИП Кузнецова Мария Игоревна",
                  "legal_form": "IP", "status": "ACTIVE", "registered_at": "2024-05-20",
                  "director": {"fio": "Кузнецова М. И.", "appointed_at": "2024-05-20", "is_mass": False},
                  "address": {"value": "г. Пермь, ул. Сибирская, 15", "is_mass": False},
                  "okved_main": "47.91", "founders_count": 1},
        "bki": {"source": "BKI", "inn": "590987654351", "credit_score": 680,
                "open_credits": [{"type": "CREDIT_CARD", "bank": "АО «Банк»", "limit": 150000, "debt": 40000,
                                  "opened_at": "2024-11-05"}],
                "closed_credits": 0, "delinquencies": {"current": 0, "max_days_ever": 0, "last_12m": 0},
                "inquiries_6m": 2},
        "fns": {"source": "FNS", "inn": "590987654351",
                "tax_debt": {"amount": 0, "currency": "RUB", "as_of": "2026-10-15"},
                "unfiled_reports": 0, "tax_system": "USN"},
        "marketplace": {"source": "MARKETPLACE", "inn": "590987654351",
                        "months": [{"month": m, "gmv": int(round(p / 0.75, -4)), "payout": p, "returns_pct": r}
                                   for m, p, r in zip(M6, wb_payout, wb_returns, strict=True)],
                        "rating": 4.6, "store_age_months": 14},
    },
    "fns": {
        "name": "ООО «Техснаб» (долг в ФНС)", "inn": "7725555556",
        "app": {"product": "WORKING_CAPITAL", "amount": 800000, "term": 12,
                "goal": "закупка запчастей для перепродажи"},
        "egrul": {"source": "EGRUL", "inn": "7725555556", "name": "ООО «Техснаб»", "legal_form": "OOO",
                  "status": "ACTIVE", "registered_at": "2021-02-10",
                  "director": {"fio": "Орлов Д. А.", "appointed_at": "2021-02-10", "is_mass": False},
                  "address": {"value": "г. Москва, Варшавское ш., 33", "is_mass": False},
                  "okved_main": "46.69", "founders_count": 1},
        "bki": {"source": "BKI", "inn": "7725555556", "credit_score": 730, "open_credits": [],
                "closed_credits": 4, "delinquencies": {"current": 0, "max_days_ever": 3, "last_12m": 0},
                "inquiries_6m": 0},
        "fns": {"source": "FNS", "inn": "7725555556",
                "tax_debt": {"amount": 3412, "currency": "RUB", "as_of": "2026-10-15"},
                "unfiled_reports": 0, "tax_system": "OSN"},
        "acquiring": {"source": "ACQUIRING", "inn": "7725555556",
                      "months": [{"month": m, "turnover": v, "tx_count": round(v / 4800)}
                                 for m, v in zip(M12, fns_acq, strict=True)],
                      "avg_ticket": 4800},
    },
    "shell": {
        "name": "ООО «Однодневка»", "inn": "7719999994",
        "app": {"product": "WORKING_CAPITAL", "amount": 2000000, "term": 12, "goal": "пополнение оборотных средств"},
        "egrul": {"source": "EGRUL", "inn": "7719999994", "name": "ООО «Однодневка»", "legal_form": "OOO",
                  "status": "ACTIVE", "registered_at": "2025-08-01",
                  "director": {"fio": "Иванов И. И.", "appointed_at": "2026-09-15", "is_mass": False},
                  "address": {"value": "г. Москва, ул. Примерная, 1", "is_mass": True},
                  "okved_main": "46.90", "founders_count": 1},
        "bki": {"source": "BKI", "inn": "7719999994", "credit_score": 600, "open_credits": [],
                "closed_credits": 0, "delinquencies": {"current": 0, "max_days_ever": 0, "last_12m": 0},
                "inquiries_6m": 3},
        "fns": {"source": "FNS", "inn": "7719999994",
                "tax_debt": {"amount": 0, "currency": "RUB", "as_of": "2026-10-15"},
                "unfiled_reports": 0, "tax_system": "OSN"},
        "acquiring": {"source": "ACQUIRING", "inn": "7719999994",
                      "months": [{"month": m, "turnover": v, "tx_count": round(v / 1500)}
                                 for m, v in zip(M6, shell_acq, strict=True)],
                      "avg_ticket": 1500},
    },
}


# --- производные: из ответов моков в вход скоринга -------------------------------
def money(value: float) -> int:
    return int((Decimal(str(value)) * 100).quantize(Decimal(1), rounding=ROUND_HALF_UP))


def anomalies(values: list[int], months: list[str], source: SourceCode) -> list[str]:
    request = AnomaliesRequest(series=[Series(
        source=source,
        points=[SeriesPoint(month=month, value=money(value)) for month, value in zip(months, values, strict=True)],
    )])
    return [anomaly.title for anomaly in ENGINE.detect_anomalies(request).anomalies]


def base_input(p):
    e, b, f = p["egrul"], p["bki"], p["fns"]
    return dict(egrul_status=e["status"], registered_at=date.fromisoformat(e["registered_at"]),
                director_since=date.fromisoformat(e["director"]["appointed_at"]),
                mass_address=e["address"]["is_mass"], credit_score=b["credit_score"],
                delinq_12m=b["delinquencies"]["last_12m"], bki_current_overdue=b["delinquencies"]["current"],
                tax_debt=f["tax_debt"]["amount"], unfiled_reports=f["unfiled_reports"])


def score_request(persona: dict, updates: dict) -> ScoreRequest:
    application = persona["app"]
    values = {**base_input(persona), **updates}
    monthly_revenue = values.get("monthly_revenue")
    return ScoreRequest(
        as_of=AS_OF,
        product_code=application["product"],
        amount=money(application["amount"]),
        term_months=application["term"],
        egrul_status=values["egrul_status"],
        registered_at=values["registered_at"],
        director_since=values["director_since"],
        mass_address=values["mass_address"],
        credit_score=values["credit_score"],
        delinq_12m=values["delinq_12m"],
        bki_current_overdue=values["bki_current_overdue"],
        tax_debt=money(values["tax_debt"]),
        unfiled_reports=values["unfiled_reports"],
        monthly_revenue=None if monthly_revenue is None else money(monthly_revenue),
        stability=None if values.get("stability") is None else values["stability"].upper(),
        purpose=None if values.get("purpose") is None else values["purpose"].upper(),
        collateral=None if values.get("collateral") is None else values["collateral"].upper(),
    )


def avg(xs):
    return sum(xs) / len(xs)


SCENARIOS = {
    "coffee": [("ИНН", {}), ("ОФД", {"monthly_revenue": avg(coffee_ofd), "stability": "anomaly_open"}),
               ("март принят", {"monthly_revenue": avg(coffee_ofd), "stability": "explained"}),
               ("цель", {"monthly_revenue": avg(coffee_ofd), "stability": "explained", "purpose": "match"})],
    "builder": [("ИНН", {}), ("цель", {"purpose": "match"}),
                ("договор принят", {"purpose": "match", "stability": "document",
                                    "monthly_revenue": P["builder"]["document"]["amount"] / 12})],
    "wb": [("ИНН", {}), ("маркетплейс", {"monthly_revenue": avg(wb_payout), "stability": "clean"}),
           ("цель", {"monthly_revenue": avg(wb_payout), "stability": "clean", "purpose": "match"})],
    "fns": [("ИНН", {}),
            ("эквайринг+цель", {"monthly_revenue": avg(fns_acq), "stability": "clean", "purpose": "match"}),
            ("погасил", {"monthly_revenue": avg(fns_acq), "stability": "clean", "purpose": "match", "tax_debt": 0})],
    "shell": [("ИНН", {})],
}


def check():
    print("Производные:")
    for k, values, months, source in [
        ("coffee OFD", coffee_ofd, M12, SourceCode.OFD),
        ("fns acquiring", fns_acq, M12, SourceCode.ACQUIRING),
        ("wb payout", wb_payout, M6, SourceCode.MARKETPLACE),
        ("shell acquiring", shell_acq, M6, SourceCode.ACQUIRING),
    ]:
        print(f"  {k}: среднее {avg(values):,.0f}, аномалии: {anomalies(values, months, source) or 'нет'}")
    print(f"  wb returns max {max(wb_returns)} % (порог 20 %)")
    for k in P:
        e_ = P[k]["egrul"]
        print(f"  {k}: возраст {months_between(date.fromisoformat(e_['registered_at']), AS_OF)} мес, "
              f"директор {months_between(date.fromisoformat(e_['director']['appointed_at']), AS_OF)} мес")

    print("\nВилки:")
    for k, steps in SCENARIOS.items():
        for label, upd in steps:
            result = ENGINE.score(score_request(P[k], upd))
            metric = result.policy.value
            m = f" нагрузка {metric * 100:.1f}%" if metric is not None else ""
            spots = [(factor.code, factor.spot_price_pp) for factor in result.factors
                     if factor.applicable and not factor.known]
            stops = [stop.code for stop in result.stop_factors]
            print(f"  {k} · {label}: {result.rate_from}–{result.rate_to}{m} · пятна {spots} · стоп {stops}")
        grid = ENGINE.offer_grid(score_request(P[k], steps[-1][1]))
        offers = [(offer.product_code.value, offer.amount // 100, offer.term_months, offer.collateral_type,
                   offer.rate_from, offer.rate_to, offer.policy.value) for offer in grid.offers]
        print(f"  {k} → passes={result.passes}, сетка {offers}")
        if k == "builder":
            submitted = ENGINE.score(score_request(P[k], steps[1][1]))
            print(f"   при подаче: passes={submitted.passes}")
        if k == "fns":
            unpaid = ENGINE.score(score_request(P[k], steps[1][1]))
            print(f"   без погашения: passes={unpaid.passes}, стоп {[stop.code for stop in unpaid.stop_factors]}")


def dump(obj, ind=0):
    """JSON с отступами, но элементы списков-рядов — по одному в строку."""
    pad = "  " * ind
    if isinstance(obj, dict):
        items = [f'{pad}  "{k}": {dump(v, ind + 1).lstrip()}' for k, v in obj.items()]
        return pad + "{\n" + ",\n".join(items) + "\n" + pad + "}"
    if isinstance(obj, list) and obj and isinstance(obj[0], dict):
        rows = [pad + "  " + json.dumps(x, ensure_ascii=False) for x in obj]
        return pad + "[\n" + ",\n".join(rows) + "\n" + pad + "]"
    return pad + json.dumps(obj, ensure_ascii=False)


def render(template, out):
    text = open(template, encoding="utf-8").read()

    def repl(m):
        k, src = m.group(1), m.group(2)
        return "```json\n" + dump(P[k][src]) + "\n```"

    text = re.sub(r"<<(\w+)\.(\w+)>>", repl, text)
    assert "<<" not in text
    open(out, "w", encoding="utf-8").write(text)
    print("written", out)


if __name__ == "__main__":
    if sys.argv[1] == "render":
        render(sys.argv[2], sys.argv[3])
    else:
        check()
