# Chronos Agent Handoff

## Purpose

This file is the continuation guide for the next coding agent. Read it together with `README.md` and the local project requirements. Implement work incrementally, validate it, and commit each coherent change. Do not recreate or add `PROMPT.md` to Git; it is intentionally ignored and may exist locally as agent context.

## Current Baseline

- Repository: `https://github.com/atharvagitaye/chronos.git`
- Branch: `main`
- Java: 25
- Spring Boot: 4.1.1
- Build: Maven Wrapper
- Latest commit: `8e54102 test: cover rabbitmq job execution`
- Working tree at handoff: expected clean except ignored `.idea/`, `PROMPT.md`, and `target/`
- Docker Desktop is available and has been used successfully.

## Completed Capabilities

- Spring Boot REST API under `/api/v1/jobs`
- Create, retrieve, and paginated list jobs
- PostgreSQL persistence with Flyway migrations `V1` through `V5`
- JSON payload persistence
- Job statuses and basic state transitions
- Transactional outbox for job creation, scheduling, replay, and lease recovery
- RabbitMQ direct exchange with HIGH, MEDIUM, and LOW queues
- Compact job messages containing job ID, attempt, type, and priority
- RabbitMQ outbox publisher with pending-event retry
- Bounded worker listener with configurable concurrency
- Simulated handler with duration and failure modes
- Retryable and non-retryable exception classification
- Exponential retry delay with jitter and TTL/DLX retry queues
- Durable `chronos.dlq` queue
- DLQ listing and replay endpoints
- Execution idempotency using unique `(job_id, attempt_number)` claims
- Worker leases and expired-lease recovery through the outbox
- Future scheduled jobs and due-job scheduler
- PostgreSQL-backed request idempotency using `Idempotency-Key`
- Java 25 multi-stage non-root Docker image
- Infrastructure-only and full Docker Compose files
- API/worker execution modes through `CHRONOS_MODE=api|worker|all`
- Actuator Prometheus endpoint and custom job metrics
- Prometheus scrape configuration and Grafana dashboard provisioning
- Kubernetes API/worker Deployments, Services, ConfigMap, Secret template
- KEDA RabbitMQ queue-depth autoscaling configuration
- PostgreSQL Testcontainers integration test
- PostgreSQL + RabbitMQ Testcontainers end-to-end success-flow test

## Verified Commands

Run unit and integration tests from PowerShell:

```powershell
.\mvnw.cmd test
```

Expected current result: 16 tests pass. Docker Desktop must be running because Testcontainers starts PostgreSQL and RabbitMQ.

Validate Compose files:

```powershell
docker compose -f docker-compose.yml config
docker compose -f docker-compose.full.yml config
```

Start infrastructure only for local Maven development:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Start the complete Docker stack:

```powershell
docker compose -f docker-compose.full.yml up --build
```

Stop the complete stack:

```powershell
docker compose -f docker-compose.full.yml down
```

## Recommended Remaining Work

### 1. Add failure-flow integration tests

Add Testcontainers tests in `src/test/java/.../integration` for:

- `FAIL_ONCE` retries and then succeeds.
- `ALWAYS_FAIL` retries until `DLQ`.
- DLQ replay returns the job to normal processing and eventually succeeds after payload/configuration is corrected where applicable.
- Duplicate delivery does not invoke the handler twice.
- Future scheduled job does not execute before `scheduledAt`.
- Expired worker lease produces a new attempt.
- Outbox remains pending when RabbitMQ is unavailable and is published after recovery.

Use short test-specific retry intervals where practical. Do not make tests depend on long real delays. Isolate container-backed Spring contexts with `@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)` so scheduled tasks do not use stopped containers.

### 2. Add Redis integration and distributed scheduler locking

Redis is currently provisioned by Docker and expected by the architecture, but application code does not yet use it. Add Redis only for coordination, not as the source of truth:

- Add the Spring Data Redis dependency.
- Add a small scheduler lock abstraction.
- Ensure only one scheduler instance publishes a due job at a time.
- Add a Redis Testcontainers test or focused integration coverage.
- Document degraded behavior when Redis is unavailable.

Avoid introducing Redis-based job persistence or request idempotency.

### 3. Complete missing REST operations

The requirements call for these endpoints, which are not all implemented yet:

- `POST /api/v1/jobs/{jobId}/cancel`
- `POST /api/v1/jobs/{jobId}/retry`
- Filtering jobs by status, job type, priority, date, and pagination metadata

Implement legal state transitions in the service/entity, never accept arbitrary client status changes, and add controller tests for valid and invalid transitions.

### 4. Improve API response and documentation quality

- Document all current endpoints, headers, statuses, error codes, and examples.
- Add OpenAPI/Swagger support if it is not already present.
- Consider returning a page response rather than a bare list for paginated job results.
- Fix stale README sentences that say workers, leases, or idempotency are future work even though they are implemented.
- Keep claims tied to tests or observed Docker runs.

### 5. Add queue and worker observability

Current custom metrics cover submission and worker outcomes, but the requirements also call for useful queue/worker metrics. Add low-cardinality metrics for:

- Queue depth per priority.
- Active worker count.
- Completed worker work.
- Job wait duration.
- DLQ count/rate.

Do not label metrics with job IDs or arbitrary payload values. Update the Grafana dashboard and verify the Prometheus target and queries with the full Docker stack.

### 6. Add load testing

Create a Windows-friendly script, preferably PowerShell, under `scripts/`, for submitting configurable job counts such as 100, 1,000, and 5,000. It should support:

- API URL parameter.
- Job count parameter.
- Duration/failure mode parameters.
- Optional idempotency-key generation.
- Clear progress and error reporting.

Measure only what is actually observed: submission time, completion time, queue depth, retry count, DLQ count, and worker/container behavior. Never fabricate benchmark results. Add a README section with a placeholder for real measurements until they are run.

### 7. Kubernetes validation and operational polish

- Validate manifests against a real Kubernetes cluster or local Docker Desktop Kubernetes after enabling it.
- Replace placeholder image and Secret values only in local deployment instructions; do not commit real credentials.
- Confirm KEDA version compatibility and secret-backed RabbitMQ authentication.
- Add resource/readiness/liveness documentation.
- Do not claim Kubernetes/KEDA is production-ready.

### 8. Architecture documentation and ADRs

Create the missing documentation requested by the requirements:

- `docs/architecture/architecture.md`
- `docs/architecture/system-design.md`
- ER diagram
- RabbitMQ topology diagram
- Normal, retry, DLQ, duplicate, scheduled, and outbox-recovery sequence diagrams
- ADRs for RabbitMQ, PostgreSQL source of truth, outbox, at-least-once delivery, idempotency, Redis, and Kubernetes/KEDA
- Failure scenario runbook

Use Mermaid where useful. Explain that delivery is at-least-once, not exactly-once, and describe the crash window honestly.

## Important Technical Caveats

- Kafka is explicitly out of scope. Use RabbitMQ.
- PostgreSQL is the source of truth. Do not move state into Redis or RabbitMQ messages.
- The same JAR supports API, worker, and all modes.
- The current full Compose stack uses a placeholder GHCR image in Kubernetes manifests; build/push a real image only when explicitly needed.
- Flyway migration history matters. Never modify an already-applied migration. Add a new forward migration instead.
- `V4__create_job_executions.sql` is intentionally idempotent because execution-table creation was previously added to `V2` during development. Preserve compatibility with existing volumes.
- Testcontainers tests must use the PostgreSQL driver override because the shared test properties specify H2.
- RabbitMQ test containers use `rabbitmq:4-management-alpine` and PostgreSQL uses `postgres:16-alpine`.
- Do not add large refactors, microservices, Kafka, Elasticsearch, authentication, or a React dashboard unless explicitly requested.
- Use Java 25, not Java 21.
- Commit messages should describe behavior or infrastructure, for example `feat: add failure flow integration tests`; do not use vague phase-only commit names.

## Definition of Done for Each Increment

1. Read the owning code path and a nearby test.
2. State a concrete behavior hypothesis and the cheapest check that can disprove it.
3. Make the smallest focused edit.
4. Run a narrow test immediately, then the full suite when appropriate.
5. Run `git diff --check`.
6. Update README/docs if behavior changed.
7. Commit only the coherent increment.
8. Push to `origin/main` only after tests pass.
9. Report what was verified and any environment limitation.

## Suggested Immediate Task

Start with failure-flow integration tests using the existing PostgreSQL + RabbitMQ Testcontainers pattern. This is the highest-value gap because normal success, schema persistence, and request idempotency are covered, while retry/DLQ/replay behavior has mostly unit coverage only.
