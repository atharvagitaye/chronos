# Chronos Operations and Failure Runbook

## Health and metrics

The API exposes Spring Boot Actuator at `/actuator/health` and Prometheus metrics at `/actuator/prometheus`. In the full Compose stack, Prometheus is available on port `9090` and Grafana on port `3000`. Queue depth should be monitored by priority, together with worker outcomes, job wait duration, retries, and DLQ growth. Do not add job IDs or payload data as metric labels.

## Failure scenarios

| Scenario | Expected behavior | Operator action |
| --- | --- | --- |
| RabbitMQ unavailable | The outbox event stays pending and is retried with backoff. | Restore RabbitMQ; inspect pending outbox events and publisher failures. |
| Worker crashes while running | The job remains `RUNNING` until its lease expires, then is requeued with a new attempt. | Check worker logs and lease-recovery activity; avoid shortening leases below normal job duration. |
| Retryable handler failure | The job is routed to its priority retry queue with delayed redelivery. | Inspect `lastError`, retry count, and retry queue depth. |
| Retry limit reached or non-retryable error | The job is marked `DLQ` and sent to `chronos.dlq`. | Correct the cause, then replay through `POST /api/v1/dlq/jobs/{jobId}/replay`. |
| Duplicate RabbitMQ delivery | Existing execution claim suppresses a second handler invocation. | Investigate broker or worker instability if duplicates become frequent. |
| Redis unavailable | Scheduler proceeds with PostgreSQL locking as a degraded mode. | Restore Redis; verify scheduling throughput and lock errors. |

## Local recovery checks

1. Check `docker compose ps` and the Actuator health endpoint.
2. Check RabbitMQ queue depth and the `chronos.dlq` queue in the management UI.
3. Query the job API for the affected job status, `lastError`, retry count, and timestamps.
4. Restore dependencies before replaying jobs; replay only after the underlying fault is corrected.
5. Use the load script only against an environment intended for load testing.

## Kubernetes scope

The manifests under `infrastructure/kubernetes` are a demonstration deployment. Replace the sample image and secret placeholders for the target cluster. Validate readiness, resource limits, RabbitMQ connectivity, and KEDA authentication on a real cluster before claiming production readiness.
