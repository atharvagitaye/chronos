package dev.atharvagitaye.chronos.worker;

import java.util.UUID;

public record JobMessage(UUID jobId, int attempt, String jobType, String priority) {
}