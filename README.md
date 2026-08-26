# Chronos

Chronos is a distributed task scheduler and asynchronous job execution platform built incrementally with Java 25 and Spring Boot.

## Capabilities

The current implementation provides a working REST API backed by PostgreSQL and a transactional messaging boundary:

- Create a job with a JSON payload
- Retrieve a job by UUID
- List jobs with filters and pagination metadata
- Cancel eligible jobs and manually retry eligible terminal jobs
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
- Defer future jobs until the scheduler finds them due
- Export Prometheus metrics through Spring Boot Actuator
- Support PostgreSQL-backed idempotent job submission

See [the API reference](docs/api.md), [architecture](docs/architecture/architecture.md), and [operations runbook](docs/architecture/operations.md) for implementation details and operational boundaries.

Jobs begin in `CREATED`. The same application runs in API, worker, or combined mode; Compose and Kubernetes manifests support separate API and worker deployments. Monitoring and Kubernetes remain demonstration configurations that require environment-specific validation before production use.

## Requirements

- Java 25
- Docker Desktop, for PostgreSQL, RabbitMQ, and Redis during local development and Testcontainers integration tests
- Maven Wrapper (`mvnw.cmd` on Windows)

## Run the API

Start PostgreSQL, RabbitMQ, and Redis with Docker, then run:

```powershell
docker compose up -d
```

After the containers become healthy, run the API locally:

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

RabbitMQ Management UI: `http://localhost:15672`. Redis is available at `localhost:6379`. Copy `.env.example` to `.env` only when you need to override the development defaults; do not use these credentials in production.

Override database settings with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. RabbitMQ settings use `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, and `RABBITMQ_PASSWORD`. Set `CHRONOS_OUTBOX_PUBLISHER_ENABLED=false` when running only the API without RabbitMQ.

The API transaction creates the job and its pending outbox event together. A scheduled publisher sends compact job messages to `chronos.job.exchange`, routing them to `chronos.high.queue`, `chronos.medium.queue`, or `chronos.low.queue`. The local worker runs from the Maven application; Docker Compose provides the infrastructure dependencies.

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

Filter jobs by status, type, priority, or creation time:

```http
GET /api/v1/jobs?status=SUCCESS&priority=HIGH&createdAfter=2026-01-01T00:00:00Z&page=0&size=20
```

Cancel or manually retry a job when its current state permits it:

```http
POST /api/v1/jobs/{jobId}/cancel
POST /api/v1/jobs/{jobId}/retry
```

## Verify

The test suite uses an in-memory H2 database and disables RabbitMQ publishing:

```powershell
.\mvnw.cmd test
```

The test suite also includes PostgreSQL and RabbitMQ Testcontainers integration tests. They start disposable `postgres:16-alpine` and `rabbitmq:4-management-alpine` containers, apply every Flyway migration, and verify persistence plus asynchronous success, retry, DLQ, replay, duplicate-delivery, scheduled-job, and lease-recovery flows. Docker Desktop must be running for these tests.

Retryable simulated failures are republished to a priority-specific RabbitMQ retry queue with per-message expiration. The delay starts at 2 seconds and doubles up to the configured maximum, with 10% jitter to reduce retry synchronization. The retry queue dead-letters the message back to the original priority queue. Unsupported job types are non-retryable and, like exhausted retries, are recorded as `DLQ` and sent to `chronos.dlq`. `GET /api/v1/dlq/jobs` lists these jobs; `POST /api/v1/dlq/jobs/{jobId}/replay` resets the job and creates a new outbox event for normal processing.

Execution idempotency stores a committed unique claim for each `(jobId, attemptNumber)` before the handler runs. A duplicate RabbitMQ delivery sees the existing claim and is acknowledged without invoking the handler again. Delivery remains at-least-once; if a worker dies while holding a `RUNNING` claim, lease recovery re-queues the job through the outbox with a new attempt number.

Workers hold a 60-second database lease in `locked_by` and `lease_until`. A scheduled recovery task finds expired `RUNNING` jobs, returns them to `QUEUED`, and writes a new outbox event with the next attempt number. This allows a crashed worker's work to be retried while preserving the original execution record.

Jobs with a future `scheduledAt` are persisted as `CREATED` without an outbox event, so they cannot execute early. The scheduler polls PostgreSQL every second by default, locks due jobs, changes them to `QUEUED`, and creates a `JOB_SCHEDULED` outbox event. Configure the interval with `CHRONOS_SCHEDULER_INTERVAL_MS`.

Send an `Idempotency-Key` header with `POST /api/v1/jobs` to make submission retry-safe. Reusing the key with the same request returns the original job; reusing it with a different request returns `409 IDEMPOTENCY_CONFLICT`. PostgreSQL stores the request fingerprint and enforces key uniqueness.

## Run the full Docker stack

To run the API and worker as separate containers:

```powershell
docker compose -f docker-compose.full.yml up --build
```

The API is available at `http://localhost:8080`, while the worker runs the same image with `CHRONOS_MODE=worker`. The API runs with `CHRONOS_MODE=api`, so only it publishes the outbox and schedules future jobs. Stop the stack with:

```powershell
docker compose -f docker-compose.full.yml down
```

Prometheus is available at `http://localhost:9090`, and Grafana is available at `http://localhost:3000` with the development credentials from `.env.example`. The pre-provisioned `Chronos Overview` dashboard uses the API's `/actuator/prometheus` endpoint.

## Kubernetes and KEDA

The Kubernetes manifests are in `infrastructure/kubernetes`. They deploy two API replicas and two worker replicas using the same image, with separate `CHRONOS_MODE` values. Before applying them, replace the example image `ghcr.io/atharvagitaye/chronos:latest` and the placeholder credentials in `secret.yaml` with values appropriate for the cluster. PostgreSQL, RabbitMQ, and Redis are expected to be reachable at the hostnames configured in that Secret.

Install KEDA in the cluster, then apply the manifests:

```powershell
kubectl apply -f infrastructure/kubernetes
```

`keda-scaledobject.yaml` scales workers from 2 to 10 replicas using the HIGH, MEDIUM, and LOW RabbitMQ queue depths. This is a demonstration deployment, not a production-ready cluster configuration.

For a static manifest rendering check, run `kubectl kustomize infrastructure/kubernetes`. A live `kubectl apply` also requires a configured Kubernetes context and the KEDA CRDs installed in the target cluster.

## Load testing

Use the Windows-friendly load script against a local or disposable environment:

```powershell
.\scripts\Submit-LoadJobs.ps1 -Count 1000 -DurationMs 100 -UseIdempotencyKeys -WaitForCompletion
```

The script reports observed submission time, throughput, terminal job counts, and completion time when polling is enabled. Inspect queue depth, retries, DLQ count, and worker behavior in Prometheus/Grafana during the run; no benchmark result is claimed until it has been measured on the target machine.

A measured local 100-job Compose run is recorded in [load-test-results.md](docs/load-test-results.md). It is a development observation, not a production benchmark.
