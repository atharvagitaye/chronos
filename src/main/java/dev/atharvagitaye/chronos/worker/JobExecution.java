package dev.atharvagitaye.chronos.worker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_executions")
public class JobExecution {

	public enum Status { RUNNING, SUCCESS, FAILED }

	@Id
	private UUID id;

	@Column(name = "job_id", nullable = false)
	private UUID jobId;

	@Column(name = "attempt_number", nullable = false)
	private int attemptNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	@Column(name = "worker_id", nullable = false, length = 150)
	private String workerId;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "error")
	private String error;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected JobExecution() { }

	public JobExecution(UUID jobId, int attemptNumber, String workerId) {
		this.id = UUID.randomUUID();
		this.jobId = jobId;
		this.attemptNumber = attemptNumber;
		this.workerId = workerId;
		this.status = Status.RUNNING;
		this.startedAt = Instant.now();
		this.createdAt = this.startedAt;
	}

	public void complete() {
		status = Status.SUCCESS;
		completedAt = Instant.now();
	}

	public void fail(String error) {
		status = Status.FAILED;
		this.error = error;
		completedAt = Instant.now();
	}

	public UUID getId() { return id; }
	public UUID getJobId() { return jobId; }
	public int getAttemptNumber() { return attemptNumber; }
	public Status getStatus() { return status; }
	public String getWorkerId() { return workerId; }
	public Instant getStartedAt() { return startedAt; }
	public Instant getCompletedAt() { return completedAt; }
	public String getError() { return error; }
}