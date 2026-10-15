package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.IncrementBatch;
import com.forward.hrms.payroll.enums.IncrementBatchStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface IncrementBatchRepository extends JpaRepository<IncrementBatch, Long> {

    @Query("""
            select b from IncrementBatch b
            where (:status is null or b.status = :status)
              and (:fyStart is null or b.effectiveFrom >= :fyStart)
              and (:fyEnd   is null or b.effectiveFrom <= :fyEnd)
            order by b.id desc
            """)
    List<IncrementBatch> filter(IncrementBatchStatus status, LocalDate fyStart, LocalDate fyEnd);
}
