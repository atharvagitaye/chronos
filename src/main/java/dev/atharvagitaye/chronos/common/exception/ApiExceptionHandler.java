package dev.atharvagitaye.chronos.common.exception;

import dev.atharvagitaye.chronos.job.service.JobService.JobNotFoundException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(JobNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(JobNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst().map(error -> error.getField() + " " + error.getDefaultMessage())
				.orElse("Request validation failed");
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
	}

	private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now(), "status", status.value(),
				"error", code, "message", message));
	}
}