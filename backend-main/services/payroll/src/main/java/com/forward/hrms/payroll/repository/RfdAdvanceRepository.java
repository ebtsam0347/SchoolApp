package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.RfdAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
public interface RfdAdvanceRepository extends JpaRepository<RfdAdvance, Long> {

    /** RFD to deduct for a month: every row dated within {@code [from, to]}. */
    @Query("""
            select coalesce(sum(r.amount), 0)
              from RfdAdvance r
             where r.employeeId = :employeeId
               and r.transactionDate between :from and :to
            """)
    BigDecimal sumForPeriod(long employeeId, LocalDate from, LocalDate to);

    /**
     * Run reset: delete the "From Salary" rows the run's compute created —
     * one per payslip, dated that payslip's created date. Must run before
     * the payslips are deleted.
     */
    @Modifying
    @Query(value = """
            delete from rfd_advance r
             using payslips p
             where p.payroll_run_id = :runId
               and r.employee_id = p.employee_id
               and r.transaction_date = cast(p.created_at as date)
               and r.transaction_state like 'From Salary %'
            """, nativeQuery = true)
    int deleteCreatedByRun(long runId);

    /**
     * Payslip undo: delete the one "From Salary" row a payslip's compute
     * created — same employee, dated the payslip's created date, labelled
     * with the run's month ({@code From Salary September 2026}).
     */
    @Modifying
    @Query("""
            delete from RfdAdvance r
             where r.employeeId = :employeeId
               and r.transactionDate = :transactionDate
               and r.transactionState = :transactionState
            """)
    int deleteFromSalary(long employeeId, LocalDate transactionDate, String transactionState);
}
