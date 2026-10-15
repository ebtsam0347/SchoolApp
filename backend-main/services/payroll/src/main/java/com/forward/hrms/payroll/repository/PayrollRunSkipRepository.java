package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.PayrollRunSkip;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PayrollRunSkipRepository extends JpaRepository<PayrollRunSkip, Long> {

    Page<PayrollRunSkip> findByPayrollRunId(long runId, Pageable pageable);

    List<PayrollRunSkip> findByPayrollRunIdAndEmployeeIdIn(long runId, List<Long> employeeIds);

    List<PayrollRunSkip> findAllByPayrollRunId(long runId);

    int countByPayrollRunId(long runId);

    boolean existsByPayrollRunIdAndEmployeeId(long runId, long employeeId);

    @Modifying
    @Query("delete from PayrollRunSkip s where s.payrollRunId = :runId")
    int deleteAllByPayrollRunId(long runId);

    @Modifying
    @Query("delete from PayrollRunSkip s where s.payrollRunId = :runId and s.employeeId in :employeeIds")
    int deleteByPayrollRunIdAndEmployeeIdIn(long runId, List<Long> employeeIds);
}
