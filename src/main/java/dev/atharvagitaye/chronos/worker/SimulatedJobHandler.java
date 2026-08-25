package dev.atharvagitaye.chronos.worker;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SimulatedJobHandler {

	public void execute(Map<String, Object> payload) throws InterruptedException {
		long durationMs = ((Number) payload.getOrDefault("durationMs", 0)).longValue();
		if (durationMs > 0) {
			Thread.sleep(durationMs);
		}
		if ("ALWAYS_FAIL".equals(payload.get("failureMode")) || "FAIL_ONCE".equals(payload.get("failureMode"))) {
			throw new IllegalStateException("Simulated job failure");
		}
	}
}