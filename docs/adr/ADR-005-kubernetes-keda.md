# ADR-005: KEDA scales workers from RabbitMQ queue depth

The Kubernetes deployment runs API and worker modes from the same image. KEDA observes RabbitMQ priority queues and scales worker replicas within configured bounds. This is a demonstration configuration; deployment credentials, resource tuning, readiness, and KEDA compatibility must be validated per cluster.
