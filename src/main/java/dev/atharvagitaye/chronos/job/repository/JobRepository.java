package dev.atharvagitaye.chronos.job.repository;

import dev.atharvagitaye.chronos.job.entity.Job;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;

public interface JobRepository extends JpaRepository<Job, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select job from Job job where job.id = :jobId")
	java.util.Optional<Job> findForUpdate(UUID jobId);
}