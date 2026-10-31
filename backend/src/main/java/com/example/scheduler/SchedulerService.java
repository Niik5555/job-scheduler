package com.example.scheduler;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerService {

    private final JobRepository jobs;
    private final DispatchService dispatch;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void tick() {
        for (Job job : jobs.claimDueJobs()) {
            JobRun run = dispatch.newRun(job);
            job.setNextRunAt(nextRun(job.getCronExpression()));
            log.info("Dispatched job {} as run {}", job.getName(), run.getId());
        }
    }

    public static Instant nextRun(String cron) {
        if (cron == null || cron.isBlank()) {
            return null;
        }
        ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.now(ZoneOffset.UTC));
        return next == null ? null : next.toInstant();
    }
}
