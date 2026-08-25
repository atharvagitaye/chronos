ALTER TABLE jobs ADD COLUMN locked_by VARCHAR(150);
ALTER TABLE jobs ADD COLUMN lease_until TIMESTAMP WITH TIME ZONE;

CREATE INDEX jobs_lease_expiry_idx ON jobs (status, lease_until);