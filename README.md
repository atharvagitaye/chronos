# Chronos

Chronos is a fault-tolerant job scheduling and asynchronous execution platform built with Java 25 and Spring Boot. It accepts jobs through a REST API, persists state in PostgreSQL, and executes work asynchronously through RabbitMQ.

## Highlights

- Priority queues, scheduled jobs, retries with exponential backoff, and a dead-letter queue
- Transactional outbox for reliable database-to-broker publishing
- Request idempotency and execution claims for at-least-once delivery
- Worker leases and recovery for interrupted executions
- Redis-based scheduler coordination with PostgreSQL as the source of truth
- Prometheus metrics, Grafana dashboard, Docker Compose, and Kubernetes/KEDA manifests

## Quick Start

Requirements: Java 25, Docker Desktop, and Maven Wrapper.

Start the complete local environment:

```powershell
docker compose -f docker-compose.full.yml up --build -d
```

Verify the API:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Create a job:

```powershell
$job = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/jobs" `
  -ContentType "application/json" `
  -Headers @{ "Idempotency-Key" = [guid]::NewGuid().ToString() } `
  -Body '{
    "jobType": "SIMULATED",
    "payload": { "durationMs": 1000, "failureMode": "NONE" },
    "priority": "HIGH",
    "maxRetries": 3
  }'

Invoke-RestMethod "http://localhost:8080/api/v1/jobs/$($job.jobId)"
```

The job should progress to `SUCCESS`. Use `FAIL_ONCE` to observe retry behavior or `ALWAYS_FAIL` to route a job to the DLQ.

Stop the local environment:

```powershell
docker compose -f docker-compose.full.yml down
```

## Services

| Service | Address | Development credentials |
| --- | --- | --- |
| Chronos API | `http://localhost:8080` | None |
| RabbitMQ Management | `http://localhost:15672` | `chronos` / `chronos` |
| Prometheus | `http://localhost:9090` | None |
| Grafana | `http://localhost:3000` | `admin` / `admin` |

## API and Operations

- [API reference](docs/api.md) and [OpenAPI contract](docs/openapi.yaml)
- [Architecture](docs/architecture/architecture.md) and [operations runbook](docs/architecture/operations.md)
- [Architecture decisions](docs/adr)
- [Measured local load-test result](docs/load-test-results.md)

The API supports job creation, retrieval, filtering, cancellation, manual retry, DLQ inspection, and DLQ replay. Use `Idempotency-Key` with `POST /api/v1/jobs` when clients may retry requests.

## Development

Run infrastructure only when starting the application from Maven:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Run the test suite, including Testcontainers coverage for PostgreSQL, RabbitMQ, and Redis:

```powershell
.\mvnw.cmd test
```

Run a configurable local load test:

```powershell
.\scripts\Submit-LoadJobs.ps1 -Count 100 -DurationMs 20 -UseIdempotencyKeys -WaitForCompletion
```

## Deployment

`infrastructure/kubernetes` contains API and worker deployments, resource limits, probes, and KEDA RabbitMQ scaling. Replace the example image and Secret placeholders before applying to a cluster. Render the manifests locally with:

```powershell
kubectl kustomize infrastructure/kubernetes
```
