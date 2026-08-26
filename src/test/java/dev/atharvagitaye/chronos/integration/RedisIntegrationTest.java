package dev.atharvagitaye.chronos.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.atharvagitaye.chronos.scheduler.SchedulerLock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"chronos.outbox.publisher.enabled=false",
		"chronos.worker.enabled=false",
		"chronos.scheduler.interval-ms=1000000" // prevent scheduled task from messing with our tests
})
class RedisIntegrationTest {

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void configureRedis(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
		
		// Use H2 in-memory for this test since we disable flyway and don't need real PG
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
	}

	@Autowired
	private SchedulerLock schedulerLock;

	@Test
	void shouldAcquireLockOnlyOnceWithinDuration() throws InterruptedException {
		String lockKey = "test:lock";
		Duration duration = Duration.ofSeconds(2);

		// First acquisition should succeed
		boolean acquired1 = schedulerLock.acquireLock(lockKey, duration);
		assertTrue(acquired1, "Should acquire lock successfully on first try");

		// Second acquisition immediately after should fail
		boolean acquired2 = schedulerLock.acquireLock(lockKey, duration);
		assertFalse(acquired2, "Should fail to acquire lock while it is still held");

		// Wait for lock to expire
		Thread.sleep(2100);

		// Third acquisition should succeed again
		boolean acquired3 = schedulerLock.acquireLock(lockKey, duration);
		assertTrue(acquired3, "Should acquire lock successfully after previous one expired");
	}
}
