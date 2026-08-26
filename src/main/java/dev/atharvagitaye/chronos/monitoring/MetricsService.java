package dev.atharvagitaye.chronos.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Gauge;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

	private final MeterRegistry meterRegistry;
	private final AtomicInteger activeWorkers = new AtomicInteger();

	public MetricsService(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
		Gauge.builder("chronos_worker_active", activeWorkers, AtomicInteger::get)
				.description("Number of jobs currently executing in this process")
				.register(meterRegistry);
	}

	public void submitted(String jobType, String priority) {
		counter("chronos_jobs_submitted_total", jobType, priority, "submitted");
	}

	public void completed(String jobType, String priority) {
		counter("chronos_jobs_completed_total", jobType, priority, "success");
	}

	public void failed(String jobType, String priority) {
		counter("chronos_jobs_failed_total", jobType, priority, "failed");
	}

	public void retried(String jobType, String priority) {
		counter("chronos_jobs_retried_total", jobType, priority, "retry");
	}

	public void dlq(String jobType, String priority) {
		counter("chronos_jobs_dlq_total", jobType, priority, "dlq");
	}

	public void cancelled(String jobType, String priority) {
		counter("chronos_jobs_cancelled_total", jobType, priority, "cancelled");
	}

	/**
	 * Records how long a job waited between being queued (outbox event created) and
	 * being picked up by a worker. Call this at the moment the worker starts the job.
	 */
	public void waitTime(String jobType, String priority, Instant createdAt) {
		Duration waited = Duration.between(createdAt, Instant.now());
		meterRegistry.timer("chronos_job_wait_seconds",
				Tags.of("job_type", jobType, "priority", priority)).record(waited);
	}

	public void processingTime(String jobType, Duration duration) {
		meterRegistry.timer("chronos_job_processing_seconds", "job_type", jobType).record(duration);
	}

	public void workerStarted() {
		activeWorkers.incrementAndGet();
	}

	public void workerFinished() {
		activeWorkers.decrementAndGet();
	}

	public int activeWorkers() {
		return activeWorkers.get();
	}

	private void counter(String name, String jobType, String priority, String outcome) {
		meterRegistry.counter(name, "job_type", jobType, "priority", priority, "outcome", outcome).increment();
	}
}