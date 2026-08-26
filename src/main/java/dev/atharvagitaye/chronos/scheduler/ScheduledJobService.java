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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

@Service
@ConditionalOnExpression("'${chronos.mode:all}' == 'all' or '${chronos.mode:all}' == 'api'")
public class ScheduledJobService {

	private final JobRepository jobRepository;
	private final OutboxRepository outboxRepository;
	private final SchedulerLock schedulerLock;

	public ScheduledJobService(JobRepository jobRepository, OutboxRepository outboxRepository, SchedulerLock schedulerLock) {
		this.jobRepository = jobRepository;
		this.outboxRepository = outboxRepository;
		this.schedulerLock = schedulerLock;
	}

	@Scheduled(fixedDelayString = "${chronos.scheduler.interval-ms:1000}")
	@Transactional
	public void publishDueJobs() {
		// Acquire lock for slightly less than the interval to prevent concurrent runs on different nodes
		// but allow the next interval to acquire it.
		if (!schedulerLock.acquireLock("chronos:scheduler:lock", java.time.Duration.ofMillis(900))) {
			return; // Another instance holds the lock
		}

		List<Job> jobs = jobRepository.findDueScheduledJobs(Instant.now(), PageRequest.of(0, 50));
		for (Job job : jobs) {
			job.queue();
			outboxRepository.save(new OutboxEvent(job.getId(), "JOB", "JOB_SCHEDULED", Map.of(
					"jobId", job.getId().toString(), "attempt", 0, "jobType", job.getJobType(),
					"priority", job.getPriority().name())));
		}
	}
}