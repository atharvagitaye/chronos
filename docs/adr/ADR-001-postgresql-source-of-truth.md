# ADR-001: PostgreSQL is the source of truth

Chronos stores jobs, state transitions, execution claims, request idempotency records, and outbox events in PostgreSQL. RabbitMQ messages and Redis keys are coordination mechanisms only. This keeps recovery and inspection possible after broker or cache outages, at the cost of an asynchronous publishing step.
