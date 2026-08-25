CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    job_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    max_retries INTEGER NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT jobs_max_retries_check CHECK (max_retries BETWEEN 0 AND 10),
    CONSTRAINT jobs_retry_count_check CHECK (retry_count >= 0),
    CONSTRAINT jobs_status_check CHECK (status IN ('CREATED', 'QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'RETRYING', 'DLQ', 'CANCELLED')),
    CONSTRAINT jobs_priority_check CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX jobs_status_idx ON jobs (status);
CREATE INDEX jobs_scheduled_at_idx ON jobs (scheduled_at);
CREATE INDEX jobs_priority_idx ON jobs (priority);
CREATE INDEX jobs_created_at_idx ON jobs (created_at);