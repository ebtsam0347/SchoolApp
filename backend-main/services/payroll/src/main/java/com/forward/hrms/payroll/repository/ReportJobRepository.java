package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.ReportJob;
import com.forward.hrms.payroll.enums.ReportJobStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportJobRepository extends JpaRepository<ReportJob, Long> {

    /**
     * Pick the oldest QUEUED rows. The runner uses
     * {@code findFirstByStatusOrderByCreatedAtAsc} once a QUEUED row is
     * locked it flips status to RUNNING in its own tx so concurrent
     * pollers don't double-process.
     */
    @Query("""
            select j from ReportJob j
            where j.status = :status
            order by j.createdAt asc
            """)
    List<ReportJob> findOldestByStatus(@Param("status") ReportJobStatus status, Pageable pageable);

    Page<ReportJob> findAllByTypeOrderByCreatedAtDesc(String type, Pageable pageable);

    Page<ReportJob> findAllByRequestedByOrderByCreatedAtDesc(String requestedBy, Pageable pageable);
}
