package dev.atharvagitaye.chronos.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

	public enum Status {
		PENDING,
		PUBLISHED
	}

	@Id
	private UUID id;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "aggregate_type", nullable = false, length = 100)
	private String aggregateType;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	protected OutboxEvent() {
	}

	public OutboxEvent(UUID aggregateId, String aggregateType, String eventType, Map<String, Object> payload) {
		this.id = UUID.randomUUID();
		this.aggregateId = aggregateId;
		this.aggregateType = aggregateType;
		this.eventType = eventType;
		this.payload = payload;
		this.status = Status.PENDING;
		this.attempts = 0;
		this.nextAttemptAt = Instant.now();
		this.createdAt = this.nextAttemptAt;
	}

	public void markPublished(Instant publishedAt) {
		this.status = Status.PUBLISHED;
		this.publishedAt = publishedAt;
	}

	public void recordFailure(Instant nextAttemptAt) {
		this.attempts++;
		this.nextAttemptAt = nextAttemptAt;
	}

	public UUID getId() { return id; }
	public UUID getAggregateId() { return aggregateId; }
	public String getAggregateType() { return aggregateType; }
	public String getEventType() { return eventType; }
	public Map<String, Object> getPayload() { return payload; }
	public Status getStatus() { return status; }
	public int getAttempts() { return attempts; }
	public Instant getNextAttemptAt() { return nextAttemptAt; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getPublishedAt() { return publishedAt; }
}