package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.PermanentAdvanceDeduction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface PermanentAdvanceDeductionRepository extends JpaRepository<PermanentAdvanceDeduction, Long> {

    /** An employee's whole permanent-advance ledger, newest first. */
    List<PermanentAdvanceDeduction> findAllByEmployeeIdOrderByTransactionDateDescIdDesc(Long employeeId);

    /**
     * Undo a source: deletes every row it wrote — a reset payroll run
     * (SALARY + run id), a reversed final settlement (SETTLEMENT + settlement
     * id), a reverted bonus / annual-leave transaction.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            delete from PermanentAdvanceDeduction d
            where d.source = :source
              and d.sourceRefId = :sourceRefId
            """)
    int deleteBySource(com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource source, long sourceRefId);

    /** {@link #deleteBySource} narrowed to one employee — a single payslip undone on a run. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            delete from PermanentAdvanceDeduction d
            where d.source = :source
              and d.sourceRefId = :sourceRefId
              and d.employeeId = :employeeId
            """)
    int deleteBySourceForEmployee(com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource source,
                                  long sourceRefId, long employeeId);

    @Query("""
            select coalesce(sum(d.deductedAmount), 0)
            from PermanentAdvanceDeduction d
            where d.employeeId = :employeeId
            """)
    BigDecimal employeeTotalPermanentAdvanceDeduction(Long employeeId);

    /** {@link #employeeTotalPermanentAdvanceDeduction} for a page of employees. */
    @Query("""
            select d.employeeId as employeeId, coalesce(sum(d.deductedAmount), 0) as appliedAmount
            from PermanentAdvanceDeduction d
            where d.employeeId in :employeeIds
            group by d.employeeId
            """)
    List<com.forward.hrms.payroll.repository.projection.EmployeeAppliedAdvanceProjection> totalDeductedByEmployeeIds(
            Collection<Long> employeeIds);

    /** Salary installments a payroll run recorded, per employee (salary-slip advances block). Read-only. */
    @Query("""
            select d.employeeId as employeeId, coalesce(sum(d.deductedAmount), 0) as appliedAmount
            from PermanentAdvanceDeduction d
            where d.employeeId in :employeeIds
              and d.source = com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource.SALARY
              and d.sourceRefId = :runId
            group by d.employeeId
            """)
    List<com.forward.hrms.payroll.repository.projection.EmployeeAppliedAdvanceProjection> deductedInRunByEmployeeIds(
            Collection<Long> employeeIds, Long runId);
}
