package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.AnnualLeaveTransaction;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.Payslip;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Read-only loads behind the salary accounts reports (A2, A4, A5, A7, A9).
 *
 * <p>A month's salary is the payslips of its payroll run once the run has
 * been computed ({@code COMPUTED}, {@code APPROVED}, {@code POSTING},
 * {@code POSTED}); draft, locked, still-computing, failed and cancelled
 * runs have no salary to report. Every payslip of the run is included,
 * whatever its own status (paid, held, released).
 */
public interface SalaryReportRepository extends Repository<Payslip, Long> {

    @Query("""
            select p from Payslip p, PayrollRun r
            where r.id = p.payrollRunId
              and r.periodYear = :year and r.periodMonth = :month
              and r.status in (com.forward.hrms.payroll.enums.PayrollRunStatus.COMPUTED,
                               com.forward.hrms.payroll.enums.PayrollRunStatus.APPROVED,
                               com.forward.hrms.payroll.enums.PayrollRunStatus.POSTING,
                               com.forward.hrms.payroll.enums.PayrollRunStatus.POSTED)
            order by p.employeeId, p.id
            """)
    List<Payslip> findComputedPayslips(@Param("year") int year, @Param("month") int month);

    /** Contract basic effective on {@code asOf}, per employee (latest row wins in the caller). */
    @Query("""
            select s from EmployeeSalary s
            where s.employeeId in :employeeIds
              and s.effectiveFrom <= :asOf
              and (s.effectiveTo is null or s.effectiveTo >= :asOf)
            order by s.employeeId, s.effectiveFrom
            """)
    List<EmployeeSalary> findEffectiveOn(@Param("employeeIds") Collection<Long> employeeIds,
                                         @Param("asOf") LocalDate asOf);

    /** Annual-leave encashment rows calculated for a month ({@code calculation_month} within it). */
    @Query("""
            select t from AnnualLeaveTransaction t
            where t.calculationMonth >= :from and t.calculationMonth <= :to
            order by t.employeeId, t.id
            """)
    List<AnnualLeaveTransaction> findAnnualLeaveBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
