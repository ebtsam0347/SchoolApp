package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmployeeQualification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeQualificationRepository extends JpaRepository<EmployeeQualification, Long> {

    List<EmployeeQualification> findAllByEmployeeIdOrderByYearOfCompletionDescIdAsc(Long employeeId);

    List<EmployeeQualification> findAllByQualificationIdOrderByEmployeeIdAsc(Long qualificationId);
}
