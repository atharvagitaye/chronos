# ADR-006: Use database-backed request and execution idempotency

`Idempotency-Key` values map a request fingerprint to one job ID in PostgreSQL, making client retries safe and rejecting key reuse with different input. Worker execution claims use a unique `(job_id, attempt_number)` constraint. Both mechanisms are durable and remain effective across process restarts.
