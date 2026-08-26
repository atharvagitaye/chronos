# ADR-003: RabbitMQ with at-least-once delivery

RabbitMQ was chosen for its durable work queues, acknowledgements, TTL/DLX delayed retry pattern, and simple operational model for this project. Chronos explicitly offers at-least-once delivery, not exactly-once delivery. Execution claims prevent duplicate handler invocation for the same stored attempt, while external handlers remain responsible for idempotent side effects.
