package dev.atharvagitaye.chronos.worker;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.queue.RabbitMqConfig;
import dev.atharvagitaye.chronos.retry.NonRetryableException;
import dev.atharvagitaye.chronos.retry.RetryStrategy;
import dev.atharvagitaye.chronos.retry.RetryableException;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "chronos.worker.enabled", havingValue = "true", matchIfMissing = true)
public class JobWorker {

	private final JobRepository jobRepository;
	private final SimulatedJobHandler simulatedJobHandler;
	private final RabbitTemplate rabbitTemplate;
	private final RetryStrategy retryStrategy;

	public JobWorker(JobRepository jobRepository, SimulatedJobHandler simulatedJobHandler, RabbitTemplate rabbitTemplate,
			RetryStrategy retryStrategy) {
		this.jobRepository = jobRepository;
		this.simulatedJobHandler = simulatedJobHandler;
		this.rabbitTemplate = rabbitTemplate;
		this.retryStrategy = retryStrategy;
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
				throw new NonRetryableException("Unsupported job type: " + job.getJobType());
			}
			simulatedJobHandler.execute(job.getPayload(), message.attempt());
			job.complete();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			job.fail("Job interrupted");
		} catch (RetryableException exception) {
			if (job.getRetryCount() >= job.getMaxRetries()) {
				job.fail(exception.getMessage());
				return;
			}
			job.retry(exception.getMessage());
			rabbitTemplate.convertAndSend(retryQueue(job.getPriority().name()),
				new JobMessage(job.getId(), job.getRetryCount(), job.getJobType(), job.getPriority().name()), retryMessage -> {
					retryMessage.getMessageProperties().setExpiration(
							String.valueOf(retryStrategy.delayMs(job.getRetryCount())));
					return retryMessage;
				});
		} catch (NonRetryableException exception) {
			job.fail(exception.getMessage());
		} catch (RuntimeException exception) {
			job.fail(exception.getMessage());
		}
	}

	private String retryQueue(String priority) {
		return switch (priority) {
		case "HIGH" -> RabbitMqConfig.HIGH_RETRY_QUEUE;
		case "MEDIUM" -> RabbitMqConfig.MEDIUM_RETRY_QUEUE;
		default -> RabbitMqConfig.LOW_RETRY_QUEUE;
		};
	}
}