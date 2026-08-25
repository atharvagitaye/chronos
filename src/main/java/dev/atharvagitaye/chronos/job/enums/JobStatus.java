package dev.atharvagitaye.chronos.job.enums;

public enum JobStatus {
	CREATED,
	QUEUED,
	RUNNING,
	SUCCESS,
	FAILED,
	RETRYING,
	DLQ,
	CANCELLED
}