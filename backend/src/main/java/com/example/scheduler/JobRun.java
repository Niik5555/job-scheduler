package com.example.scheduler;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "job_runs")
@Getter
@Setter
public class JobRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long jobId;
    private String jobName;
    private int attempt = 1;

    @Enumerated(EnumType.STRING)
    private RunStatus status = RunStatus.QUEUED;

    private Instant scheduledFor = Instant.now();
    private Instant startedAt;
    private Instant finishedAt;
    private String workerId;

    @Column(columnDefinition = "text")
    private String error;

    private Instant createdAt = Instant.now();
}
