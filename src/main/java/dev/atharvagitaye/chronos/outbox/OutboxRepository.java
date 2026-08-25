package dev.atharvagitaye.chronos.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select event from OutboxEvent event where event.status = dev.atharvagitaye.chronos.outbox.OutboxEvent$Status.PENDING "
			+ "and event.nextAttemptAt <= :now order by event.createdAt")
	List<OutboxEvent> findPending(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);
}