package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface PermanentAdvanceRepository extends JpaRepository<PermanentAdvance, Long> {

    @Query("""
            select a from PermanentAdvance a
            where (cast(:employeeId as long) is null or a.employeeId = :employeeId)
              and (cast(:status as string) is null or a.status = :status)
            """)
    Page<PermanentAdvance> search(
            @Param("employeeId") Long employeeId,
            @Param("status") AdvanceStatus status,
            Pageable pageable);

    @Query("""
            select a from PermanentAdvance a
            where a.employeeId = :employeeId
              and a.status = com.forward.hrms.payroll.enums.AdvanceStatus.APPROVED
            """)
    List<PermanentAdvance> findOpenByEmployee(Long employeeId);

    List<PermanentAdvance> findByStatusInOrderByIdDesc(List<AdvanceStatus> statuses);

    /**
     * Whether {@code employeeId} already has a permanent advance filed in
     * {@code [from, to)} that still counts — anything but REJECTED or
     * CANCELLED.
     */
    @Query("""
            select count(a) > 0 from PermanentAdvance a
            where a.employeeId = :employeeId
              and a.createdAt >= :from and a.createdAt < :to
              and a.status not in (com.forward.hrms.payroll.enums.AdvanceStatus.REJECTED,
                                   com.forward.hrms.payroll.enums.AdvanceStatus.CANCELLED)
            """)
    boolean existsActiveFiledBetween(Long employeeId, java.time.LocalDateTime from, java.time.LocalDateTime to);

    // new method to get advances.
    @Query("""
            select a
            from PermanentAdvance a
            where a.employeeId=:employeeId
            and a.status=com.forward.hrms.payroll.enums.AdvanceStatus.APPROVED
            and a.closedAt is null
            """)
    List<PermanentAdvance> findOutstandingByEmployee(Long employeeId);

    // important method for reporting
    @Query("""
            select coalesce(sum(a.amount),0)
            from PermanentAdvance a
            where a.employeeId=:employeeId
            and a.status=com.forward.hrms.payroll.enums.AdvanceStatus.APPROVED
            """)
    BigDecimal totalDisbursed(Long employeeId);

    /** Every APPROVED advance of one employee — settled ones included. */
    List<PermanentAdvance> findByEmployeeIdAndStatus(Long employeeId, AdvanceStatus status);

    /** {@link #findByEmployeeIdAndStatus} for a page of employees. */
    List<PermanentAdvance> findByEmployeeIdInAndStatus(Collection<Long> employeeIds, AdvanceStatus status);

    @Query("""
    SELECT p
    FROM PermanentAdvance p
    WHERE p.employeeId = :employeeId
      AND p.status = com.forward.hrms.payroll.enums.AdvanceStatus.APPROVED
      AND p.annualLeaveMonth = :annualLeaveMonth
    ORDER BY p.id DESC
    """)
    List<PermanentAdvance> findApprovedAdvanceAgainstAnnualLeaveLatest(Long employeeId, LocalDate annualLeaveMonth);

    @Query("""
           select sum(a.amount)
              from PermanentAdvance a
              where a.employeeId = :employeeId
                and a.status = com.forward.hrms.payroll.enums.AdvanceStatus.APPROVED
        """)
    BigDecimal employeeTotalPermanentAdvance(Long employeeId);

    PermanentAdvance findTopByEmployeeIdAndStatusOrderByIdDesc(Long employeeId, AdvanceStatus status);

    /**
     * Sum of every APPROVED advance per employee — the "taken" side of
     * balance = taken − deducted. Settled advances count; so do their
     * closing entries on the deducted side.
     */
    @Query("""
            select a.employeeId as employeeId, coalesce(sum(a.amount), 0) as appliedAmount
            from PermanentAdvance a
            where a.employeeId in :employeeIds
              and a.status = com.forward.hrms.payroll.enums.AdvanceStatus.APPROVED
            group by a.employeeId
            """)
    List<com.forward.hrms.payroll.repository.projection.EmployeeAppliedAdvanceProjection> approvedTotalsByEmployeeIds(
            @Param("employeeIds") Collection<Long> employeeIds);

    /**
     * Principal of each employee's open (APPROVED, not closed) permanent advances received on or before the
     * period end - the "total amount applied" shown on the salary slip. Read-only.
     */
    @Query("""
            select a.employeeId as employeeId, coalesce(sum(a.amount), 0) as appliedAmount
            from PermanentAdvance a
            where a.employeeId in :employeeIds
              and a.status = com.forward.hrms.payroll.enums.AdvanceStatus.APPROVED
              and a.closedAt is null
              and (a.receivingDate is null or a.receivingDate <= :periodEnd)
            group by a.employeeId
            """)
    List<com.forward.hrms.payroll.repository.projection.EmployeeAppliedAdvanceProjection> appliedTotalsByEmployeeIds(
            @Param("employeeIds") Collection<Long> employeeIds, @Param("periodEnd") LocalDate periodEnd);

    /**
     * Advances {@code createdBy} filed for someone other than
     * {@code excludeEmployeeId} (their own employee id) — on-behalf
     * submissions. {@code created_by} is the submitter's username, stamped on
     * insert and never updated.
     */
    @Query("""
            select a from PermanentAdvance a
            where a.createdBy = :createdBy
              and (cast(:excludeEmployeeId as long) is null or a.employeeId <> :excludeEmployeeId)
              and (cast(:status as string) is null or a.status = :status)
              and (cast(:filedFrom as LocalDateTime) is null or a.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or a.createdAt < :filedTo)
            """)
    Page<PermanentAdvance> searchFiledOnBehalf(
            @Param("createdBy") String createdBy,
            @Param("excludeEmployeeId") Long excludeEmployeeId,
            @Param("status") AdvanceStatus status,
            @Param("filedFrom") java.time.LocalDateTime filedFrom,
            @Param("filedTo") java.time.LocalDateTime filedTo,
            Pageable pageable);

    /**
     * Requests for any of {@code employeeIds} — the caller's department —
     * whoever filed them (the employee, their manager, or a team-request
     * generator). Optional status and filed-on window ({@code createdAt},
     * half-open). Backs the "Generated team requests" department view.
     */
    @Query("""
            select a from PermanentAdvance a
            where a.employeeId in :employeeIds
              and (cast(:status as string) is null or a.status = :status)
              and (cast(:filedFrom as LocalDateTime) is null or a.createdAt >= :filedFrom)
              and (cast(:filedTo as LocalDateTime) is null or a.createdAt < :filedTo)
            """)
    Page<PermanentAdvance> searchDepartment(
            @Param("employeeIds") java.util.Collection<Long> employeeIds,
            @Param("status") AdvanceStatus status,
            @Param("filedFrom") java.time.LocalDateTime filedFrom,
            @Param("filedTo") java.time.LocalDateTime filedTo,
            Pageable pageable);
}
