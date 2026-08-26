package dev.atharvagitaye.chronos.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.atharvagitaye.chronos.idempotency.IdempotencyRepository;
import dev.atharvagitaye.chronos.job.entity.Job;
import dev.atharvagitaye.chronos.job.enums.JobPriority;
import dev.atharvagitaye.chronos.job.repository.JobRepository;
import dev.atharvagitaye.chronos.monitoring.MetricsService;
import dev.atharvagitaye.chronos.outbox.OutboxRepository;
import dev.atharvagitaye.chronos.job.service.JobService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"chronos.outbox.publisher.enabled=false",
		"chronos.worker.enabled=false"
})
class PostgresIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private JobService jobService;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private OutboxRepository outboxRepository;

	@Autowired
	private IdempotencyRepository idempotencyRepository;

	@MockitoBean
	private StringRedisTemplate redisTemplate;

	@Test
	void appliesMigrationsAndPersistsJobOutboxAndIdempotencyRecord() {
		Job job = jobService.create(new dev.atharvagitaye.chronos.job.dto.CreateJobRequest(
				"SIMULATED", Map.of("durationMs", 0), JobPriority.HIGH, 2, null), "postgres-integration-key");

		assertTrue(jobRepository.existsById(job.getId()));
		assertEquals(1, outboxRepository.count());
		assertEquals(1, idempotencyRepository.count());
	}
}