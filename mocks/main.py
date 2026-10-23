from fastapi import FastAPI

app = FastAPI(title="External Systems Mocks")


@app.get("/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import os

    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8002")))
