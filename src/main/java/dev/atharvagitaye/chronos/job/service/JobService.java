package dev.atharvagitaye.chronos.job.service;

import dev.atharvagitaye.chronos.job.dto.CreateJobRequest;
import dev.atharvagitaye.chronos.job.dto.JobSearchCriteria;
import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.outbox.OutboxEvent;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import dev.atharvagitaye.chronos.monitoring.MetricsService;
import dev.atharvagitaye.chronos.idempotency.IdempotencyRecord;
import dev.atharvagitaye.chronos.idempotency.IdempotencyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

	private final JobRepository jobRepository;
	private final OutboxRepository outboxRepository;
	private final MetricsService metricsService;
	private final IdempotencyRepository idempotencyRepository;

	public JobService(JobRepository jobRepository, OutboxRepository outboxRepository, MetricsService metricsService,
			IdempotencyRepository idempotencyRepository) {
		this.jobRepository = jobRepository;
		this.outboxRepository = outboxRepository;
		this.metricsService = metricsService;
		this.idempotencyRepository = idempotencyRepository;
	}

	@Transactional
	public Job create(CreateJobRequest request, String idempotencyKey) {
		String requestHash = requestHash(request);
		if (idempotencyKey != null && !idempotencyKey.isBlank()) {
			var existing = idempotencyRepository.findByKey(idempotencyKey);
			if (existing.isPresent()) {
				if (!existing.get().getRequestHash().equals(requestHash)) {
					throw new IdempotencyConflictException(idempotencyKey);
				}
				return get(existing.get().getJobId());
			}
		}
		int maxRetries = request.maxRetries() == null ? 3 : request.maxRetries();
		Job job = jobRepository.save(new Job(request.jobType(), request.payload(), request.priority(), maxRetries,
				request.scheduledAt()));
		if (job.getScheduledAt() == null || !job.getScheduledAt().isAfter(java.time.Instant.now())) {
			outboxRepository.save(new OutboxEvent(job.getId(), "JOB", "JOB_CREATED", messagePayload(job)));
		}
		if (idempotencyKey != null && !idempotencyKey.isBlank()) {
			try {
				idempotencyRepository.saveAndFlush(new IdempotencyRecord(idempotencyKey, requestHash, job.getId()));
			} catch (org.springframework.dao.DataIntegrityViolationException exception) {
				IdempotencyRecord existing = idempotencyRepository.findByKey(idempotencyKey).orElseThrow();
				if (!existing.getRequestHash().equals(requestHash)) {
					throw new IdempotencyConflictException(idempotencyKey);
				}
				return get(existing.getJobId());
			}
		}
		metricsService.submitted(job.getJobType(), job.getPriority().name());
		return job;
	}

	private String requestHash(CreateJobRequest request) {
		String canonical = request.jobType() + "|" + canonicalValue(request.payload()) + "|" + request.priority() + "|"
				+ request.maxRetries() + "|" + request.scheduledAt();
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String canonicalValue(Object value) {
		if (value instanceof Map<?, ?> map) {
			return map.entrySet().stream().sorted(java.util.Comparator.comparing(entry -> entry.getKey().toString()))
					.map(entry -> entry.getKey() + "=" + canonicalValue(entry.getValue()))
					.collect(Collectors.joining("{", ",", "}"));
		}
		if (value instanceof Collection<?> collection) {
			return collection.stream().map(this::canonicalValue).collect(Collectors.joining("[", ",", "]"));
		}
		return String.valueOf(value);
	}

	private Map<String, Object> messagePayload(Job job) {
		return Map.of("jobId", job.getId().toString(), "attempt", 0, "jobType", job.getJobType(),
				"priority", job.getPriority().name());
	}

	@Transactional(readOnly = true)
	public Job get(UUID jobId) {
		return jobRepository.findById(jobId)
				.orElseThrow(() -> new JobNotFoundException(jobId));
	}

	@Transactional(readOnly = true)
	public Page<Job> list(JobSearchCriteria criteria, Pageable pageable) {
		Specification<Job> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (criteria.status() != null) {
				predicates.add(cb.equal(root.get("status"), criteria.status()));
			}
			if (criteria.jobType() != null && !criteria.jobType().isBlank()) {
				predicates.add(cb.equal(root.get("jobType"), criteria.jobType()));
			}
			if (criteria.priority() != null) {
				predicates.add(cb.equal(root.get("priority"), criteria.priority()));
			}
			if (criteria.createdAfter() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.createdAfter()));
			}
			if (criteria.createdBefore() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), criteria.createdBefore()));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
		return jobRepository.findAll(spec, pageable);
	}

	@Transactional
	public Job cancel(UUID jobId) {
		Job job = jobRepository.findForUpdate(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		job.cancel();
		return job;
	}

	@Transactional
	public Job retry(UUID jobId) {
		Job job = jobRepository.findForUpdate(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		job.retryManually();
		outboxRepository.save(new OutboxEvent(job.getId(), "JOB", "JOB_REPLAYED", messagePayload(job)));
		return job;
	}

	public static class JobNotFoundException extends RuntimeException {
		public JobNotFoundException(UUID jobId) {
			super("Job not found: " + jobId);
		}
	}

	public static class IdempotencyConflictException extends RuntimeException {
		public IdempotencyConflictException(String key) {
			super("Idempotency key has already been used with a different request: " + key);
		}
	}
}