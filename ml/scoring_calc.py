"""Калькулятор вилки по scoring_rules.yaml — проверка «на бумаге» для T06.

Запуск: python3 scoring_calc.py  (или python3 synthetic_data.py check)
Числа (базы, пороги, диапазоны «неизвестно», ступени) берутся из YAML,
условия ступеней повторены в коде. Детерминированно: один вход — один выход.
"""

import sys
from datetime import date

import yaml

AS_OF = date(2026, 10, 23)


def months_between(d1: date, d2: date) -> int:
    return (d2.year - d1.year) * 12 + (d2.month - d1.month) - (1 if d2.day < d1.day else 0)


def annuity(amount: float, term: int, rate_pct: float) -> float:
    r = rate_pct / 100 / 12
    return amount * r / (1 - (1 + r) ** -term)


class Engine:
    def __init__(self, rules: dict):
        self.r = rules
        self.f = rules["factors"]

    # --- стоп-факторы -------------------------------------------------------
    def stop_factors(self, c: dict) -> list[tuple[str, bool]]:
        out = []
        if c["egrul_status"] != "ACTIVE":
            out.append(("LIQUIDATION", False))
        if months_between(c["registered_at"], AS_OF) < 6:
            out.append(("YOUNG_BUSINESS", False))
        if months_between(c["director_since"], AS_OF) < 3:
            out.append(("NEW_DIRECTOR", False))
        if c["mass_address"]:
            out.append(("MASS_ADDRESS", False))
        if c.get("tax_debt", 0) > 0:
            out.append(("TAX_DEBT", True))
        if c.get("bki_current_overdue", 0) > 0:
            out.append(("BKI_OVERDUE", False))
        return out

    # --- факторы ------------------------------------------------------------
    def credit_history(self, c):
        s, d = c["credit_score"], c["delinq_12m"]
        if d > 0 or s < 500:
            return 2.0
        if s >= 700:
            return -1.0
        if s >= 600:
            return 0.0
        return 1.0

    def business_age(self, c):
        m = months_between(c["registered_at"], AS_OF)
        return 1.5 if m < 12 else 0.5 if m < 36 else -0.5

    def tax_discipline(self, c):
        return 1.0 if c.get("unfiled_reports", 0) > 0 else 0.0

    def debt_load(self, c, product, amount, term):
        rev = c.get("monthly_revenue")
        if rev is None:
            return None, None
        if product == "OVERDRAFT":
            share = amount / rev
            return (-0.5 if share <= 0.15 else 0.5), share
        pti = annuity(amount, term, self.r["calc_rate_pct"]) / rev
        v = -1.5 if pti <= 0.05 else -0.5 if pti <= 0.10 else 0.5 if pti <= 0.25 else 1.5
        return v, pti

    def revenue_stability(self, c):
        st = c.get("stability")  # None | 'clean' | 'anomaly_open' | 'explained' | 'document' | 'rejected'
        return {None: None, "anomaly_open": None, "clean": 0.0, "explained": 0.0,
                "document": 1.0, "rejected": 2.5}[st]

    def purpose(self, c):
        p = c.get("purpose")  # None | 'match' | 'mismatch'
        return None if p is None else (-0.5 if p == "match" else 0.5)

    def collateral(self, c):
        k = c.get("collateral")  # None | 'guarantee' | 'declined'
        return None if k is None else (-1.5 if k == "guarantee" else 0.0)

    # --- вилка --------------------------------------------------------------
    def fork(self, c, product, amount, term, collateral_override=None):
        p = self.r["products"][product]
        skip = set(p.get("skip_factors", []))
        cc = dict(c)
        if collateral_override is not None:
            cc["collateral"] = collateral_override
        dl, metric = self.debt_load(cc, product, amount, term)
        vals = {
            "credit_history": self.credit_history(cc),
            "business_age": self.business_age(cc),
            "tax_discipline": self.tax_discipline(cc),
            "debt_load": dl,
            "revenue_stability": self.revenue_stability(cc),
            "purpose": self.purpose(cc),
            "collateral": self.collateral(cc),
        }
        lo = hi = p["base_rate"]
        spots = []
        for name, v in vals.items():
            if name in skip:
                continue
            if v is None:
                best, worst = self.f[name]["unknown"]
                lo += best
                hi += worst
                spots.append((name, worst - best))
            else:
                lo += v
                hi += v
        # политика продукта (кредитная логика, не скоринг)
        policy_ok = True
        if metric is not None:
            cap = p.get("limit_cap") if product == "OVERDRAFT" else p.get("pti_cap")
            policy_ok = metric <= cap
        a_min, a_max = p["amount"]
        policy_ok = policy_ok and a_min <= amount <= a_max
        return dict(lo=round(lo, 2), hi=round(hi, 2), spots=spots, vals=vals,
                    metric=metric, policy_ok=policy_ok)

    def passes(self, fk, product):
        return fk["policy_ok"] and fk["hi"] <= self.r["products"][product]["approve_max"]

    # --- сетка иных условий ---------------------------------------------------
    def grid(self, c, req_product, amount, term):
        g = self.r["offer_grid"]
        offers = []
        allowed = {req_product, *g["other_products"]}
        for product, p in self.r["products"].items():
            if product not in allowed:
                continue
            if product == "OVERDRAFT":
                rev = c.get("monthly_revenue")
                if rev is None:
                    continue
                cands = [(min(amount, int(rev * p["limit_cap"] // 10000 * 10000)), 12)]
            else:
                terms = sorted({t for t in (term, max(p["terms"])) if t in p["terms"]} or {max(p["terms"])})
                amounts = [amount] if product != req_product else []
                amounts += [round(amount * (1 - cut), -3) for cut in g["amount_cuts"]]
                cands = [(a, t) for a in amounts for t in terms]
            for coll in ("declined", "guarantee"):
                if coll == "guarantee" and product == "OVERDRAFT":
                    continue
                best = None
                for a, t in cands:
                    fk = self.fork(c, product, a, t, collateral_override=coll)
                    if self.passes(fk, product) and (best is None or a > best[1]):
                        best = (product, a, t, coll, fk)
                if best:
                    offers.append(best)
                    break  # без обеспечения прошло — вариант с обеспечением не нужен
        return offers

    # --- решение ------------------------------------------------------------
    def decide(self, c, product, amount, term):
        p = self.r["products"][product]
        fk = self.fork(c, product, amount, term)
        spots = [{"id": code.lower(), "type": "STOP_FACTOR", "status": "OPEN", "pricePp": None, "removable": rem,
                  "scoringFactor": None} for code, rem in self.stop_factors(c)]
        spots += [{"id": name, "type": SPOT_FACTORS[name], "status": "OPEN" if fk["vals"][name] is None else "CLOSED",
                   "pricePp": worst - best, "removable": None, "scoringFactor": name}
                  for name in SPOT_FACTORS if name not in p.get("skip_factors", [])
                  for best, worst in [self.f[name]["unknown"]]]
        needs_grid = not (fk["policy_ok"] and fk["hi"] <= p["approve_max"])
        offers = self.grid(c, product, amount, term) if needs_grid and not any(
            s["type"] == "STOP_FACTOR" for s in spots) else []
        status, reason_spot, rule = decision(fk["lo"], fk["hi"], p["approve_max"], fk["policy_ok"], len(offers),
                                             spots)
        why = offers if status == "COUNTER_OFFER" else f"{rule}: {RULE_TEXT[rule]}" + (
            f", причина — {reason_spot}" if reason_spot else "")
        return status, why, fk


SPOT_FACTORS = {"debt_load": "NO_DATA", "revenue_stability": "NO_DATA", "purpose": "NO_DATA",
                "collateral": "COLLATERAL"}
RULE_TEXT = {
    "R1": "неустранимый стоп-фактор",
    "R2": "открыт устранимый стоп-фактор",
    "R3": "политика выполнена и правый край ≤ порога одобрения",
    "R4": "запрошенное не проходит, в сетке есть проходящий вариант",
    "R5": "запрошенное не проходит, ни один вариант сетки не прошёл",
    "R6": "вилка пересекает порог одобрения",
}


def decision(rate_from, rate_to, approve_max, policy_ok, grid_offers, spots):
    for removable, rule in ((False, "R1"), (True, "R2")):
        stop = next((s for s in spots if s["type"] == "STOP_FACTOR" and s["status"] != "CLOSED"
                     and bool(s["removable"]) == removable), None)
        if stop:
            return "REJECTED", stop["id"], rule
    if policy_ok and rate_to <= approve_max:
        return "APPROVED", None, "R3"
    if not policy_ok or rate_from > approve_max:
        if grid_offers:
            return "COUNTER_OFFER", None, "R4"
        return "REJECTED", reason_spot(spots), "R5"
    return "UNDERWRITING", None, "R6"


def reason_spot(spots):
    candidates = [s for s in spots if s["status"] != "CLOSED" and s["pricePp"] is not None]
    if candidates:
        return max(enumerate(candidates), key=lambda item: (item[1]["pricePp"], -item[0]))[1]["id"]
    debt = next((s for s in spots if s["scoringFactor"] == "debt_load"), None)
    if debt:
        return debt["id"]
    if not spots:
        raise ValueError("у заявки нет пятен — отказ без причины невозможен")
    return spots[0]["id"]


if __name__ == "__main__":
    # Данные персонажей живут в synthetic_data.py (единый источник, как и synthetic-data.md).
    import synthetic_data

    synthetic_data.check()
