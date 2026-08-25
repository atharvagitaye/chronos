package dev.atharvagitaye.chronos.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class LeaseRecoveryServiceTest {

	@Mock
	private JobRepository jobRepository;

	@Mock
	private OutboxRepository outboxRepository;

	@Test
	void recoversExpiredRunningJobThroughOutbox() {
		Job job = new Job("SIMULATED", Map.of(), JobPriority.HIGH, 3, null);
		job.start("worker-test", 0);
		when(jobRepository.findExpiredLeases(any(Instant.class), any(Pageable.class))).thenReturn(List.of(job));

		new LeaseRecoveryService(jobRepository, outboxRepository).recoverExpiredLeases();

		assertEquals(JobStatus.QUEUED, job.getStatus());
		verify(outboxRepository).save(any());
	}
}