package com.example.scheduler;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {
    List<JobRun> findTop50ByOrderByIdDesc();

    List<JobRun> findTop50ByStatusOrderByIdDesc(RunStatus status);

    List<JobRun> findTop50ByJobIdOrderByIdDesc(Long jobId);

    List<JobRun> findByStatus(RunStatus status);

    /** Compare-and-set: only changes the run if it is still RUNNING on that same worker. */
    @Modifying
    @Transactional
    @Query("""
            update JobRun r
            set r.status = com.example.scheduler.RunStatus.QUEUED, r.error = :error
            where r.id = :id
              and r.status = com.example.scheduler.RunStatus.RUNNING
              and r.workerId = :workerId
            """)
    int markWorkerLost(@Param("id") Long id, @Param("workerId") String workerId, @Param("error") String error);
}
