package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmployeePriorExperience;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeePriorExperienceRepository extends JpaRepository<EmployeePriorExperience, Long> {

    List<EmployeePriorExperience> findAllByEmployeeIdOrderByEndDateDescIdAsc(Long employeeId);
}
