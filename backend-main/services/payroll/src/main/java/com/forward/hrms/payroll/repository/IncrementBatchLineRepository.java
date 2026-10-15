package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.IncrementBatchLine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncrementBatchLineRepository extends JpaRepository<IncrementBatchLine, Long> {

    List<IncrementBatchLine> findAllByBatchIdOrderByEmployeeId(long batchId);

    Optional<IncrementBatchLine> findByBatchIdAndEmployeeId(long batchId, long employeeId);

    long countByBatchIdAndStatus(long batchId,
                                 com.forward.hrms.payroll.enums.IncrementBatchLineStatus status);
}
