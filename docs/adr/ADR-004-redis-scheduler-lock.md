# ADR-004: Redis is scheduler coordination only

Redis provides a short-lived `SET NX` lock so only one API instance normally runs a scheduler tick. If Redis is unavailable, Chronos continues using PostgreSQL pessimistic locking as the correctness backstop. Redis is not used for job persistence, submission idempotency, or execution state.
