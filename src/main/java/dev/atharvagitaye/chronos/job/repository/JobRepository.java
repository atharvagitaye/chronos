package dev.atharvagitaye.chronos.job.repository;

import dev.atharvagitaye.chronos.job.entity.Job;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import dev.atharvagitaye.chronos.job.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select job from Job job where job.id = :jobId")
	java.util.Optional<Job> findForUpdate(UUID jobId);

	Page<Job> findByStatus(JobStatus status, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select job from Job job where job.status = dev.atharvagitaye.chronos.job.enums.JobStatus.RUNNING "
			+ "and job.leaseUntil < :now")
	List<Job> findExpiredLeases(@org.springframework.data.repository.query.Param("now") Instant now,
			Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select job from Job job where job.status = dev.atharvagitaye.chronos.job.enums.JobStatus.CREATED "
			+ "and job.scheduledAt <= :now")
	List<Job> findDueScheduledJobs(@org.springframework.data.repository.query.Param("now") Instant now,
			Pageable pageable);
}