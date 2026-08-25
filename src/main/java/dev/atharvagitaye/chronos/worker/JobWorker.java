package dev.atharvagitaye.chronos.worker;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.queue.RabbitMqConfig;
import dev.atharvagitaye.chronos.retry.NonRetryableException;
import dev.atharvagitaye.chronos.retry.RetryStrategy;
import dev.atharvagitaye.chronos.retry.RetryableException;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import dev.atharvagitaye.chronos.monitoring.MetricsService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "chronos.worker.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'${chronos.mode:all}' == 'all' or '${chronos.mode:all}' == 'worker'")
public class JobWorker {

	private final JobRepository jobRepository;
	private final SimulatedJobHandler simulatedJobHandler;
	private final RabbitTemplate rabbitTemplate;
	private final RetryStrategy retryStrategy;
	private final JobExecutionService executionService;
	private final MetricsService metricsService;
	private final long leaseDurationMs = 60000;

	public JobWorker(JobRepository jobRepository, SimulatedJobHandler simulatedJobHandler, RabbitTemplate rabbitTemplate,
			RetryStrategy retryStrategy, JobExecutionService executionService, MetricsService metricsService) {
		this.jobRepository = jobRepository;
		this.simulatedJobHandler = simulatedJobHandler;
		this.rabbitTemplate = rabbitTemplate;
		this.retryStrategy = retryStrategy;
		this.executionService = executionService;
		this.metricsService = metricsService;
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
		int attemptNumber = message.attempt();
		if (!executionService.claim(jobId, attemptNumber)) {
			return;
		}

		job.start(executionService.workerId(), leaseDurationMs);
		if (job.getStatus() != dev.atharvagitaye.chronos.job.enums.JobStatus.RUNNING) {
			return;
		}
		try {
			Instant processingStartedAt = Instant.now();
			if (!"SIMULATED".equals(job.getJobType())) {
				throw new NonRetryableException("Unsupported job type: " + job.getJobType());
			}
			simulatedJobHandler.execute(job.getPayload(), message.attempt());
			job.complete();
			executionService.complete(jobId, attemptNumber);
			metricsService.completed(job.getJobType(), job.getPriority().name());
			metricsService.processingTime(job.getJobType(), Duration.between(processingStartedAt, Instant.now()));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			job.fail("Job interrupted");
			executionService.fail(jobId, attemptNumber, "Job interrupted");
			metricsService.failed(job.getJobType(), job.getPriority().name());
		} catch (RetryableException exception) {
			executionService.fail(jobId, attemptNumber, exception.getMessage());
			if (job.getRetryCount() >= job.getMaxRetries()) {
				moveToDlq(job, exception.getMessage());
				return;
			}
			job.retry(exception.getMessage());
			metricsService.retried(job.getJobType(), job.getPriority().name());
			rabbitTemplate.convertAndSend(retryQueue(job.getPriority().name()),
				new JobMessage(job.getId(), job.getRetryCount(), job.getJobType(), job.getPriority().name()), retryMessage -> {
					retryMessage.getMessageProperties().setExpiration(
							String.valueOf(retryStrategy.delayMs(job.getRetryCount())));
					return retryMessage;
				});
		} catch (NonRetryableException exception) {
			moveToDlq(job, exception.getMessage());
			executionService.fail(jobId, attemptNumber, exception.getMessage());
			metricsService.dlq(job.getJobType(), job.getPriority().name());
		} catch (RuntimeException exception) {
			job.fail(exception.getMessage());
			executionService.fail(jobId, attemptNumber, exception.getMessage());
			metricsService.failed(job.getJobType(), job.getPriority().name());
		}
	}

	private void moveToDlq(Job job, String error) {
		job.moveToDlq(error);
		rabbitTemplate.convertAndSend(RabbitMqConfig.DLQ,
				new JobMessage(job.getId(), job.getRetryCount(), job.getJobType(), job.getPriority().name()));
	}

	private String retryQueue(String priority) {
		return switch (priority) {
		case "HIGH" -> RabbitMqConfig.HIGH_RETRY_QUEUE;
		case "MEDIUM" -> RabbitMqConfig.MEDIUM_RETRY_QUEUE;
		default -> RabbitMqConfig.LOW_RETRY_QUEUE;
		};
	}
}