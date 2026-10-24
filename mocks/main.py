import copy

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

from inn import is_valid_inn
from personas import PERSONAS

app = FastAPI(title="External Systems Mocks")

paid_inns: set[str] = set()


class ConnectRequest(BaseModel):
    inn: str
    consent: bool | None = None
    api_key: str | None = None


class PayRequest(BaseModel):
    inn: str


def resolve_inn(inn: str) -> dict:
    if not is_valid_inn(inn):
        raise HTTPException(status_code=422, detail=f"ИНН {inn} не проходит проверку контрольной суммы")
    persona = PERSONAS.get(inn)
    if persona is None:
        raise HTTPException(status_code=404, detail=f"ИНН {inn} не найден в источнике")
    return persona


def resolve_source(inn: str, source: str) -> dict:
    persona = resolve_inn(inn)
    data = persona.get(source.lower())
    if data is None:
        raise HTTPException(status_code=404, detail=f"Источник {source} не заведён для ИНН {inn}")
    return data


def last_months(data: dict, months: int) -> dict:
    result = copy.deepcopy(data)
    result["months"] = result["months"][-months:]
    return result


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/mock/egrul/v1/company")
def egrul(inn: str) -> dict:
    return resolve_source(inn, "EGRUL")


@app.get("/mock/bki/v1/report")
def bki(inn: str) -> dict:
    return resolve_source(inn, "BKI")


@app.get("/mock/fns/v1/debts")
def fns_debts(inn: str) -> dict:
    data = copy.deepcopy(resolve_source(inn, "FNS"))
    if inn in paid_inns:
        data["tax_debt"]["amount"] = 0
        data["tax_debt"]["paid_at"] = "2026-10-24"
    return data


@app.post("/mock/fns/v1/debts/pay")
def fns_pay(request: PayRequest) -> dict:
    resolve_source(request.inn, "FNS")
    paid_inns.add(request.inn)
    return {"source": "FNS", "inn": request.inn, "paid": True}


@app.post("/mock/ofd/v1/connect")
def ofd_connect(request: ConnectRequest) -> dict:
    resolve_source(request.inn, "OFD")
    if request.consent is not True:
        raise HTTPException(status_code=422, detail="Требуется согласие клиента (consent: true)")
    return {"source": "OFD", "inn": request.inn, "connected": True}


@app.get("/mock/ofd/v1/revenue")
def ofd_revenue(inn: str, months: int = Query(default=12, ge=1, le=12)) -> dict:
    return last_months(resolve_source(inn, "OFD"), months)


@app.post("/mock/marketplace/v1/connect")
def marketplace_connect(request: ConnectRequest) -> dict:
    resolve_source(request.inn, "MARKETPLACE")
    if request.api_key != "demo":
        raise HTTPException(status_code=401, detail="Неверный api_key")
    return {"source": "MARKETPLACE", "inn": request.inn, "connected": True}


@app.get("/mock/marketplace/v1/sales")
def marketplace_sales(inn: str, months: int = Query(default=6, ge=1, le=6)) -> dict:
    return last_months(resolve_source(inn, "MARKETPLACE"), months)


@app.post("/mock/acquiring/v1/connect")
def acquiring_connect(request: ConnectRequest) -> dict:
    resolve_source(request.inn, "ACQUIRING")
    if request.consent is not True:
        raise HTTPException(status_code=422, detail="Требуется согласие клиента (consent: true)")
    return {"source": "ACQUIRING", "inn": request.inn, "connected": True}


@app.get("/mock/acquiring/v1/turnover")
def acquiring_turnover(inn: str, months: int = Query(default=12, ge=1, le=12)) -> dict:
    return last_months(resolve_source(inn, "ACQUIRING"), months)


@app.post("/mock/reset")
def reset() -> dict[str, str]:
    paid_inns.clear()
    return {"status": "reset"}


if __name__ == "__main__":
    import os

    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8002")))
