package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.AllowanceTransaction;
import com.forward.hrms.payroll.enums.AllowanceTransactionStatus;
import com.forward.hrms.payroll.enums.PayslipStatus;
import io.micrometer.core.instrument.config.validate.Validated;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AllowanceTransactionRepository extends JpaRepository<AllowanceTransaction, Integer> {

    @Query("select at.employeeId from AllowanceTransaction at where at.allowanceRunId = :runId")
    Set<Long> findByEmployeeIdsAndAllowanceRunId(long runId);

    @Query("""
            select new com.forward.hrms.payroll.repository.AllowanceTransactionRepository$RunTotals(
                count(distinct at.employeeId),
                coalesce(sum(at.amount),         0),
                coalesce(sum(case when at.status = com.forward.hrms.payroll.enums.AllowanceTransactionStatus.HELD then 1 else 0 end), 0),
                coalesce(sum(case when at.status = com.forward.hrms.payroll.enums.AllowanceTransactionStatus.RELEASE_PENDING then 1 else 0 end), 0)
            )
            from AllowanceTransaction at
            where at.allowanceRunId = :runId
              and (:status is null or at.status = :status)
              and (:q is null or lower(at.employeeCode) like lower(concat('%', cast(:q as string), '%')))
            """)
    AllowanceTransactionRepository.RunTotals totalsForRun(long runId, AllowanceTransactionStatus status, String q);

    record RunTotals(
            long employeeCount,
            java.math.BigDecimal allowances,
            long heldCount,
            long releasePendingCount
    ) {}

    @Modifying
    @Query("delete from AllowanceTransaction at where at.allowanceRunId = :runId")
    int deleteAllByAllowanceRunId(long runId);

    @Query(value = """
        SELECT 
            at.employee_id,
            at.employee_code,
            at.grade,
            at.fiscal_year_code,
            at.allowance_year,
            at.allowance_month,
            esc.id,
            sc.code,
            sc.name,
            at.amount,
            at.status
        FROM payroll.allowance_transaction at
        JOIN payroll.employee_salary_components esc ON esc.id = at.employee_salary_component_id
        JOIN payroll.salary_components sc ON sc.id = esc.component_id
        WHERE at.allowance_run_id = :runId
        AND (:status IS NULL OR at.status = :status)
        AND (:employeeCode IS NULL OR LOWER(at.employee_code) LIKE LOWER(CONCAT('%', :employeeCode, '%')))
        ORDER BY at.employee_id, sc.display_order
        """,
            nativeQuery = true
    )
    List<Object[]> findTransactionsWithFilters(
            @Param("runId") Long runId,
            @Param("status") String status,
            @Param("employeeCode") String employeeCode,
            Pageable pageable
    );

    @Query(value = """
        SELECT COUNT(DISTINCT at.employee_id)
        FROM payroll.allowance_transaction at
        WHERE at.allowance_run_id = :runId
        AND (:status IS NULL OR at.status = :status)
        AND (:employeeCode IS NULL OR LOWER(at.employee_code) LIKE LOWER(CONCAT('%', :employeeCode, '%')))
        """,
            nativeQuery = true
    )
    long countDistinctEmployees(
            @Param("runId") Long runId,
            @Param("status") String status,
            @Param("employeeCode") String employeeCode
    );

    @Modifying
    @Query("""
        UPDATE AllowanceTransaction at
        SET at.status = :status
        WHERE at.allowanceRunId = :allowanceRunId
    """)
    int updateStatusByAllowanceRunId(
            @Param("allowanceRunId") Long allowanceRunId,
            @Param("status") AllowanceTransactionStatus status
    );
}
