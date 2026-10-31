package com.example.scheduler;

/** Sent to workers. {@code payload} is the job's JSON payload as a string. */
public record JobMessage(Long runId, Long jobId, String jobName, String type,
                         String payload, int attempt, int timeoutSeconds) {
}
