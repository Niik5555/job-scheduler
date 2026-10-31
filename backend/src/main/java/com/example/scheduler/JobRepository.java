package com.example.scheduler;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobRepository extends JpaRepository<Job, Long> {

    /**
     * Claims due jobs. FOR UPDATE SKIP LOCKED lets several scheduler instances run
     * side by side: each locks a different set of rows, so no job is dispatched twice.
     * Must be called inside a transaction.
     */
    @Query(value = """
            SELECT * FROM jobs
            WHERE enabled = true AND next_run_at <= now()
            ORDER BY next_run_at
            LIMIT 20
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Job> claimDueJobs();
}
