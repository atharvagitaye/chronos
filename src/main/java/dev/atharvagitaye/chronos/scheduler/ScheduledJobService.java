package dev.atharvagitaye.chronos.scheduler;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.outbox.OutboxEvent;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduledJobService {

	private final JobRepository jobRepository;
	private final OutboxRepository outboxRepository;

	public ScheduledJobService(JobRepository jobRepository, OutboxRepository outboxRepository) {
		this.jobRepository = jobRepository;
		this.outboxRepository = outboxRepository;
	}

	@Scheduled(fixedDelayString = "${chronos.scheduler.interval-ms:1000}")
	@Transactional
	public void publishDueJobs() {
		List<Job> jobs = jobRepository.findDueScheduledJobs(Instant.now(), PageRequest.of(0, 50));
		for (Job job : jobs) {
			job.queue();
			outboxRepository.save(new OutboxEvent(job.getId(), "JOB", "JOB_SCHEDULED", Map.of(
					"jobId", job.getId().toString(), "attempt", 0, "jobType", job.getJobType(),
					"priority", job.getPriority().name())));
		}
	}
}