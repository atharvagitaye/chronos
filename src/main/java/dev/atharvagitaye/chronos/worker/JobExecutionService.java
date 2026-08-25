package dev.atharvagitaye.chronos.worker;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobExecutionService {

	private final JobExecutionRepository executionRepository;
	private final String workerId = "worker-" + UUID.randomUUID();

	public JobExecutionService(JobExecutionRepository executionRepository) {
		this.executionRepository = executionRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean claim(UUID jobId, int attemptNumber) {
		if (executionRepository.existsByJobIdAndAttemptNumber(jobId, attemptNumber)) {
			return false;
		}
		try {
			executionRepository.saveAndFlush(new JobExecution(jobId, attemptNumber, workerId));
			return true;
		} catch (DataIntegrityViolationException exception) {
			return false;
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void complete(UUID jobId, int attemptNumber) {
		executionRepository.findByJobIdAndAttemptNumber(jobId, attemptNumber)
				.ifPresent(execution -> execution.complete());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void fail(UUID jobId, int attemptNumber, String error) {
		executionRepository.findByJobIdAndAttemptNumber(jobId, attemptNumber)
				.ifPresent(execution -> execution.fail(error));
	}

	public String workerId() {
		return workerId;
	}
}