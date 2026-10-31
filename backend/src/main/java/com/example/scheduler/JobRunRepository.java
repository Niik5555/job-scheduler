package com.example.scheduler;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {
    List<JobRun> findTop50ByOrderByIdDesc();

    List<JobRun> findTop50ByStatusOrderByIdDesc(RunStatus status);

    List<JobRun> findTop50ByJobIdOrderByIdDesc(Long jobId);
}
