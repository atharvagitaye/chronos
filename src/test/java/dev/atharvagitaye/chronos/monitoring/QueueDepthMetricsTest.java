package dev.atharvagitaye.chronos.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import dev.atharvagitaye.chronos.queue.RabbitMqConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

class QueueDepthMetricsTest {

	@Test
	void recordsQueueDepthForEachPriorityQueue() {
		RabbitAdmin rabbitAdmin = org.mockito.Mockito.mock(RabbitAdmin.class);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		Properties properties = new Properties();
		properties.put("QUEUE_MESSAGE_COUNT", 7);
		when(rabbitAdmin.getQueueProperties(RabbitMqConfig.HIGH_QUEUE)).thenReturn(properties);

		QueueDepthMetrics metrics = new QueueDepthMetrics(rabbitAdmin, registry);
		metrics.refresh();

		assertEquals(7.0, registry.get("chronos_queue_depth")
				.tag("queue", RabbitMqConfig.HIGH_QUEUE).gauge().value());
		assertEquals(0.0, registry.get("chronos_queue_depth")
				.tag("queue", RabbitMqConfig.LOW_QUEUE).gauge().value());
	}
}
