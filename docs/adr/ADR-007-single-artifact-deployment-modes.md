# ADR-007: One application artifact with explicit modes

The same JAR and image support `api`, `worker`, and `all` modes. API mode owns HTTP handling, outbox publication, and scheduling; worker mode consumes queues and runs handlers. This keeps local development simple while allowing independent API and worker scaling in Docker Compose and Kubernetes.
