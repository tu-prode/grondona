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

## API Endpoints

### Create User
```bash
POST /api/users
Content-Type: application/json

{
    "fullname": "John Doe",
    "username": "johndoe",
    "email": "john@example.com",
    "password": "secret123"
}

# Response: 201 Created
{
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": "uuid",
    "username": "johndoe",
    "email": "john@example.com",
    "fullname": "John Doe"
}
```

### Login
```bash
POST /api/users/login
Content-Type: application/json

{
    "username": "johndoe",
    "password": "secret123"
}

# Response: 200 OK
{
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": "uuid",
    "username": "johndoe",
    "email": "john@example.com",
    "fullname": "John Doe"
}
```

### Update User (Partial Update)
```bash
PATCH /api/users
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
    "fullname": "John Smith",
    "email": "john.smith@example.com"
}

# Response: 200 OK
{
    "id": "uuid",
    "fullname": "John Smith",
    "username": "johndoe",
    "email": "john.smith@example.com",
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-02T00:00:00"
}
```

### Delete User
```bash
DELETE /api/users/{userId}
Authorization: Bearer <jwt-token>

# Response: 204 No Content
```

### Get Current User
```bash
GET /api/users/me
Authorization: Bearer <jwt-token>

# Response: 200 OK
{
    "id": "uuid",
    "fullname": "John Doe",
    "username": "johndoe",
    "email": "john@example.com",
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
}
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
├── docker-compose.yml           # Docker services
├── Dockerfile                   # Application container
├── init.sql                     # Database initialization
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

## License

MIT
