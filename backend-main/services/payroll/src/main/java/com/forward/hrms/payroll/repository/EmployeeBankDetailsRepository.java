package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.EmployeeBankDetails;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeBankDetailsRepository extends JpaRepository<EmployeeBankDetails, Long> {

    Optional<EmployeeBankDetails> findByEmployeeId(long employeeId);

    boolean existsByEmployeeId(long employeeId);

    void deleteByEmployeeId(long employeeId);

    List<EmployeeBankDetails> findByEmployeeIdIn(Collection<Long> employeeIds);
}
