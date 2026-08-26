package dev.atharvagitaye.chronos.worker;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {

	boolean existsByJobIdAndAttemptNumber(UUID jobId, int attemptNumber);

	Optional<JobExecution> findByJobIdAndAttemptNumber(UUID jobId, int attemptNumber);

	void deleteByJobId(UUID jobId);
}