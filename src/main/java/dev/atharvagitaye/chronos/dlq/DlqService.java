package dev.atharvagitaye.chronos.dlq;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.outbox.OutboxEvent;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import dev.atharvagitaye.chronos.worker.JobExecutionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DlqService {

	private final JobRepository jobRepository;
	private final OutboxRepository outboxRepository;
	private final JobExecutionRepository jobExecutionRepository;

	public DlqService(JobRepository jobRepository, OutboxRepository outboxRepository, JobExecutionRepository jobExecutionRepository) {
		this.jobRepository = jobRepository;
		this.outboxRepository = outboxRepository;
		this.jobExecutionRepository = jobExecutionRepository;
	}

	@Transactional(readOnly = true)
	public List<Job> list(Pageable pageable) {
		return jobRepository.findByStatus(dev.atharvagitaye.chronos.job.enums.JobStatus.DLQ, pageable).getContent();
	}

	@Transactional
	public Job replay(UUID jobId) {
		Job job = jobRepository.findForUpdate(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		job.replay();
		jobExecutionRepository.deleteByJobId(job.getId());
		outboxRepository.save(new OutboxEvent(job.getId(), "JOB", "JOB_REPLAYED", Map.of(
				"jobId", job.getId().toString(), "attempt", 0, "jobType", job.getJobType(),
				"priority", job.getPriority().name())));
		return job;
	}

	public static class JobNotFoundException extends RuntimeException {
		public JobNotFoundException(UUID jobId) {
			super("Job not found: " + jobId);
		}
	}
}