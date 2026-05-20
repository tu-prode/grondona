# Grondona Server

A Kotlin HTTP server with user management, built with Spring Boot, Maven, and Docker.

## Features

- User registration with JWT token generation
- User authentication (login)
- User profile updates (PATCH)
- User deletion
- MD5 password hashing
- PostgreSQL database
- Docker containerization

## Requirements

- Docker & Docker Compose
- Make (optional, for convenience commands)
- JDK 17+ (for local development)
- Maven 3.9+ (for local development)

## Quick Start

```bash
# Start the server (builds and runs in Docker)
make start

# View logs
make logs

# Stop the server
make stop
```

## API Documentation

Full API documentation is available in OpenAPI 3.0 format at [`docs/openapi.yaml`](docs/openapi.yaml).

### Quick Reference

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/users` | Create a new user | No |
| POST | `/api/users/login` | User login | No |
| GET | `/api/users/me` | Get current user profile | Yes |
| PATCH | `/api/users` | Update current user | Yes |
| DELETE | `/api/users/{userId}` | Delete user | Yes |

### Viewing the API Documentation

You can view the OpenAPI specification using:

- **Swagger Editor**: Open [editor.swagger.io](https://editor.swagger.io) and paste the contents of `docs/openapi.yaml`
- **VS Code**: Install the "OpenAPI (Swagger) Editor" extension
- **Redoc**: Use the [Redoc CLI](https://github.com/Redocly/redoc) to generate HTML documentation

```bash
# Generate HTML documentation with Redoc
npx @redocly/cli build-docs docs/openapi.yaml -o docs/api.html
```

## Project Structure

```
grondona/
├── src/main/kotlin/com/grondona/
│   ├── Application.kt           # Main application entry point
│   ├── config/
│   │   ├── JwtConfig.kt         # JWT configuration
│   │   └── SecurityConfig.kt    # Security configuration
│   ├── controller/
│   │   └── UserController.kt    # REST endpoints
│   ├── exception/
│   │   ├── Exceptions.kt        # Custom exceptions
│   │   └── GlobalExceptionHandler.kt
│   ├── model/
│   │   ├── User.kt              # User entity
│   │   └── dto/                 # Data transfer objects
│   ├── repository/
│   │   └── UserRepository.kt    # Database access
│   ├── security/
│   │   ├── JwtService.kt        # JWT generation/validation
│   │   ├── JwtAuthenticationFilter.kt
│   │   └── JwtUserPrincipal.kt
│   └── service/
│       └── UserService.kt       # Business logic
├── src/main/resources/
│   └── application.properties   # Application configuration
├── src/test/kotlin/             # Unit and integration tests
├── docs/
│   └── openapi.yaml             # OpenAPI 3.0 specification
├── infra/
│   ├── docker-compose.yml       # Docker services
│   ├── docker-compose.debug.yml # Debug mode override
│   ├── Dockerfile               # Production container
│   ├── Dockerfile.debug         # Debug container
│   └── initdb/                  # Ordered database initialization scripts
├── Makefile                     # Convenience commands
└── pom.xml                      # Maven configuration
```

## Architecture

The project follows the **Controller/Service/Repository** pattern:

- **Controller Layer**: Handles HTTP requests, validates input, and returns responses
- **Service Layer**: Contains business logic, authentication, and data transformation
- **Repository Layer**: Manages database access using Spring Data JPA

## Configuration

Environment variables (can be set in `docker-compose.yml`):

| Variable | Default | Description |
|----------|---------|-------------|
| DB_HOST | localhost | PostgreSQL host |
| DB_PORT | 5432 | PostgreSQL port |
| DB_NAME | grondona | Database name |
| DB_USER | grondona | Database user |
| DB_PASSWORD | grondona | Database password |
| JWT_SECRET | (long default) | Secret key for JWT signing |
| JWT_EXPIRATION | 86400000 | Token expiration (ms, default 24h) |

## Local Development

```bash
# Start only the database
make db-only

# Run tests
make test

# Build and run locally
make run-local
```

## Make Commands

| Command | Description |
|---------|-------------|
| `make help` | Show available commands |
| `make build` | Build Docker images |
| `make start` | Start all services |
| `make stop` | Stop all services |
| `make restart` | Restart all services |
| `make logs` | View application logs |
| `make logs-db` | View database logs |
| `make clean` | Stop and remove volumes |
| `make db-only` | Start only PostgreSQL |
| `make psql` | Connect to PostgreSQL CLI |
| `make test` | Run tests |
| `make debug` | Start in debug mode (port 5005) |
| `make debug-stop` | Stop debug services |

## License

MIT
