package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.enums.ComponentType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SalaryComponentRepository extends JpaRepository<SalaryComponent, Long> {

    Optional<SalaryComponent> findByCode(String code);

    boolean existsByCode(String code);

    @Query("""
            select c from SalaryComponent c
            where (:type   is null or c.type = :type)
              and (:active is null or c.active = :active)
            order by c.displayOrder, c.code
            """)
    List<SalaryComponent> filter(ComponentType type, Boolean active);

    // find all active salary components.
    List<SalaryComponent> findAllByActiveTrue();

    /** Active allowances paid apart from salary — the columns of the allowance screens. */
    @Query("""
            select c from SalaryComponent c
            where c.active = true and c.calculateWithSalary = false
            order by c.displayOrder, c.code
            """)
    List<SalaryComponent> findAllowanceComponents();
}
