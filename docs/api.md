# Chronos API Reference

Base path: `/api/v1`. JSON request and response bodies use `application/json`. A machine-readable contract is available in [openapi.yaml](openapi.yaml).

## Jobs

| Method and path | Purpose | Success response |
| --- | --- | --- |
| `POST /jobs` | Create a job. Optional `Idempotency-Key` makes resubmission safe. | `201 Created`, `Location`, job response |
| `GET /jobs/{jobId}` | Get one job. | `200 OK`, job response |
| `GET /jobs` | List jobs with filters and pagination. | `200 OK`, page response |
| `POST /jobs/{jobId}/cancel` | Cancel an eligible job. | `200 OK`, job response |
| `POST /jobs/{jobId}/retry` | Manually retry an eligible job. | `200 OK`, job response |
| `GET /dlq/jobs` | List dead-lettered jobs. | `200 OK`, list of job responses |
| `POST /dlq/jobs/{jobId}/replay` | Reset a DLQ job and enqueue it through the outbox. | `200 OK`, job response |

Create request:

```json
{
  "jobType": "SIMULATED",
  "payload": { "durationMs": 1000, "failureMode": "NONE" },
  "priority": "HIGH",
  "maxRetries": 3,
  "scheduledAt": "2026-08-26T12:00:00Z"
}
```

`jobType`, `payload`, and `priority` are required. `maxRetries` is from `0` through `10` and defaults to `3` when omitted. `scheduledAt` is optional. For `SIMULATED`, `failureMode` may be `NONE`, `FAIL_ONCE`, or `ALWAYS_FAIL`.

List filters are `status`, `jobType`, `priority`, `createdAfter`, `createdBefore`, `page` (default `0`), and `size` (default `20`, capped at `100`). List responses contain `content`, `page`, `size`, `totalElements`, and `totalPages`.

Each job response includes `jobId`, `jobType`, `payload`, `status`, `priority`, retry values, schedule and lifecycle timestamps, and `lastError`.

Cancellation is rejected only for `SUCCESS` and already `CANCELLED` jobs. Manual retry is accepted only from `FAILED`, `CANCELLED`, and `DLQ`; it resets the retry counter and emits a new outbox event.

## Errors

Errors use this shape:

```json
{
  "timestamp": "2026-08-26T12:00:00Z",
  "status": 409,
  "error": "INVALID_JOB_STATE",
  "message": "..."
}
```

| HTTP status | Error code | Meaning |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | The create request failed validation. |
| `404` | `JOB_NOT_FOUND` | No job exists for the requested ID. |
| `409` | `IDEMPOTENCY_CONFLICT` | An idempotency key was reused with a different request. |
| `409` | `INVALID_JOB_STATE` | The requested transition is not legal for the job's current state. |
