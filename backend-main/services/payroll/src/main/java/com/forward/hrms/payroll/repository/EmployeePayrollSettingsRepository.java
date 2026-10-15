package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.EmployeePayrollSettings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeePayrollSettingsRepository extends JpaRepository<EmployeePayrollSettings, Long> {

    Optional<EmployeePayrollSettings> findByEmployeeId(long employeeId);

    boolean existsByEmployeeId(long employeeId);

    java.util.List<EmployeePayrollSettings> findAllByEmployeeIdIn(java.util.Collection<Long> employeeIds);

    /** Regular-run employee selection (Audit Finding 4) — excludes
     *  terminated employees so they don't get a payslip alongside their
     *  final settlement. */
    java.util.List<EmployeePayrollSettings> findAllByActiveTrue();
}
