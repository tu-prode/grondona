.PHONY: help build start stop restart logs clean db-only test

# Default target
help:
	@echo "Grondona Server - Available commands:"
	@echo ""
	@echo "  make build      - Build the Docker images"
	@echo "  make start      - Start all services (PostgreSQL + App)"
	@echo "  make stop       - Stop all services"
	@echo "  make restart    - Restart all services"
	@echo "  make logs       - View application logs"
	@echo "  make logs-db    - View database logs"
	@echo "  make clean      - Stop services and remove volumes"
	@echo "  make db-only    - Start only the database"
	@echo "  make shell      - Open a shell in the app container"
	@echo "  make psql       - Connect to PostgreSQL CLI"
	@echo "  make test       - Run tests"
	@echo "  make build-jar  - Build JAR file locally"
	@echo ""

# Build Docker images
build:
	@echo "Building Docker images..."
	docker-compose build

# Start all services
start: build
	@echo "Starting all services..."
	docker-compose up -d
	@echo ""
	@echo "Services started successfully!"
	@echo "  - API: http://localhost:8080"
	@echo "  - PostgreSQL: localhost:5432"
	@echo ""
	@echo "Run 'make logs' to view application logs"

# Stop all services
stop:
	@echo "Stopping all services..."
	docker-compose down

# Restart all services
restart: stop start

# View application logs
logs:
	docker-compose logs -f app

# View database logs
logs-db:
	docker-compose logs -f db

# Clean up everything including volumes
clean:
	@echo "Stopping services and removing volumes..."
	docker-compose down -v
	@echo "Cleanup complete!"

# Start only the database (useful for local development)
db-only:
	@echo "Starting PostgreSQL..."
	docker-compose up -d db
	@echo "PostgreSQL is running on localhost:5432"

# Open shell in app container
shell:
	docker-compose exec app /bin/sh

# Connect to PostgreSQL CLI
psql:
	docker-compose exec db psql -U grondona -d grondona

# Run tests
test:
	@echo "Running tests..."
	mvn test

# Build JAR file locally (requires Maven and JDK 17+)
build-jar:
	@echo "Building JAR file..."
	mvn clean package -DskipTests

# Run locally (requires db-only to be running)
run-local: build-jar
	@echo "Starting application locally..."
	DB_HOST=localhost DB_PORT=5432 DB_NAME=grondona DB_USER=grondona DB_PASSWORD=grondona \
	java -jar target/*.jar
