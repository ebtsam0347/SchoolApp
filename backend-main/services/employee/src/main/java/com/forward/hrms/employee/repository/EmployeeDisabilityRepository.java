package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmployeeDisability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDisabilityRepository extends JpaRepository<EmployeeDisability, Long> {

    List<EmployeeDisability> findAllByEmployeeIdOrderByIdAsc(Long employeeId);

    boolean existsByEmployeeIdAndDisabilityId(Long employeeId, Long disabilityId);
}
