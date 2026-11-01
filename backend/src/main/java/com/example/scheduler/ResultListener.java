package com.example.scheduler;

import java.time.Instant;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResultListener {

    private final JobRunRepository runs;
    private final JobRepository jobs;
    private final DispatchService dispatch;

    @RabbitListener(queues = RabbitConfig.RESULTS_QUEUE)
    @Transactional
    public void onResult(ResultMessage msg) {
        JobRun run = runs.findById(msg.runId()).orElse(null);
        if (run == null) {
            log.warn("Result for unknown run {}", msg.runId());
            return;
        }
        run.setWorkerId(msg.workerId());
        switch (msg.status()) {
            case "RUNNING" -> {
                run.setStatus(RunStatus.RUNNING);
                run.setStartedAt(Instant.now());
                run.setError(null);
            }
            case "SUCCEEDED" -> {
                run.setStatus(RunStatus.SUCCEEDED);
                run.setFinishedAt(Instant.now());
                run.setError(null);
            }
            case "FAILED" -> handleFailure(run, msg.error());
            default -> log.warn("Unknown status {} for run {}", msg.status(), run.getId());
        }
    }

    private void handleFailure(JobRun run, String error) {
        run.setError(error);
        Job job = jobs.findById(run.getJobId()).orElse(null);
        if (job == null) {
            run.setStatus(RunStatus.DEAD);
            run.setFinishedAt(Instant.now());
            return;
        }
        if (run.getAttempt() <= job.getMaxRetries()) {
            int tier = Math.min(run.getAttempt(), RabbitConfig.RETRY_DELAYS_MS.length);
            run.setStatus(RunStatus.RETRY_WAIT);
            run.setAttempt(run.getAttempt() + 1);
            dispatch.publishRetry(job, run, tier);
            log.info("Run {} failed, retry {} in tier {}", run.getId(), run.getAttempt(), tier);
        } else {
            run.setStatus(RunStatus.DEAD);
            run.setFinishedAt(Instant.now());
            dispatch.publishDlq(job, run);
            log.warn("Run {} is dead after {} attempts", run.getId(), run.getAttempt());
        }
    }
}
