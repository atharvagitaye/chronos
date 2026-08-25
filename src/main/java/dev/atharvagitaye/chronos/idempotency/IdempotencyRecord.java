package dev.atharvagitaye.chronos.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

	@Id
	private UUID id;

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
	private String key;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "job_id", nullable = false)
	private UUID jobId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected IdempotencyRecord() { }

	public IdempotencyRecord(String key, String requestHash, UUID jobId) {
		this.id = UUID.randomUUID();
		this.key = key;
		this.requestHash = requestHash;
		this.jobId = jobId;
		this.createdAt = Instant.now();
	}

	public String getKey() { return key; }
	public String getRequestHash() { return requestHash; }
	public UUID getJobId() { return jobId; }
}