package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.LeaveBalance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    /**
     * Cycle identity: one row per (employee, type, cycle-start).
     */
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndCycleStart(
            Long employeeId, Long leaveTypeId, LocalDate cycleStart);

    /**
     * Cycles whose window contains {@code on} — the "current balances" read.
     */
    List<LeaveBalance> findAllByEmployeeIdAndCycleStartLessThanEqualAndCycleEndGreaterThanEqualOrderByLeaveTypeIdAsc(
            Long employeeId, LocalDate on, LocalDate sameOn);

    /**
     * Historical grouping by cycle-start year (reports / analytics / explicit ?year=).
     */
    List<LeaveBalance> findAllByEmployeeIdAndYearOrderByLeaveTypeIdAsc(Long employeeId, int year);

    /** Bulk "cycles containing {@code on}" for a page of employees (payslip leave section). */
    List<LeaveBalance> findAllByEmployeeIdInAndCycleStartLessThanEqualAndCycleEndGreaterThanEqual(
            java.util.Collection<Long> employeeIds, LocalDate on, LocalDate sameOn);

    // new method for leaves calculation
    Optional<LeaveBalance> findFirstByEmployeeIdAndLeaveTypeIdAndCycleStartLessThanEqualAndCycleEndGreaterThanEqual(
            Long employeeId,
            Long leaveTypeId,
            LocalDate cycleStart,
            LocalDate cycleEnd
    );

    // even better method
    @Query("""
            select b
            from LeaveBalance b
            where b.employeeId = :employeeId
              and b.leaveTypeId = :leaveTypeId
              and :onDate between b.cycleStart and b.cycleEnd
            """)
    Optional<LeaveBalance> findCurrentCycle(
            @Param("employeeId") Long employeeId,
            @Param("leaveTypeId") Long leaveTypeId,
            @Param("onDate") LocalDate onDate);

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndCycleStartLessThanEqualAndCycleEndGreaterThanEqual(
            long employeeId,
            long leaveTypeId,
            LocalDate cycleStart,
            LocalDate endDate
    );
    
    /**
     * Delete leave balance entries created from annual leave unpaid transactions.
     */
    long deleteByAnnualLeaveTransactionId(Long annualLeaveTransactionId);
}
