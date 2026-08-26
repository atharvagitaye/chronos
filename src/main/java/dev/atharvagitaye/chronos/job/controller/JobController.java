package dev.atharvagitaye.chronos.job.controller;

import dev.atharvagitaye.chronos.job.dto.CreateJobRequest;
import dev.atharvagitaye.chronos.job.dto.JobResponse;
import dev.atharvagitaye.chronos.job.dto.JobSearchCriteria;
import dev.atharvagitaye.chronos.job.dto.PageResponse;
import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import dev.atharvagitaye.chronos.job.service.JobService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

	private final JobService jobService;

	public JobController(JobService jobService) {
		this.jobService = jobService;
	}

	@PostMapping
	public ResponseEntity<JobResponse> create(@Valid @RequestBody CreateJobRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
		JobResponse response = JobResponse.from(jobService.create(request, idempotencyKey));
		return ResponseEntity.created(URI.create("/api/v1/jobs/" + response.jobId())).body(response);
	}

	@GetMapping("/{jobId}")
	public JobResponse get(@PathVariable UUID jobId) {
		return JobResponse.from(jobService.get(jobId));
	}

	@GetMapping
	public PageResponse<JobResponse> list(
			@RequestParam(required = false) JobStatus status,
			@RequestParam(required = false) String jobType,
			@RequestParam(required = false) JobPriority priority,
			@RequestParam(required = false) Instant createdAfter,
			@RequestParam(required = false) Instant createdBefore,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
		JobSearchCriteria criteria = new JobSearchCriteria(status, jobType, priority, createdAfter, createdBefore);
		return PageResponse.from(jobService.list(criteria, pageRequest), JobResponse::from);
	}

	@PostMapping("/{jobId}/cancel")
	public JobResponse cancel(@PathVariable UUID jobId) {
		return JobResponse.from(jobService.cancel(jobId));
	}

	@PostMapping("/{jobId}/retry")
	public JobResponse retry(@PathVariable UUID jobId) {
		return JobResponse.from(jobService.retry(jobId));
	}
}