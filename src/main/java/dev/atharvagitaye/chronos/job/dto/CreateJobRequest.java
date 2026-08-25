package dev.atharvagitaye.chronos.job.dto;

import dev.atharvagitaye.chronos.job.enums.JobPriority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public record CreateJobRequest(
		@NotBlank String jobType,
		@NotNull Map<String, Object> payload,
		@NotNull JobPriority priority,
		@Min(0) @Max(10) Integer maxRetries,
		Instant scheduledAt) {
}