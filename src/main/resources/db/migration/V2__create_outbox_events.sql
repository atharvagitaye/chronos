CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT outbox_events_status_check CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX outbox_events_pending_idx ON outbox_events (status, next_attempt_at, created_at);

CREATE TABLE job_executions (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id),
    attempt_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    worker_id VARCHAR(150) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT job_executions_status_check CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    CONSTRAINT job_executions_attempt_check CHECK (attempt_number >= 0),
    CONSTRAINT job_executions_job_attempt_unique UNIQUE (job_id, attempt_number)
);

CREATE INDEX job_executions_job_idx ON job_executions (job_id);