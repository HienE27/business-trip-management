FROM python:3.11-slim

WORKDIR /workspace
ENV PYTHONPATH=/workspace

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY tests ./tests

CMD ["python", "-m", "app.engine", "app/sample_engine_input.json", "app/sample_engine_output.json", "--algorithm", "ortools", "--pretty"]
