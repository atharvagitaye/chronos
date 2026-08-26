package dev.atharvagitaye.chronos.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.retry.RetryStrategy;
import dev.atharvagitaye.chronos.retry.RetryableException;
import dev.atharvagitaye.chronos.monitoring.MetricsService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobWorkerTest {

	@Mock
	private JobRepository jobRepository;

	@Mock
	private SimulatedJobHandler simulatedJobHandler;

	@Mock
	private RabbitTemplate rabbitTemplate;

	@Mock
	private JobExecutionService executionService;

	@Mock
	private MetricsService metricsService;

	@Test
	void processesSimulatedJobAndMarksItSuccessful() throws Exception {
		Job job = new Job("SIMULATED", Map.of("durationMs", 0), JobPriority.HIGH, 3, null);
		UUID jobId = job.getId();
		when(jobRepository.findForUpdate(jobId)).thenReturn(Optional.of(job));

		when(executionService.claim(jobId, 0)).thenReturn(true);
		new JobWorker(jobRepository, simulatedJobHandler, rabbitTemplate, new RetryStrategy(2000, 30000), executionService, metricsService)
				.consume(new JobMessage(jobId, 0, "SIMULATED", "HIGH"));

		assertEquals(JobStatus.SUCCESS, job.getStatus());
		org.junit.jupiter.api.Assertions.assertEquals(null, job.getLeaseUntil());
		verify(simulatedJobHandler).execute(job.getPayload(), 0);
	}

	@Test
	void schedulesRetryForRetryableFailure() throws Exception {
		Job job = new Job("SIMULATED", Map.of(), JobPriority.HIGH, 3, null);
		when(jobRepository.findForUpdate(job.getId())).thenReturn(Optional.of(job));
		doThrow(new RetryableException("temporary failure")).when(simulatedJobHandler).execute(job.getPayload(), 0);

		when(executionService.claim(job.getId(), 0)).thenReturn(true);
		new JobWorker(jobRepository, simulatedJobHandler, rabbitTemplate, new RetryStrategy(2000, 30000), executionService, metricsService)
				.consume(new JobMessage(job.getId(), 0, "SIMULATED", "HIGH"));

		assertEquals(JobStatus.RETRYING, job.getStatus());
		assertEquals(1, job.getRetryCount());
		verify(rabbitTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("chronos.high.retry.queue"),
				org.mockito.ArgumentMatchers.any(JobMessage.class),
				org.mockito.ArgumentMatchers.any(MessagePostProcessor.class));
	}

	@Test
	void sendsExhaustedRetryToDlq() throws Exception {
		Job job = new Job("SIMULATED", Map.of(), JobPriority.LOW, 0, null);
		when(jobRepository.findForUpdate(job.getId())).thenReturn(Optional.of(job));
		doThrow(new RetryableException("permanent failure")).when(simulatedJobHandler).execute(job.getPayload(), 0);

		when(executionService.claim(job.getId(), 0)).thenReturn(true);
		new JobWorker(jobRepository, simulatedJobHandler, rabbitTemplate, new RetryStrategy(2000, 30000), executionService, metricsService)
				.consume(new JobMessage(job.getId(), 0, "SIMULATED", "LOW"));

		assertEquals(JobStatus.DLQ, job.getStatus());
		verify(rabbitTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("chronos.dlq"),
				org.mockito.ArgumentMatchers.any(JobMessage.class));
		verify(metricsService).dlq("SIMULATED", "LOW");
	}

	@Test
	void skipsDuplicateDeliveryForTheSameAttempt() {
		Job job = new Job("SIMULATED", Map.of(), JobPriority.HIGH, 3, null);
		when(jobRepository.findForUpdate(job.getId())).thenReturn(Optional.of(job));
		when(executionService.claim(job.getId(), 0)).thenReturn(false);

		new JobWorker(jobRepository, simulatedJobHandler, rabbitTemplate, new RetryStrategy(2000, 30000), executionService, metricsService)
				.consume(new JobMessage(job.getId(), 0, "SIMULATED", "HIGH"));

		org.mockito.Mockito.verifyNoInteractions(simulatedJobHandler);
	}
}
