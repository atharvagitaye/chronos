package dev.atharvagitaye.chronos.outbox;

import dev.atharvagitaye.chronos.queue.RabbitMqConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "chronos.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

	private final OutboxRepository outboxRepository;
	private final RabbitTemplate rabbitTemplate;

	public OutboxPublisher(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate) {
		this.outboxRepository = outboxRepository;
		this.rabbitTemplate = rabbitTemplate;
	}

	@Scheduled(fixedDelayString = "${chronos.outbox.publisher.interval-ms:1000}")
	@Transactional
	public void publishPending() {
		List<OutboxEvent> events = outboxRepository.findPending(Instant.now(), PageRequest.of(0, 50));
		for (OutboxEvent event : events) {
			try {
				rabbitTemplate.convertAndSend(RabbitMqConfig.JOB_EXCHANGE, routingKey(event), event.getPayload());
				event.markPublished(Instant.now());
			} catch (RuntimeException exception) {
				event.recordFailure(nextAttempt(event.getAttempts()));
			}
		}
	}

	private String routingKey(OutboxEvent event) {
		return "job." + event.getPayload().get("priority").toString().toLowerCase();
	}

	private Instant nextAttempt(int attempts) {
		long delaySeconds = Math.min(60, 1L << Math.min(attempts, 6));
		return Instant.now().plus(Duration.ofSeconds(delaySeconds));
	}
}