"""Синтетические данные персонажей — единый источник для synthetic-data.md.

python3 synthetic_data.py render  → печатает JSON-блоки для документа
python3 synthetic_data.py check   → считает производные (выручка, аномалии) и вилки
"""

import json
import re
import sys
from datetime import date
from pathlib import Path

import yaml

from scoring_calc import AS_OF, Engine, months_between

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
                "months": [{"month": m, "revenue": v} for m, v in zip(M12, coffee_ofd)],
                "share_of_total_revenue": 0.7},
        "acquiring": {"source": "ACQUIRING", "inn": "7701234560",
                      "months": [{"month": m, "turnover": int(round(v * 0.67, -4)), "tx_count": round(v * 0.67 / 233)}
                                 for m, v in zip(M12, coffee_ofd)],
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
                                   for m, p, r in zip(M6, wb_payout, wb_returns)],
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
                      "months": [{"month": m, "turnover": v, "tx_count": round(v / 4800)} for m, v in zip(M12, fns_acq)],
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
                      "months": [{"month": m, "turnover": v, "tx_count": round(v / 1500)} for m, v in zip(M6, shell_acq)],
                      "avg_ticket": 1500},
    },
}


# --- производные: из ответов моков в вход скоринга -------------------------------
def anomalies(series: list[int]) -> list[str]:
    """Простые правила T26: падение месяц к месяцу > 30 %, рост > ×3."""
    out = []
    for i in range(1, len(series)):
        prev, cur = series[i - 1], series[i]
        if cur < prev * 0.7:
            out.append(f"падение {round((1 - cur / prev) * 100)} % в месяце {i + 1}")
        if cur > prev * 3:
            out.append(f"рост ×{cur / prev:.1f} в месяце {i + 1}")
    return out


def base_input(p):
    e, b, f = p["egrul"], p["bki"], p["fns"]
    return dict(egrul_status=e["status"], registered_at=date.fromisoformat(e["registered_at"]),
                director_since=date.fromisoformat(e["director"]["appointed_at"]),
                mass_address=e["address"]["is_mass"], credit_score=b["credit_score"],
                delinq_12m=b["delinquencies"]["last_12m"], bki_current_overdue=b["delinquencies"]["current"],
                tax_debt=f["tax_debt"]["amount"], unfiled_reports=f["unfiled_reports"])


def avg(xs):
    return sum(xs) / len(xs)


def check():
    e = Engine(yaml.safe_load(open(Path(__file__).with_name("scoring_rules.yaml"), encoding="utf-8")))
    print("Производные:")
    for k, series in [("coffee OFD", coffee_ofd), ("fns acquiring", fns_acq), ("wb payout", wb_payout),
                      ("shell acquiring", shell_acq)]:
        print(f"  {k}: среднее {avg(series):,.0f}, аномалии: {anomalies(series) or 'нет'}")
    print(f"  wb returns max {max(wb_returns)} % (порог 20 %)")
    for k in P:
        e_ = P[k]["egrul"]
        print(f"  {k}: возраст {months_between(date.fromisoformat(e_['registered_at']), AS_OF)} мес, "
              f"директор {months_between(date.fromisoformat(e_['director']['appointed_at']), AS_OF)} мес")

    scen = {
        "coffee": [("ИНН", {}), ("ОФД", {"monthly_revenue": avg(coffee_ofd), "stability": "anomaly_open"}),
                   ("март принят", {"monthly_revenue": avg(coffee_ofd), "stability": "explained"}),
                   ("цель", {"monthly_revenue": avg(coffee_ofd), "stability": "explained", "purpose": "match"})],
        "builder": [("ИНН", {}), ("цель", {"purpose": "match"}),
                    ("договор принят", {"purpose": "match", "stability": "document",
                                        "monthly_revenue": P["builder"]["document"]["amount"] / 12})],
        "wb": [("ИНН", {}), ("маркетплейс", {"monthly_revenue": avg(wb_payout), "stability": "clean"}),
               ("цель", {"monthly_revenue": avg(wb_payout), "stability": "clean", "purpose": "match"})],
        "fns": [("ИНН", {}), ("эквайринг+цель", {"monthly_revenue": avg(fns_acq), "stability": "clean", "purpose": "match"}),
                ("погасил", {"monthly_revenue": avg(fns_acq), "stability": "clean", "purpose": "match", "tax_debt": 0})],
        "shell": [("ИНН", {})],
    }
    print("\nВилки:")
    for k, steps in scen.items():
        a = P[k]["app"]
        c0 = base_input(P[k])
        for label, upd in steps:
            c = {**c0, **upd}
            fk = e.fork(c, a["product"], a["amount"], a["term"])
            m = f" нагрузка {fk['metric'] * 100:.1f}%" if fk["metric"] is not None else ""
            print(f"  {k} · {label}: {fk['lo']}–{fk['hi']}{m} · пятна {[(n, w) for n, w in fk['spots']]}"
                  f" · стоп {e.stop_factors(c)}")
        v, why, _ = e.decide({**c0, **steps[-1][1]}, a["product"], a["amount"], a["term"])
        if v == "COUNTER_OFFER":
            why = [(o[0], o[1], o[2], o[3], o[4]["hi"], round(o[4]["metric"] * 100, 1)) for o in why]
        print(f"  {k} → {v}: {why}")
        if k == "builder":
            print("   при подаче:", e.decide({**c0, **steps[1][1]}, a["product"], a["amount"], a["term"])[:2])
        if k == "fns":
            print("   без погашения:", e.decide({**c0, **steps[1][1]}, a["product"], a["amount"], a["term"])[:2])


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
