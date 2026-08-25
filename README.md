# Chronos

Chronos is a distributed task scheduler and asynchronous job execution platform built incrementally with Java 25 and Spring Boot.

## Current Phase: 1 - Job persistence API

The first slice provides a working REST API backed by PostgreSQL:

- Create a job with a JSON payload
- Retrieve a job by UUID
- List jobs with pagination
- Validate job type, priority, and retry limits
- Persist the schema with Flyway

Jobs currently begin in `CREATED`. RabbitMQ publishing, workers, retries, idempotency, scheduling, and deployment infrastructure will be added in later verified phases.

## Requirements

- Java 25
- Docker, for PostgreSQL during local development
- Maven Wrapper (`mvnw.cmd` on Windows)

## Run the Phase 1 API

Start PostgreSQL with a local database named `chronos`, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

Default database settings are:

```text
URL:      jdbc:postgresql://localhost:5432/chronos
Username: chronos
Password: chronos
```

Override them with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` environment variables. Flyway creates the `jobs` table on startup.

## API examples

Create a job:

```http
POST /api/v1/jobs
Content-Type: application/json

{
  "jobType": "SIMULATED",
  "payload": { "durationMs": 1000 },
  "priority": "HIGH",
  "maxRetries": 3
}
```

Retrieve a job:

```http
GET /api/v1/jobs/{jobId}
```

List jobs:

```http
GET /api/v1/jobs?page=0&size=20
```

## Verify

The test suite uses an in-memory H2 database:

```powershell
.\mvnw.cmd test
```

The next implementation phase will introduce RabbitMQ and the outbox boundary while keeping this API contract tested and working.
