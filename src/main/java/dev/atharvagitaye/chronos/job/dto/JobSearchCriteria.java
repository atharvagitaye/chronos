package dev.atharvagitaye.chronos.job.dto;

import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import java.time.Instant;

public record JobSearchCriteria(
		JobStatus status,
		String jobType,
		JobPriority priority,
		Instant createdAfter,
		Instant createdBefore) {
}
