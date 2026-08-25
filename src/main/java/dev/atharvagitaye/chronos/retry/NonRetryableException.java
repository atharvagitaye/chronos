package dev.atharvagitaye.chronos.retry;

public class NonRetryableException extends RuntimeException {

	public NonRetryableException(String message) {
		super(message);
	}
}