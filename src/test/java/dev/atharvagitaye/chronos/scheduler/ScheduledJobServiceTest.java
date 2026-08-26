package dev.atharvagitaye.chronos.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.dto.CreateJobRequest;
import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.job.service.JobService;
import dev.atharvagitaye.chronos.monitoring.MetricsService;
import dev.atharvagitaye.chronos.idempotency.IdempotencyRepository;
import dev.atharvagitaye.chronos.outbox.OutboxEvent;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import static org.mockito.ArgumentMatchers.anyString;
import java.time.Duration;

@ExtendWith(MockitoExtension.class)
class ScheduledJobServiceTest {

	@Mock
	private JobRepository jobRepository;

	@Mock
	private OutboxRepository outboxRepository;

	@Mock
	private MetricsService metricsService;

	@Mock
	private IdempotencyRepository idempotencyRepository;

	@Mock
	private SchedulerLock schedulerLock;

	@Test
	void queuesDueJobsThroughOutbox() {
		Job job = new Job("SIMULATED", Map.of(), JobPriority.HIGH, 3, Instant.now().minusSeconds(1));
		when(jobRepository.findDueScheduledJobs(any(Instant.class), any(Pageable.class))).thenReturn(List.of(job));
		when(schedulerLock.acquireLock(anyString(), any(Duration.class))).thenReturn(true);

		new ScheduledJobService(jobRepository, outboxRepository, schedulerLock).publishDueJobs();

		assertEquals(dev.atharvagitaye.chronos.job.enums.JobStatus.QUEUED, job.getStatus());
		verify(outboxRepository).save(any(OutboxEvent.class));
	}

	@Test
	void futureJobIsNotPublishedImmediately() {
		when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
		CreateJobRequest request = new CreateJobRequest("SIMULATED", Map.of(), JobPriority.LOW, 3,
				Instant.now().plusSeconds(60));

		new JobService(jobRepository, outboxRepository, metricsService, idempotencyRepository).create(request, null);

		verify(outboxRepository, never()).save(any());
	}
}