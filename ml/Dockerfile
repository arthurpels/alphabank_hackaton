FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py scoring_rules.yaml ./
COPY scoring ./scoring
EXPOSE 8001
CMD ["python", "main.py"]
