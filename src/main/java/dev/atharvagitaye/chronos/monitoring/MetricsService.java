package dev.atharvagitaye.chronos.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

	private final MeterRegistry meterRegistry;

	public MetricsService(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
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

	public void processingTime(String jobType, Duration duration) {
		meterRegistry.timer("chronos_job_processing_seconds", "job_type", jobType).record(duration);
	}

	private void counter(String name, String jobType, String priority, String outcome) {
		meterRegistry.counter(name, "job_type", jobType, "priority", priority, "outcome", outcome).increment();
	}
}