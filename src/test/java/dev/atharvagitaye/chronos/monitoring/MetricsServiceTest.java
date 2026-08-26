package dev.atharvagitaye.chronos.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MetricsServiceTest {

	@Test
	void recordsWorkerActivityAndLowCardinalityJobMetrics() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		MetricsService metrics = new MetricsService(registry);

		metrics.workerStarted();
		metrics.submitted("SIMULATED", "HIGH");
		metrics.waitTime("SIMULATED", "HIGH", Instant.now().minusMillis(10));
		metrics.processingTime("SIMULATED", Duration.ofMillis(5));
		metrics.workerFinished();

		assertEquals(0, metrics.activeWorkers());
		assertEquals(1.0, registry.get("chronos_jobs_submitted_total")
				.tags("job_type", "SIMULATED", "priority", "HIGH", "outcome", "submitted")
				.counter().count());
		assertEquals(1L, registry.get("chronos_job_wait_seconds")
				.tags("job_type", "SIMULATED", "priority", "HIGH").timer().count());
		assertEquals(1L, registry.get("chronos_job_processing_seconds")
				.tag("job_type", "SIMULATED").timer().count());
	}
}
