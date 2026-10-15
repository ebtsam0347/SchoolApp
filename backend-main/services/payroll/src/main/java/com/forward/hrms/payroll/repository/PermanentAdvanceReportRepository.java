package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.PermanentAdvanceDeduction;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Read-only loads behind the permanent-advance accounts reports (C1–C11).
 * The ledger is small (a few hundred employees, tens of thousands of rows),
 * so the reports aggregate in Java rather than via one bespoke SQL per
 * report — same numbers, one place to read the rules.
 *
 * <p>"Paid" advances are {@code APPROVED}, including ones a final
 * settlement recovered ({@code closed_at} set): such an
 * advance was still paid out and still belongs in the ledger and balances.
 */
public interface PermanentAdvanceReportRepository extends Repository<PermanentAdvance, Long> {

    @Query("""
            select a from PermanentAdvance a
            where a.status = com.forward.hrms.payroll.enums.AdvanceStatus.APPROVED
            order by a.employeeId, a.receivingDate, a.id
            """)
    List<PermanentAdvance> findPaid();

    @Query("""
            select d from PermanentAdvanceDeduction d
            order by d.employeeId, d.transactionDate, d.id
            """)
    List<PermanentAdvanceDeduction> findAllDeductions();
}
