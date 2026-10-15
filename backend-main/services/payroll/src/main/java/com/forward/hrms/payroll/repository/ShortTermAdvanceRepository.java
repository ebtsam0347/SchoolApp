package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShortTermAdvanceRepository extends JpaRepository<ShortTermAdvance, Long> {

    List<ShortTermAdvance> findAllByBatchIdOrderByEmployeeIdAsc(Long batchId);

    boolean existsByBatchIdAndEmployeeId(Long batchId, Long employeeId);

    @Query("""
            select s from ShortTermAdvance s
            where s.targetYear = :year
              and s.targetMonth = :month
              and s.status = com.forward.hrms.payroll.enums.ShortTermAdvanceStatus.APPROVED
              and s.employeeId = :employeeId
            """)
    List<ShortTermAdvance> findApprovedForEmployeeMonth(Long employeeId, int year, int month);

    // best naming of above method // Pure readability.
    @Query("""
            select s from ShortTermAdvance s
            where s.targetYear = :year
              and s.targetMonth = :month
              and s.status = com.forward.hrms.payroll.enums.ShortTermAdvanceStatus.APPROVED
              and s.employeeId = :employeeId
            """)
    List<ShortTermAdvance> findApprovedForSettlementMonth(Long employeeId, int year, int month);

    @Query("""
            select coalesce(sum(s.amount), 0)
            from ShortTermAdvance s
            where s.batchId = :batchId
              and s.status not in (com.forward.hrms.payroll.enums.ShortTermAdvanceStatus.REJECTED,
                                   com.forward.hrms.payroll.enums.ShortTermAdvanceStatus.CANCELLED)
            """)
    long activeTotalForBatch(Long batchId);

    @Query("""
            select count(s)
            from ShortTermAdvance s
            where s.batchId = :batchId
              and s.status not in (com.forward.hrms.payroll.enums.ShortTermAdvanceStatus.REJECTED,
                                   com.forward.hrms.payroll.enums.ShortTermAdvanceStatus.CANCELLED)
            """)
    long activeCountForBatch(Long batchId);

    /** Advances approved for the given month, for a page of employees. */
    @Query("""
            select s from ShortTermAdvance s
            where s.employeeId in :employeeIds
              and s.targetYear = :year
              and s.targetMonth = :month
              and s.status = com.forward.hrms.payroll.enums.ShortTermAdvanceStatus.APPROVED
            """)
    List<ShortTermAdvance> findAppliedForEmployeesInMonth(java.util.Collection<Long> employeeIds, int year, int month);

    /**
     * Lines for any of {@code employeeIds} in the target month that still
     * count — pending approval or approved. REJECTED and CANCELLED lines
     * don't, so the employee may apply again.
     */
    @Query("""
            select s from ShortTermAdvance s
            where s.employeeId in :employeeIds
              and s.targetYear = :year
              and s.targetMonth = :month
              and s.status not in (com.forward.hrms.payroll.enums.ShortTermAdvanceStatus.REJECTED,
                                   com.forward.hrms.payroll.enums.ShortTermAdvanceStatus.CANCELLED)
            """)
    List<ShortTermAdvance> findActiveForEmployeesInMonth(java.util.Collection<Long> employeeIds, int year, int month);

    long countByBatchId(Long batchId);

    /** APPROVED lines for the month not yet recovered by a final settlement. */
    List<ShortTermAdvance> findByEmployeeIdAndTargetYearAndTargetMonthAndStatusAndFinalSettlementIdIsNull(
            Long employeeId, int year, int monthValue, ShortTermAdvanceStatus status);

    /**
     * Rows recovered by a Final Settlement's closing
     * step — used to undo a rejected settlement.
     */
    List<ShortTermAdvance> findAllByFinalSettlementId(Long finalSettlementId);
}
