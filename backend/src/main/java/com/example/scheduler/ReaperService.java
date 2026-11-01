package com.example.scheduler;

import java.time.Duration;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds RUNNING runs whose worker has stopped sending heartbeats and puts them back to QUEUED.
 * RabbitMQ still holds the unacked message and redelivers it, so no new message is published
 * here; this only keeps the status truthful while no worker is alive.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReaperService {

    private final JobRunRepository runs;
    private final WorkerRegistry workers;
    private final StringRedisTemplate redis;

    @Scheduled(fixedDelay = 5000)
    public void reap() {
        try {
            // Only one backend instance reaps per cycle. The lock expires before the next cycle.
            Boolean locked = redis.opsForValue().setIfAbsent("reaper:lock", "1", Duration.ofSeconds(4));
            if (!Boolean.TRUE.equals(locked)) {
                return;
            }
            for (JobRun run : runs.findByStatus(RunStatus.RUNNING)) {
                String workerId = run.getWorkerId();
                if (workerId != null && !workers.isAlive(workerId)) {
                    String note = "Worker " + workerId + " stopped; waiting for redelivery";
                    // Conditional update: does nothing if the run finished or moved to another worker meanwhile.
                    int changed = runs.markWorkerLost(run.getId(), workerId, note);
                    if (changed > 0) {
                        log.warn("Run {} was on dead worker {}, set back to QUEUED", run.getId(), workerId);
                    }
                }
            }
        } catch (DataAccessException e) {
            log.warn("Reaper skipped this cycle: {}", e.getMessage());
        }
    }
}
