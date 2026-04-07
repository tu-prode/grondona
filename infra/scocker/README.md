# Scocker: Scores Mocker

A tiny Python HTTP server that will simulate results for the matches and expose them through different endpoints.

## Features

- Simulate results according to the stored quotas
- Allow to change the current timestamp and the time pace
- Return matches via HTTP responses

## Requirements

- Docker & Docker Compose
- Make (optional, for convenience commands)
- Python3 (for local development)

## Run locally

```bash
# Start the server locally
python3 scocker.py

# Start the server within a Docker container
make scocker-only
```

