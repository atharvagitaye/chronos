package dev.atharvagitaye.chronos.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
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

	@Test
	void processesSimulatedJobAndMarksItSuccessful() throws Exception {
		Job job = new Job("SIMULATED", Map.of("durationMs", 0), JobPriority.HIGH, 3, null);
		UUID jobId = job.getId();
		when(jobRepository.findForUpdate(jobId)).thenReturn(Optional.of(job));

		new JobWorker(jobRepository, simulatedJobHandler).consume(new JobMessage(jobId, 0, "SIMULATED", "HIGH"));

		assertEquals(JobStatus.SUCCESS, job.getStatus());
		verify(simulatedJobHandler).execute(job.getPayload());
	}
}