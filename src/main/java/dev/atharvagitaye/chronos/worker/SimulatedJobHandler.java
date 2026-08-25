package dev.atharvagitaye.chronos.worker;

import java.util.Map;
import dev.atharvagitaye.chronos.retry.RetryableException;
import org.springframework.stereotype.Component;

@Component
public class SimulatedJobHandler {

	public void execute(Map<String, Object> payload, int attempt) throws InterruptedException {
		long durationMs = ((Number) payload.getOrDefault("durationMs", 0)).longValue();
		if (durationMs > 0) {
			Thread.sleep(durationMs);
		}
		if ("ALWAYS_FAIL".equals(payload.get("failureMode")) ||
				("FAIL_ONCE".equals(payload.get("failureMode")) && attempt == 0)) {
			throw new RetryableException("Simulated transient failure");
		}
	}
}