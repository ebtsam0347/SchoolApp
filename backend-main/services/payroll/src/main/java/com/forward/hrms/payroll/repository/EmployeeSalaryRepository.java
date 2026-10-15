package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.EmployeeSalary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeSalaryRepository extends JpaRepository<EmployeeSalary, Long> {

    /** Returns the open-ended (current) row for an employee, if any. */
    @Query("""
            select s from EmployeeSalary s
            where s.employeeId = :employeeId and s.effectiveTo is null
            """)
    Optional<EmployeeSalary> findCurrent(long employeeId);

    /** Most-recent first; the current row (effective_to is null) sits at the top. */
    List<EmployeeSalary> findAllByEmployeeIdOrderByEffectiveFromDesc(long employeeId);

    /** Salary effective on the given date — the row whose range contains it. */
    @Query("""
            select s from EmployeeSalary s
            where s.employeeId = :employeeId
              and s.effectiveFrom <= :asOf
              and (s.effectiveTo is null or s.effectiveTo >= :asOf)
            order by s.effectiveFrom desc
            """)
    List<EmployeeSalary> findEffectiveOn(long employeeId, LocalDate asOf);

    /**
     * Rows closed exactly on the given date, most recent first. Used to
     * find the row {@code closeOnExit} closed so termination-undo can
     * reopen it.
     */
    List<EmployeeSalary> findAllByEmployeeIdAndEffectiveToOrderByEffectiveFromDesc(
            long employeeId, LocalDate effectiveTo);
}
