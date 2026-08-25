package dev.atharvagitaye.chronos.worker;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.outbox.OutboxEvent;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

@Service
@ConditionalOnProperty(name = "chronos.worker.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'${chronos.mode:all}' == 'all' or '${chronos.mode:all}' == 'worker'")
public class LeaseRecoveryService {

	private final JobRepository jobRepository;
	private final OutboxRepository outboxRepository;

	public LeaseRecoveryService(JobRepository jobRepository, OutboxRepository outboxRepository) {
		this.jobRepository = jobRepository;
		this.outboxRepository = outboxRepository;
	}

	@Scheduled(fixedDelayString = "${chronos.worker.lease-recovery-interval-ms:10000}")
	@Transactional
	public void recoverExpiredLeases() {
		List<Job> jobs = jobRepository.findExpiredLeases(Instant.now(), PageRequest.of(0, 50));
		for (Job job : jobs) {
			job.recover();
			outboxRepository.save(new OutboxEvent(job.getId(), "JOB", "JOB_RECOVERED", Map.of(
					"jobId", job.getId().toString(), "attempt", job.getRetryCount() + 1,
					"jobType", job.getJobType(), "priority", job.getPriority().name())));
		}
	}
}