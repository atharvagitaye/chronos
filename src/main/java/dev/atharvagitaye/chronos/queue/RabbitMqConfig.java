package dev.atharvagitaye.chronos.queue;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

	public static final String JOB_EXCHANGE = "chronos.job.exchange";
	public static final String HIGH_QUEUE = "chronos.high.queue";
	public static final String MEDIUM_QUEUE = "chronos.medium.queue";
	public static final String LOW_QUEUE = "chronos.low.queue";
	public static final String HIGH_RETRY_QUEUE = "chronos.high.retry.queue";
	public static final String MEDIUM_RETRY_QUEUE = "chronos.medium.retry.queue";
	public static final String LOW_RETRY_QUEUE = "chronos.low.retry.queue";

	@Bean
	DirectExchange jobExchange() {
		return new DirectExchange(JOB_EXCHANGE);
	}

	@Bean
	Queue highQueue() {
		return new Queue(HIGH_QUEUE, true);
	}

	@Bean
	Queue mediumQueue() {
		return new Queue(MEDIUM_QUEUE, true);
	}

	@Bean
	Queue lowQueue() {
		return new Queue(LOW_QUEUE, true);
	}

	@Bean
	Queue highRetryQueue() {
		return retryQueue(HIGH_RETRY_QUEUE, "job.high");
	}

	@Bean
	Queue mediumRetryQueue() {
		return retryQueue(MEDIUM_RETRY_QUEUE, "job.medium");
	}

	@Bean
	Queue lowRetryQueue() {
		return retryQueue(LOW_RETRY_QUEUE, "job.low");
	}

	private Queue retryQueue(String name, String deadLetterRoutingKey) {
		return new Queue(name, true, false, false, Map.of(
				"x-message-ttl", 30000,
				"x-dead-letter-exchange", JOB_EXCHANGE,
				"x-dead-letter-routing-key", deadLetterRoutingKey));
	}

	@Bean
	Binding highBinding(Queue highQueue, DirectExchange jobExchange) {
		return BindingBuilder.bind(highQueue).to(jobExchange).with("job.high");
	}

	@Bean
	Binding mediumBinding(Queue mediumQueue, DirectExchange jobExchange) {
		return BindingBuilder.bind(mediumQueue).to(jobExchange).with("job.medium");
	}

	@Bean
	Binding lowBinding(Queue lowQueue, DirectExchange jobExchange) {
		return BindingBuilder.bind(lowQueue).to(jobExchange).with("job.low");
	}

	@Bean
	RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
		return new RabbitAdmin(connectionFactory);
	}

	@Bean
	JacksonJsonMessageConverter messageConverter() {
		return new JacksonJsonMessageConverter();
	}
}