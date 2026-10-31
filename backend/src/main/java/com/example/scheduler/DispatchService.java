package com.example.scheduler;

import java.time.Instant;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DispatchService {

    private final JobRunRepository runs;
    private final RabbitTemplate rabbit;

    /** Creates a run for the job and publishes it to the workers. */
    public JobRun newRun(Job job) {
        JobRun run = new JobRun();
        run.setJobId(job.getId());
        run.setJobName(job.getName());
        run.setScheduledFor(Instant.now());
        run = runs.save(run);
        send(RabbitConfig.EXCHANGE, RabbitConfig.RUN_KEY, toMessage(job, run));
        return run;
    }

    /** Puts a failed run back on the queue after the delay of the given retry tier. */
    public void publishRetry(Job job, JobRun run, int tier) {
        send("", RabbitConfig.retryQueueName(tier), toMessage(job, run));
    }

    public void publishDlq(Job job, JobRun run) {
        send("", RabbitConfig.DLQ, toMessage(job, run));
    }

    /** Resets a DEAD run and sends it through the queue again from attempt 1. */
    public void replay(Job job, JobRun run) {
        run.setAttempt(1);
        run.setStatus(RunStatus.QUEUED);
        run.setError(null);
        run.setStartedAt(null);
        run.setFinishedAt(null);
        send(RabbitConfig.EXCHANGE, RabbitConfig.RUN_KEY, toMessage(job, run));
    }

    private JobMessage toMessage(Job job, JobRun run) {
        return new JobMessage(run.getId(), job.getId(), job.getName(), job.getType(),
                job.getPayload(), run.getAttempt(), job.getTimeoutSeconds());
    }

    /**
     * Publishes only after the DB transaction commits, so a worker can never report
     * back about a run row that does not exist yet. The remaining gap (commit succeeds,
     * publish fails) is what the transactional outbox pattern closes.
     */
    private void send(String exchange, String routingKey, Object message) {
        Runnable publish = () -> rabbit.convertAndSend(exchange, routingKey, message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
