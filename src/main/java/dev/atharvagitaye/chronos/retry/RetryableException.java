package dev.atharvagitaye.chronos.retry;

public class RetryableException extends RuntimeException {

	public RetryableException(String message) {
		super(message);
	}
}