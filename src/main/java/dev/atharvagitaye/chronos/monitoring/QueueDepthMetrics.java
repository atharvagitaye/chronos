package dev.atharvagitaye.chronos.monitoring;

import dev.atharvagitaye.chronos.queue.RabbitMqConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls RabbitMQ via RabbitAdmin every 5 seconds to expose per-priority queue depth
 * as a Micrometer Gauge (chronos_queue_depth{priority="high"|"medium"|"low"|"dlq"}).
 * <p>
 * This is safe to run in multi-instance deployments: each instance reports the same
 * queue depth (it's a property of RabbitMQ, not the instance). Prometheus will
 * deduplicate by scraping all instances and the values will agree.
 */
@Component
@ConditionalOnExpression("'${chronos.mode:all}' == 'all' or '${chronos.mode:all}' == 'api'")
public class QueueDepthMetrics {

	private static final Logger log = LoggerFactory.getLogger(QueueDepthMetrics.class);

	private final RabbitAdmin rabbitAdmin;
	private final Map<String, AtomicInteger> depthByQueue = new ConcurrentHashMap<>();

	private static final Map<String, String> QUEUE_LABEL = Map.of(
			RabbitMqConfig.HIGH_QUEUE, "high",
			RabbitMqConfig.MEDIUM_QUEUE, "medium",
			RabbitMqConfig.LOW_QUEUE, "low",
			RabbitMqConfig.DLQ, "dlq");

	public QueueDepthMetrics(RabbitAdmin rabbitAdmin, MeterRegistry meterRegistry) {
		this.rabbitAdmin = rabbitAdmin;
		QUEUE_LABEL.forEach((queue, label) -> {
			AtomicInteger depth = new AtomicInteger(0);
			depthByQueue.put(queue, depth);
			Gauge.builder("chronos_queue_depth", depth, AtomicInteger::get)
					.tag("queue", queue)
					.tag("priority", label)
					.description("Current message depth of RabbitMQ queue")
					.register(meterRegistry);
		});
	}

	@Scheduled(fixedDelay = 5000)
	public void refresh() {
		QUEUE_LABEL.keySet().forEach(queue -> {
			try {
				Properties props = rabbitAdmin.getQueueProperties(queue);
				if (props != null) {
					int depth = ((Integer) props.get("QUEUE_MESSAGE_COUNT"));
					depthByQueue.get(queue).set(depth);
				}
			} catch (Exception e) {
				log.debug("Could not poll queue depth for {}: {}", queue, e.getMessage());
			}
		});
	}
}
