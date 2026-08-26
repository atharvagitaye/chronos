# ADR-002: Use a transactional outbox

Job creation, scheduling, replay, and lease recovery write an outbox event in the same database transaction as the state change. A separate publisher sends pending events to RabbitMQ and marks them published only after the send succeeds. This avoids the database-versus-broker dual-write failure, while accepting at-least-once publication.
