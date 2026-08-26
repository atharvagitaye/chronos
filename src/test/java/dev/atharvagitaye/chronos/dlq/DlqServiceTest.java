package dev.atharvagitaye.chronos.dlq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.outbox.OutboxEvent;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import dev.atharvagitaye.chronos.worker.JobExecutionRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DlqServiceTest {

	@Mock
	private JobRepository jobRepository;

	@Mock
	private OutboxRepository outboxRepository;

	@Test
	void replaysDlqJobThroughOutbox() {
		Job job = new Job("SIMULATED", Map.of(), JobPriority.MEDIUM, 3, null);
		job.moveToDlq("failed");
		when(jobRepository.findForUpdate(job.getId())).thenReturn(Optional.of(job));

		JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
		Job replayed = new DlqService(jobRepository, outboxRepository, jobExecutionRepository).replay(job.getId());

		assertEquals(JobStatus.CREATED, replayed.getStatus());
		assertEquals(0, replayed.getRetryCount());
		verify(outboxRepository).save(org.mockito.ArgumentMatchers.any());
	}
}