# Chronos

Chronos is a distributed task scheduler and asynchronous job execution platform built incrementally with Java 25 and Spring Boot.

## Current Slice: Job persistence and messaging boundary

The current implementation provides a working REST API backed by PostgreSQL and a transactional messaging boundary:

- Create a job with a JSON payload
- Retrieve a job by UUID
- List jobs with pagination
- Validate job type, priority, and retry limits
- Persist the schema with Flyway
- Write a `JOB_CREATED` outbox event in the same transaction as the job
- Publish pending outbox events to RabbitMQ using priority routing keys
- Consume messages with a bounded worker listener and persist `RUNNING`/`SUCCESS` state
- Retry transient simulated failures with exponential backoff and jitter
- Route exhausted and non-retryable failures to a durable DLQ
- Replay DLQ jobs through the transactional outbox
- Record one execution claim per job attempt to suppress duplicate deliveries
- Recover expired worker leases through a new queued outbox event

Jobs currently begin in `CREATED`. Workers, retries, idempotency, scheduling, and deployment infrastructure will be added in later verified slices.

## Requirements

- Java 25
- Docker, for PostgreSQL and RabbitMQ during local development
- Maven Wrapper (`mvnw.cmd` on Windows)

## Run the API

Start PostgreSQL and RabbitMQ with Docker, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

Default database settings are:

```text
URL:      jdbc:postgresql://localhost:5432/chronos
Username: chronos
Password: chronos
```

RabbitMQ defaults:

```text
AMQP:      localhost:5672
Username:  chronos
Password:  chronos
```

Override database settings with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. RabbitMQ settings use `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, and `RABBITMQ_PASSWORD`. Set `CHRONOS_OUTBOX_PUBLISHER_ENABLED=false` when running only the API without RabbitMQ.

The API transaction creates the job and its pending outbox event together. A scheduled publisher sends compact job messages to `chronos.job.exchange`, routing them to `chronos.high.queue`, `chronos.medium.queue`, or `chronos.low.queue`. Workers are not part of this slice yet.

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

The test suite uses an in-memory H2 database and disables RabbitMQ publishing:

```powershell
.\mvnw.cmd test
```

Retryable simulated failures are republished to a priority-specific RabbitMQ retry queue with per-message expiration. The delay starts at 2 seconds and doubles up to the configured maximum, with 10% jitter to reduce retry synchronization. The retry queue dead-letters the message back to the original priority queue. Unsupported job types are non-retryable and, like exhausted retries, are recorded as `DLQ` and sent to `chronos.dlq`. `GET /api/v1/dlq/jobs` lists these jobs; `POST /api/v1/dlq/jobs/{jobId}/replay` resets the job and creates a new outbox event for normal processing.

Execution idempotency stores a committed unique claim for each `(jobId, attemptNumber)` before the handler runs. A duplicate RabbitMQ delivery sees the existing claim and is acknowledged without invoking the handler again. Delivery remains at-least-once; recovery of a worker that dies while holding a `RUNNING` claim is handled by the upcoming lease/recovery slice.

Workers hold a 60-second database lease in `locked_by` and `lease_until`. A scheduled recovery task finds expired `RUNNING` jobs, returns them to `QUEUED`, and writes a new outbox event with the next attempt number. This allows a crashed worker's work to be retried while preserving the original execution record.
