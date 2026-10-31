package com.example.scheduler;

/** Sent by workers. status is RUNNING, SUCCEEDED or FAILED. */
public record ResultMessage(Long runId, String status, String workerId, String error) {
}
