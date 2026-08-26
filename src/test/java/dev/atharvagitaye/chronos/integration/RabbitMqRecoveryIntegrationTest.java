package dev.atharvagitaye.chronos.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.atharvagitaye.chronos.job.dto.CreateJobRequest;
import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.job.service.JobService;
import dev.atharvagitaye.chronos.outbox.OutboxEvent;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.AmqpException;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"chronos.outbox.publisher.enabled=true",
		"chronos.outbox.publisher.interval-ms=100",
		"chronos.worker.enabled=true",
		"chronos.worker.concurrency=1"
})
class RabbitMqRecoveryIntegrationTest {

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

	@MockitoSpyBean
	private RabbitTemplate rabbitTemplate;

	@Test
	void outboxRemainsPendingWhenRabbitMqIsUnavailableAndPublishesAfterRecovery() throws Exception {
		// Simulate RabbitMQ being unavailable by throwing an exception on send
		doThrow(new AmqpException("Simulated connection failure"))
			.when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

		var request = new CreateJobRequest("SIMULATED", Map.of("durationMs", 0), JobPriority.HIGH, 1, null);
		var created = jobService.create(request, "rabbitmq-offline-key");

		// Verify outbox remains PENDING
		Thread.sleep(500); // Give publisher a chance to run and fail
		OutboxEvent event = outboxRepository.findAll().stream()
				.filter(e -> e.getAggregateId().equals(created.getId()))
				.findFirst()
				.orElseThrow();
		assertEquals(OutboxEvent.Status.PENDING, event.getStatus());

		// Recover RabbitMQ
		reset(rabbitTemplate);

		// Verify the job eventually completes
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			Job job = jobRepository.findById(created.getId()).orElseThrow();
			assertEquals(JobStatus.SUCCESS, job.getStatus());
		});
		
		OutboxEvent recoveredEvent = outboxRepository.findById(event.getId()).orElseThrow();
		assertEquals(OutboxEvent.Status.PUBLISHED, recoveredEvent.getStatus());
	}
}
