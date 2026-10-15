package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.enums.PayslipStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    Optional<Payslip> findByPayrollRunIdAndEmployeeId(long runId, long employeeId);

    /** Settlement-overlap guard (Audit Finding 4) — is there already a
     *  payslip for this employee covering this calendar month, in any
     *  status? A settlement's earned-salary period is always contained
     *  within a single calendar month, so year+month alone is sufficient. */
    boolean existsByEmployeeIdAndPeriodYearAndPeriodMonth(long employeeId, int periodYear, int periodMonth);

    /** Used by saga-retry safety in compute: filter out employees that
     *  already have a payslip on the run from a partial earlier attempt
     *  before iterating. Returns just the ids — cheap. */
    @Query("select p.employeeId from Payslip p where p.payrollRunId = :runId")
    Set<Long> findEmployeeIdsByPayrollRunId(long runId);

    Page<Payslip> findAllByPayrollRunId(long runId, Pageable pageable);

    /**
     * Bulk delete every payslip on a run. Used by the reset endpoint to
     * clear a COMPUTED run before recompute. Schema-level
     * {@code on delete cascade} on {@code payslip_lines.payslip_id} takes
     * the lines with it; no need to delete lines explicitly.
     */
    @Modifying
    @Query("delete from Payslip p where p.payrollRunId = :runId")
    int deleteAllByPayrollRunId(long runId);

    /**
     * Bulk transition every payslip on a run from {@code from} to
     * {@code to}. Used by the post step to flip {@code DRAFT → PAID}
     * for the run; HELD rows stay HELD so they don't disburse this
     * cycle. Idempotent on a re-run because the second pass finds zero
     * rows in {@code from}.
     */
    @Modifying
    @Query("update Payslip p set p.status = :to where p.payrollRunId = :runId and p.status = :from")
    int bulkUpdateStatusForRun(long runId, PayslipStatus from, PayslipStatus to);

    Page<Payslip> findAllByEmployeeIdOrderByGeneratedAtDesc(long employeeId, Pageable pageable);

    Page<Payslip> findAllByEmployeeIdAndPeriodYear(long employeeId, int periodYear, Pageable pageable);

    /** ESS self-serve view — hides DRAFT (run not yet posted) from the employee. */
    Page<Payslip> findAllByEmployeeIdAndPeriodYearAndStatusNot(
            long employeeId, int periodYear, PayslipStatus statusToHide, Pageable pageable);

    /** Manager-team view (year). Hides DRAFT. Caller passes the resolved
     *  direct-report ids — never an empty collection (service short-circuits). */
    Page<Payslip> findAllByEmployeeIdInAndPeriodYearAndStatusNot(
            java.util.Collection<Long> employeeIds, int periodYear, PayslipStatus statusToHide, Pageable pageable);

    /** Manager-team view (year + month). Hides DRAFT. */
    Page<Payslip> findAllByEmployeeIdInAndPeriodYearAndPeriodMonthAndStatusNot(
            java.util.Collection<Long> employeeIds, int periodYear, int periodMonth,
            PayslipStatus statusToHide, Pageable pageable);

    Page<Payslip> findAllByPayrollRunIdAndStatus(long runId, PayslipStatus status, Pageable pageable);

    @Query("""
            select p from Payslip p
            where p.payrollRunId = :runId
              and (:status is null or p.status = :status)
              and (:q is null or lower(p.employeeCode) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Payslip> searchByRun(long runId, PayslipStatus status, String q, Pageable pageable);

    /** (id, created_by) for a page of payslips in one read-only query. */
    @Query("""
            select p.id as id, p.createdBy as createdBy
            from Payslip p
            where p.id in :ids
            """)
    List<com.forward.hrms.payroll.repository.projection.PayslipProvenanceProjection> findProvenanceByIdIn(
            java.util.Collection<Long> ids);

    Page<Payslip> findAllByStatus(PayslipStatus status, Pageable pageable);

    List<Payslip> findAllByEmployeeIdAndStatus(long employeeId, PayslipStatus status);

    List<Payslip> findAllByPayrollRunIdAndStatus(long runId, PayslipStatus status);

    /**
     * Pre-aggregated totals for a run, optionally filtered by the same
     * {@code status} / {@code q} predicates as {@link #searchByRun}.
     * Replaces the BFF dashboard pattern of fetching every payslip just
     * to sum it (5000 employees × 6 trend months = 30k row reads per
     * dashboard render). Returns a single projection row; SQL does the
     * SUM. Null totals on an empty run surface as zero via the
     * {@code coalesce}.
     */
    @Query("""
            select new com.forward.hrms.payroll.repository.PayslipRepository$RunTotals(
                count(p),
                coalesce(sum(p.basic),         0),
                coalesce(sum(p.allowances),    0),
                coalesce(sum(p.gross),         0),
                coalesce(sum(p.deductions),    0),
                coalesce(sum(p.tax),           0),
                coalesce(sum(p.eobi),          0),
                coalesce(sum(p.pessiEmployer), 0),
                coalesce(sum(p.net),           0),
                coalesce(sum(case when p.status = com.forward.hrms.payroll.enums.PayslipStatus.HELD then 1 else 0 end), 0),
                coalesce(sum(case when p.status = com.forward.hrms.payroll.enums.PayslipStatus.RELEASE_PENDING then 1 else 0 end), 0)
            )
            from Payslip p
            where p.payrollRunId = :runId
              and (:status is null or p.status = :status)
              and (:q is null or lower(p.employeeCode) like lower(concat('%', cast(:q as string), '%')))
            """)
    RunTotals totalsForRun(long runId, PayslipStatus status, String q);

    record RunTotals(
            long employeeCount,
            java.math.BigDecimal basic,
            java.math.BigDecimal allowances,
            java.math.BigDecimal gross,
            java.math.BigDecimal deductions,
            java.math.BigDecimal tax,
            java.math.BigDecimal eobi,
            java.math.BigDecimal pessiEmployer,
            java.math.BigDecimal net,
            long heldCount,
            long releasePendingCount
    ) {}
}
