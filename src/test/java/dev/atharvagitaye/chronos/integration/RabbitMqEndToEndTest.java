package dev.atharvagitaye.chronos.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.atharvagitaye.chronos.dlq.DlqService;
import dev.atharvagitaye.chronos.job.dto.CreateJobRequest;
import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.job.service.JobService;
import dev.atharvagitaye.chronos.idempotency.IdempotencyRepository;
import dev.atharvagitaye.chronos.outbox.OutboxEvent;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import dev.atharvagitaye.chronos.queue.RabbitMqConfig;
import dev.atharvagitaye.chronos.worker.JobExecution;
import dev.atharvagitaye.chronos.worker.JobExecutionRepository;
import dev.atharvagitaye.chronos.worker.JobMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"chronos.outbox.publisher.enabled=true",
		"chronos.outbox.publisher.interval-ms=50",
		"chronos.worker.enabled=true",
		"chronos.worker.concurrency=1",
		"chronos.scheduler.interval-ms=100",
		"chronos.worker.lease-recovery-interval-ms=100",
		"chronos.retry.base-delay-ms=100",
		"chronos.retry.max-delay-ms=200"
})
class RabbitMqEndToEndTest {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Container
	static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4-management-alpine");

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	@DynamicPropertySource
	static void configureInfrastructure(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.rabbitmq.host", rabbitmq::getHost);
		registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
		registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
		registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
	}

	@Autowired
	private JobService jobService;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private OutboxRepository outboxRepository;

	@Autowired
	private JobExecutionRepository jobExecutionRepository;

	@Autowired
	private IdempotencyRepository idempotencyRepository;

	@Autowired
	private DlqService dlqService;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private JdbcClient jdbcClient;

	@BeforeEach
	void cleanState() {
		idempotencyRepository.deleteAll();
		jobExecutionRepository.deleteAll();
		outboxRepository.deleteAll();
		jobRepository.deleteAll();
		Stream.of(
				RabbitMqConfig.HIGH_QUEUE,
				RabbitMqConfig.MEDIUM_QUEUE,
				RabbitMqConfig.LOW_QUEUE,
				RabbitMqConfig.HIGH_RETRY_QUEUE,
				RabbitMqConfig.MEDIUM_RETRY_QUEUE,
				RabbitMqConfig.LOW_RETRY_QUEUE,
				RabbitMqConfig.DLQ)
			.forEach(queue -> rabbitTemplate.execute(channel -> {
				channel.queuePurge(queue);
				return null;
			}));
	}

	@Test
	void publishesAndExecutesJobThroughRabbitMq() {
		var request = new CreateJobRequest("SIMULATED", Map.of("durationMs", 0), JobPriority.HIGH, 1, null);
		var created = jobService.create(request, "rabbitmq-e2e-key");

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			assertEquals(JobStatus.SUCCESS, jobRepository.findById(created.getId()).orElseThrow().getStatus());
		});

		OutboxEvent event = outboxRepository.findAll().stream().findFirst().orElseThrow();
		assertEquals(OutboxEvent.Status.PUBLISHED, event.getStatus());
	}

	@Test
	void retriesFailOnceJobAndEventuallySucceeds() {
		var request = new CreateJobRequest("SIMULATED", Map.of("durationMs", 0, "failureMode", "FAIL_ONCE"),
				JobPriority.HIGH, 2, null);
		var created = jobService.create(request, "retry-success-key");

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			Job job = jobRepository.findById(created.getId()).orElseThrow();
			assertEquals(JobStatus.SUCCESS, job.getStatus());
			assertEquals(1, job.getRetryCount());
		});

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			List<JobExecution> executions = executionsFor(created.getId());
			assertEquals(2, executions.size());
			assertTrue(executions.stream().anyMatch(execution ->
					execution.getAttemptNumber() == 0 && execution.getStatus() == JobExecution.Status.FAILED));
			assertTrue(executions.stream().anyMatch(execution ->
					execution.getAttemptNumber() == 1 && execution.getStatus() == JobExecution.Status.SUCCESS));
		});
	}

	@Test
	void sendsExhaustedJobToDlq() {
		var request = new CreateJobRequest("SIMULATED", Map.of("durationMs", 0, "failureMode", "ALWAYS_FAIL"),
				JobPriority.HIGH, 2, null);
		var created = jobService.create(request, "dlq-key");

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			Job job = jobRepository.findById(created.getId()).orElseThrow();
			assertEquals(JobStatus.DLQ, job.getStatus());
			assertEquals(2, job.getRetryCount());
			assertEquals("Simulated transient failure", job.getLastError());
		});

		assertEquals(3, executionsFor(created.getId()).size());
	}

	@Test
	void replaysDlqJobAfterPayloadCorrection() {
		var request = new CreateJobRequest("SIMULATED", Map.of("durationMs", 0, "failureMode", "ALWAYS_FAIL"),
				JobPriority.HIGH, 0, null);
		var created = jobService.create(request, "dlq-replay-key");

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				assertEquals(JobStatus.DLQ, jobRepository.findById(created.getId()).orElseThrow().getStatus()));

		jdbcClient.sql("update jobs set payload = cast(:payload as jsonb) where id = :jobId")
				.param("payload", "{\"durationMs\":0}")
				.param("jobId", created.getId())
				.update();

		dlqService.replay(created.getId());

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			Job job = jobRepository.findById(created.getId()).orElseThrow();
			assertEquals(JobStatus.SUCCESS, job.getStatus());
			assertEquals(0, job.getRetryCount());
		});

		assertTrue(outboxRepository.findAll().stream().anyMatch(event ->
				event.getAggregateId().equals(created.getId())
						&& "JOB_REPLAYED".equals(event.getEventType())
						&& event.getStatus() == OutboxEvent.Status.PUBLISHED));
	}

	@Test
	void ignoresDuplicateDeliveryAfterClaim() {
		var request = new CreateJobRequest("SIMULATED", Map.of("durationMs", 0), JobPriority.HIGH, 1, null);
		var created = jobService.create(request, "duplicate-delivery-key");
		JobMessage duplicate = new JobMessage(created.getId(), 0, "SIMULATED", "HIGH");

		rabbitTemplate.convertAndSend(RabbitMqConfig.JOB_EXCHANGE, "job.high", duplicate);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				assertEquals(JobStatus.SUCCESS, jobRepository.findById(created.getId()).orElseThrow().getStatus()));

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(1, executionsFor(created.getId()).size()));
	}

	@Test
	void doesNotExecuteScheduledJobBeforeItsScheduledTime() throws InterruptedException {
		Instant scheduledAt = Instant.now().plusSeconds(2);
		var request = new CreateJobRequest("SIMULATED", Map.of("durationMs", 0), JobPriority.MEDIUM, 1, scheduledAt);
		var created = jobService.create(request, "scheduled-key");

		Thread.sleep(500);

		Job beforeDue = jobRepository.findById(created.getId()).orElseThrow();
		assertEquals(JobStatus.CREATED, beforeDue.getStatus());
		assertFalse(jobExecutionRepository.existsByJobIdAndAttemptNumber(created.getId(), 0));

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			Job job = jobRepository.findById(created.getId()).orElseThrow();
			assertEquals(JobStatus.SUCCESS, job.getStatus());
			assertTrue(!job.getStartedAt().isBefore(scheduledAt));
		});
	}

	@Test
	void recoversExpiredLeaseByPublishingANewAttempt() {
		Job job = jobRepository.save(new Job("SIMULATED", Map.of("durationMs", 0), JobPriority.LOW, 1,
				Instant.now().plusSeconds(60)));
		Instant expiredAt = Instant.now().minusSeconds(5);

		jdbcClient.sql("""
				update jobs
				set status = 'RUNNING',
				    retry_count = 0,
				    started_at = :expiredAt,
				    updated_at = :expiredAt,
				    locked_by = 'stale-worker',
				    lease_until = :expiredAt
				where id = :jobId
				""")
				.param("expiredAt", java.sql.Timestamp.from(expiredAt))
				.param("jobId", job.getId())
				.update();

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			Job recovered = jobRepository.findById(job.getId()).orElseThrow();
			assertEquals(JobStatus.SUCCESS, recovered.getStatus());
		});

		assertTrue(jobExecutionRepository.existsByJobIdAndAttemptNumber(job.getId(), 1));
		assertTrue(outboxRepository.findAll().stream().anyMatch(event ->
				event.getAggregateId().equals(job.getId()) && "JOB_RECOVERED".equals(event.getEventType())));
	}

	private List<JobExecution> executionsFor(UUID jobId) {
		return jobExecutionRepository.findAll().stream()
				.filter(execution -> execution.getJobId().equals(jobId))
				.toList();
	}
}