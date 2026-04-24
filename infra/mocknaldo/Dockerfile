FROM python:3.12-alpine

WORKDIR /app

COPY infra/mocknaldo/mocknaldo.py .
COPY infra/mocknaldo/combinations.csv .

ENV PYTHONUNBUFFERED=1

EXPOSE 8085

CMD ["python", "mocknaldo.py"]