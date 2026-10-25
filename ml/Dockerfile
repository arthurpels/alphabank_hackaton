FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py scoring_rules.yaml llm.yaml okved.yaml ./
COPY scoring ./scoring
COPY eval/replay ./eval/replay
EXPOSE 8001
CMD ["python", "main.py"]
