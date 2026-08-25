package dev.atharvagitaye.chronos.job.entity;

import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jobs")
public class Job {

	@Id
	private UUID id;

	@Column(name = "job_type", nullable = false, length = 100)
	private String jobType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private JobStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private JobPriority priority;

	@Column(name = "max_retries", nullable = false)
	private int maxRetries;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "scheduled_at")
	private Instant scheduledAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "last_error")
	private String lastError;

	@Column(name = "locked_by")
	private String lockedBy;

	@Column(name = "lease_until")
	private Instant leaseUntil;

	@Version
	@Column(nullable = false)
	private long version;

	protected Job() {
	}

	public Job(String jobType, Map<String, Object> payload, JobPriority priority, int maxRetries, Instant scheduledAt) {
		this.id = UUID.randomUUID();
		this.jobType = jobType;
		this.payload = payload;
		this.status = JobStatus.CREATED;
		this.priority = priority;
		this.maxRetries = maxRetries;
		this.retryCount = 0;
		this.scheduledAt = scheduledAt;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public void start(String workerId, long leaseDurationMs) {
		if (status != JobStatus.CREATED && status != JobStatus.QUEUED && status != JobStatus.RETRYING) {
			return;
		}
		status = JobStatus.RUNNING;
		startedAt = Instant.now();
		updatedAt = startedAt;
		lockedBy = workerId;
		leaseUntil = updatedAt.plusMillis(leaseDurationMs);
	}

	public void complete() {
		status = JobStatus.SUCCESS;
		completedAt = Instant.now();
		updatedAt = completedAt;
		clearLease();
	}

	public void fail(String error) {
		status = JobStatus.FAILED;
		lastError = error;
		updatedAt = Instant.now();
		clearLease();
	}

	public void retry(String error) {
		retryCount++;
		status = JobStatus.RETRYING;
		lastError = error;
		updatedAt = Instant.now();
		clearLease();
	}

	public void moveToDlq(String error) {
		status = JobStatus.DLQ;
		lastError = error;
		updatedAt = Instant.now();
		clearLease();
	}

	public void recover() {
		if (status == JobStatus.RUNNING && leaseUntil != null && leaseUntil.isBefore(Instant.now())) {
			status = JobStatus.QUEUED;
			updatedAt = Instant.now();
			clearLease();
		}
	}

	private void clearLease() {
		lockedBy = null;
		leaseUntil = null;
	}

	public void replay() {
		if (status != JobStatus.DLQ) {
			throw new IllegalStateException("Only DLQ jobs can be replayed");
		}
		status = JobStatus.CREATED;
		retryCount = 0;
		lastError = null;
		startedAt = null;
		completedAt = null;
		updatedAt = Instant.now();
	}

	public UUID getId() { return id; }
	public String getJobType() { return jobType; }
	public Map<String, Object> getPayload() { return payload; }
	public JobStatus getStatus() { return status; }
	public JobPriority getPriority() { return priority; }
	public int getMaxRetries() { return maxRetries; }
	public int getRetryCount() { return retryCount; }
	public Instant getScheduledAt() { return scheduledAt; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public Instant getStartedAt() { return startedAt; }
	public Instant getCompletedAt() { return completedAt; }
	public String getLastError() { return lastError; }
	public String getLockedBy() { return lockedBy; }
	public Instant getLeaseUntil() { return leaseUntil; }
	public long getVersion() { return version; }
}