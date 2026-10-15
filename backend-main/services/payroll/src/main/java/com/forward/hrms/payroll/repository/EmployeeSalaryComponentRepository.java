package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.enums.ComponentType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeSalaryComponentRepository extends JpaRepository<EmployeeSalaryComponent, Long> {

    /**
     * All current (open-ended) lines for an employee, optionally filtered by EARNING / DEDUCTION.
     */
    @Query("""
            select esc from EmployeeSalaryComponent esc
              join SalaryComponent c on c.id = esc.componentId
            where esc.employeeId = :employeeId
              and esc.effectiveTo is null
              and (:type is null or c.type = :type)
            order by c.displayOrder, c.code
            """)
    List<EmployeeSalaryComponent> findCurrent(long employeeId, ComponentType type);

    // new method
    @Query("""
            select esc
            from EmployeeSalaryComponent esc
            where esc.employeeId=:employeeId
            and esc.effectiveFrom<=:asOf
            and (
                esc.effectiveTo is null
                or esc.effectiveTo>=:asOf
            )
            """)
    List<EmployeeSalaryComponent> findEffectiveOn(long employeeId, LocalDate asOf);

    /**
     * Current open-ended row for a specific catalog code (e.g. INCOME_TAX), if any.
     */
    @Query("""
            select esc from EmployeeSalaryComponent esc
              join SalaryComponent c on c.id = esc.componentId
            where esc.employeeId = :employeeId
              and esc.effectiveTo is null
              and c.code = :code
            """)
    Optional<EmployeeSalaryComponent> findCurrentByCode(long employeeId, String code);

    /**
     * Whole-history list, most recent first.
     */
    List<EmployeeSalaryComponent> findAllByEmployeeIdOrderByEffectiveFromDesc(long employeeId);

    /**
     * Lines whose [effective_from..effective_to] window overlaps the given period.
     */
    @Query("""
            select esc from EmployeeSalaryComponent esc
            where esc.employeeId = :employeeId
              and esc.effectiveFrom <= :periodEnd
              and (esc.effectiveTo is null or esc.effectiveTo >= :periodStart)
            """)
    List<EmployeeSalaryComponent> findEffectiveDuring(long employeeId,
                                                      LocalDate periodStart,
                                                      LocalDate periodEnd);

    List<EmployeeSalaryComponent> findByEmployeeIdAndEffectiveToIsNull(long employeeId);


    /** One employee's open allowance lines. */
    @Query("""
            select esc from EmployeeSalaryComponent esc
              join SalaryComponent c on c.id = esc.componentId
            where esc.employeeId = :employeeId
              and esc.effectiveTo is null
              and c.calculateWithSalary = false
            """)
    List<EmployeeSalaryComponent> findOpenAllowanceLines(long employeeId);

    /** One employee's allowance lines, open and closed — the input to the version history. */
    @Query("""
            select esc from EmployeeSalaryComponent esc
              join SalaryComponent c on c.id = esc.componentId
            where esc.employeeId = :employeeId
              and c.calculateWithSalary = false
            """)
    List<EmployeeSalaryComponent> findAllowanceLines(long employeeId);

    /**
     * Every employee's lines on allowances paid apart from salary
     * (calculate_with_salary = false), open and closed.
     */
    @Query("""
            select esc from EmployeeSalaryComponent esc
              join SalaryComponent c on c.id = esc.componentId
            where c.calculateWithSalary = false
            """)
    List<EmployeeSalaryComponent> findAllowanceLines();

    /**
     * Rows closed exactly on the given date — used to find the lines
     * {@code closeOnExit} closed so termination-undo can reopen them.
     */
    List<EmployeeSalaryComponent> findByEmployeeIdAndEffectiveTo(long employeeId, LocalDate effectiveTo);
}