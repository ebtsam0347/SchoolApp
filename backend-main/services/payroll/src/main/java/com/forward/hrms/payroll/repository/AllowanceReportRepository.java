package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.AllowanceTransaction;
import com.forward.hrms.payroll.repository.projection.EmployeeAmountProjection;
import com.forward.hrms.payroll.repository.projection.EmployeeCodeAmountProjection;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Read-only loads behind the allowance accounts reports (F-1 … F-7). An
 * allowance reaches an employee two ways, and a month's report sums both:
 * <ul>
 *   <li>as a payslip line — how legacy-imported months carry it;</li>
 *   <li>as an allowance-run transaction — how this system pays the
 *       allowances flagged {@code calculate_with_salary = false}, apart from
 *       salary.</li>
 * </ul>
 * Only computed runs count ({@code COMPUTED}, {@code APPROVED},
 * {@code POSTING}, {@code POSTED}) — the same cut as the salary reports.
 */
public interface AllowanceReportRepository extends Repository<AllowanceTransaction, Long> {

    @Query("""
            select p.employeeId as employeeId, sum(l.amount) as amount
            from PayslipLine l, Payslip p, PayrollRun r
            where p.id = l.payslipId and r.id = p.payrollRunId
              and r.periodYear = :year and r.periodMonth = :month
              and r.status in (com.forward.hrms.payroll.enums.PayrollRunStatus.COMPUTED,
                               com.forward.hrms.payroll.enums.PayrollRunStatus.APPROVED,
                               com.forward.hrms.payroll.enums.PayrollRunStatus.POSTING,
                               com.forward.hrms.payroll.enums.PayrollRunStatus.POSTED)
              and l.code = :code
            group by p.employeeId
            """)
    List<EmployeeAmountProjection> sumPayslipLines(@Param("year") int year, @Param("month") int month,
                                                   @Param("code") String code);

    @Query("""
            select t.employeeId as employeeId, sum(t.amount) as amount
            from AllowanceTransaction t, AllowanceRun r, EmployeeSalaryComponent esc, SalaryComponent sc
            where r.id = t.allowanceRunId
              and esc.id = t.employeeSalaryComponentId
              and sc.id = esc.componentId
              and r.allowanceYear = :year and r.allowanceMonth = :month
              and r.status in (com.forward.hrms.payroll.enums.AllowanceRunStatus.COMPUTED,
                               com.forward.hrms.payroll.enums.AllowanceRunStatus.APPROVED,
                               com.forward.hrms.payroll.enums.AllowanceRunStatus.POSTING,
                               com.forward.hrms.payroll.enums.AllowanceRunStatus.POSTED)
              and sc.code = :code
            group by t.employeeId
            """)
    List<EmployeeAmountProjection> sumAllowanceRun(@Param("year") int year, @Param("month") int month,
                                                   @Param("code") String code);

    /** {@link #sumPayslipLines} for several component codes at once, per employee and code. */
    @Query("""
            select p.employeeId as employeeId, l.code as code, sum(l.amount) as amount
            from PayslipLine l, Payslip p, PayrollRun r
            where p.id = l.payslipId and r.id = p.payrollRunId
              and r.periodYear = :year and r.periodMonth = :month
              and r.status in (com.forward.hrms.payroll.enums.PayrollRunStatus.COMPUTED,
                               com.forward.hrms.payroll.enums.PayrollRunStatus.APPROVED,
                               com.forward.hrms.payroll.enums.PayrollRunStatus.POSTING,
                               com.forward.hrms.payroll.enums.PayrollRunStatus.POSTED)
              and l.code in :codes
            group by p.employeeId, l.code
            """)
    List<EmployeeCodeAmountProjection> sumPayslipLinesByCode(@Param("year") int year, @Param("month") int month,
                                                             @Param("codes") Collection<String> codes);

    /** {@link #sumAllowanceRun} for several component codes at once, per employee and code. */
    @Query("""
            select t.employeeId as employeeId, sc.code as code, sum(t.amount) as amount
            from AllowanceTransaction t, AllowanceRun r, EmployeeSalaryComponent esc, SalaryComponent sc
            where r.id = t.allowanceRunId
              and esc.id = t.employeeSalaryComponentId
              and sc.id = esc.componentId
              and r.allowanceYear = :year and r.allowanceMonth = :month
              and r.status in (com.forward.hrms.payroll.enums.AllowanceRunStatus.COMPUTED,
                               com.forward.hrms.payroll.enums.AllowanceRunStatus.APPROVED,
                               com.forward.hrms.payroll.enums.AllowanceRunStatus.POSTING,
                               com.forward.hrms.payroll.enums.AllowanceRunStatus.POSTED)
              and sc.code in :codes
            group by t.employeeId, sc.code
            """)
    List<EmployeeCodeAmountProjection> sumAllowanceRunByCode(@Param("year") int year, @Param("month") int month,
                                                             @Param("codes") Collection<String> codes);
}
