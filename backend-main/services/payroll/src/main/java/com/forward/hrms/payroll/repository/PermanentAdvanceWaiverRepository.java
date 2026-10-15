package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.PermanentAdvanceWaiver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PermanentAdvanceWaiverRepository extends JpaRepository<PermanentAdvanceWaiver, Long> {

    boolean existsByEmployeeIdAndPeriodYearAndPeriodMonth(long employeeId, int periodYear, int periodMonth);

    @Modifying
    @Query("delete from PermanentAdvanceWaiver w where w.employeeId = :employeeId "
            + "and w.periodYear = :periodYear and w.periodMonth = :periodMonth")
    int deleteForPeriod(long employeeId, int periodYear, int periodMonth);
}
