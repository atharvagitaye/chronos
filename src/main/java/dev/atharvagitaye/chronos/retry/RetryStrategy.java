package dev.atharvagitaye.chronos.retry;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RetryStrategy {

	private final long baseDelayMs;
	private final long maxDelayMs;

	public RetryStrategy(@Value("${chronos.retry.base-delay-ms:2000}") long baseDelayMs,
			@Value("${chronos.retry.max-delay-ms:30000}") long maxDelayMs) {
		this.baseDelayMs = baseDelayMs;
		this.maxDelayMs = maxDelayMs;
	}

	public long delayMs(int retryCount) {
		long exponentialDelay = Math.min(maxDelayMs, baseDelayMs * (1L << Math.min(retryCount - 1, 20)));
		long jitter = Math.max(1, exponentialDelay / 10);
		return exponentialDelay - jitter + ThreadLocalRandom.current().nextLong(jitter * 2 + 1);
	}

}