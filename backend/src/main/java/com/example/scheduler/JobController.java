package com.example.scheduler;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobController {

    private final JobRepository jobs;
    private final JobRunRepository runs;
    private final DispatchService dispatch;
    private final WorkerRegistry workers;

    public record CreateJobRequest(
            @NotBlank String name,
            @NotBlank String type,
            String payload,
            String cronExpression,
            @Min(0) @Max(10) Integer maxRetries,
            @Min(1) @Max(300) Integer timeoutSeconds) {
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public Job create(@Valid @RequestBody CreateJobRequest req) {
        String cron = req.cronExpression() == null || req.cronExpression().isBlank()
                ? null : req.cronExpression().trim();
        if (cron != null && !CronExpression.isValidExpression(cron)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cron needs 6 fields: second minute hour day month weekday");
        }
        Job job = new Job();
        job.setName(req.name());
        job.setType(req.type());
        job.setPayload(req.payload() == null || req.payload().isBlank() ? "{}" : req.payload());
        job.setCronExpression(cron);
        job.setNextRunAt(SchedulerService.nextRun(cron));
        if (req.maxRetries() != null) {
            job.setMaxRetries(req.maxRetries());
        }
        if (req.timeoutSeconds() != null) {
            job.setTimeoutSeconds(req.timeoutSeconds());
        }
        return jobs.save(job);
    }

    @GetMapping("/jobs")
    public List<Job> listJobs() {
        return jobs.findAll(Sort.by("id"));
    }

    @PostMapping("/jobs/{id}/trigger")
    @Transactional
    public JobRun trigger(@PathVariable Long id) {
        return dispatch.newRun(findJob(id));
    }

    @PostMapping("/jobs/{id}/toggle")
    @Transactional
    public Job toggle(@PathVariable Long id) {
        Job job = findJob(id);
        job.setEnabled(!job.isEnabled());
        if (job.isEnabled()) {
            job.setNextRunAt(SchedulerService.nextRun(job.getCronExpression()));
        }
        return job;
    }

    @GetMapping("/jobs/{id}/runs")
    public List<JobRun> runsForJob(@PathVariable Long id) {
        return runs.findTop50ByJobIdOrderByIdDesc(id);
    }

    @GetMapping("/workers")
    public List<String> listWorkers() {
        return workers.aliveWorkers();
    }

    @GetMapping("/runs")
    public List<JobRun> listRuns(@RequestParam(required = false) RunStatus status) {
        return status == null
                ? runs.findTop50ByOrderByIdDesc()
                : runs.findTop50ByStatusOrderByIdDesc(status);
    }

    @PostMapping("/runs/{id}/replay")
    @Transactional
    public JobRun replay(@PathVariable Long id) {
        JobRun run = runs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
        if (run.getStatus() != RunStatus.DEAD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DEAD runs can be replayed");
        }
        Job job = findJob(run.getJobId());
        dispatch.replay(job, run);
        return run;
    }

    private Job findJob(Long id) {
        return jobs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }
}
