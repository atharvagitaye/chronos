CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    job_id UUID NOT NULL REFERENCES jobs(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idempotency_records_job_idx ON idempotency_records (job_id);