package dev.atharvagitaye.chronos.job.service;

import dev.atharvagitaye.chronos.job.dto.CreateJobRequest;
import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.outbox.OutboxEvent;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

	private final JobRepository jobRepository;
	private final OutboxRepository outboxRepository;

	public JobService(JobRepository jobRepository, OutboxRepository outboxRepository) {
		this.jobRepository = jobRepository;
		this.outboxRepository = outboxRepository;
	}

	@Transactional
	public Job create(CreateJobRequest request) {
		int maxRetries = request.maxRetries() == null ? 3 : request.maxRetries();
		Job job = jobRepository.save(new Job(request.jobType(), request.payload(), request.priority(), maxRetries,
				request.scheduledAt()));
		outboxRepository.save(new OutboxEvent(job.getId(), "JOB", "JOB_CREATED", Map.of(
				"jobId", job.getId().toString(), "attempt", 0, "jobType", job.getJobType(),
				"priority", job.getPriority().name())));
		return job;
	}

	@Transactional(readOnly = true)
	public Job get(UUID jobId) {
		return jobRepository.findById(jobId)
				.orElseThrow(() -> new JobNotFoundException(jobId));
	}

	@Transactional(readOnly = true)
	public List<Job> list(Pageable pageable) {
		return jobRepository.findAll(pageable).getContent();
	}

	public static class JobNotFoundException extends RuntimeException {
		public JobNotFoundException(UUID jobId) {
			super("Job not found: " + jobId);
		}
	}
}