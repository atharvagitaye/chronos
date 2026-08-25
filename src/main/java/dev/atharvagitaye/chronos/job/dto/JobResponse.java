package dev.atharvagitaye.chronos.job.dto;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JobResponse(UUID jobId, String jobType, Map<String, Object> payload, JobStatus status,
		JobPriority priority, int maxRetries, int retryCount, Instant scheduledAt, Instant createdAt,
		Instant updatedAt, Instant startedAt, Instant completedAt, String lastError) {

	public static JobResponse from(Job job) {
		return new JobResponse(job.getId(), job.getJobType(), job.getPayload(), job.getStatus(), job.getPriority(),
				job.getMaxRetries(), job.getRetryCount(), job.getScheduledAt(), job.getCreatedAt(), job.getUpdatedAt(),
				job.getStartedAt(), job.getCompletedAt(), job.getLastError());
	}
}