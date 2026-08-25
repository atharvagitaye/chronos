package dev.atharvagitaye.chronos.worker;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.queue.RabbitMqConfig;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "chronos.worker.enabled", havingValue = "true", matchIfMissing = true)
public class JobWorker {

	private final JobRepository jobRepository;
	private final SimulatedJobHandler simulatedJobHandler;

	public JobWorker(JobRepository jobRepository, SimulatedJobHandler simulatedJobHandler) {
		this.jobRepository = jobRepository;
		this.simulatedJobHandler = simulatedJobHandler;
	}

	@RabbitListener(queues = { RabbitMqConfig.HIGH_QUEUE, RabbitMqConfig.MEDIUM_QUEUE, RabbitMqConfig.LOW_QUEUE },
			concurrency = "${chronos.worker.concurrency:4}")
	@Transactional
	public void consume(JobMessage message) {
		UUID jobId = message.jobId();
		Job job = jobRepository.findForUpdate(jobId).orElse(null);
		if (job == null || job.getStatus() == dev.atharvagitaye.chronos.job.enums.JobStatus.SUCCESS) {
			return;
		}

		job.start();
		if (job.getStatus() != dev.atharvagitaye.chronos.job.enums.JobStatus.RUNNING) {
			return;
		}
		try {
			if (!"SIMULATED".equals(job.getJobType())) {
				throw new IllegalArgumentException("Unsupported job type: " + job.getJobType());
			}
			simulatedJobHandler.execute(job.getPayload());
			job.complete();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			job.fail("Job interrupted");
		} catch (RuntimeException exception) {
			job.fail(exception.getMessage());
		}
	}
}