CREATE TABLE IF NOT EXISTS job_executions (
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

CREATE INDEX IF NOT EXISTS job_executions_job_idx ON job_executions (job_id);