FROM python:3.12-alpine

WORKDIR /app

COPY infra/scocker/scocker.py .

ENV PYTHONUNBUFFERED=1

EXPOSE 8085

CMD ["python", "scocker.py"]