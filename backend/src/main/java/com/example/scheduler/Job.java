package com.example.scheduler;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "jobs")
@Getter
@Setter
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(columnDefinition = "text")
    private String payload;

    /** Spring cron (6 fields). Null means manual trigger only. */
    private String cronExpression;

    private Instant nextRunAt;
    private int maxRetries = 3;
    private int timeoutSeconds = 30;
    private boolean enabled = true;
    private Instant createdAt = Instant.now();
}
