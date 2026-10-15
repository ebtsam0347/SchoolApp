package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.EmployeeSkill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Long> {

    List<EmployeeSkill> findAllByEmployeeIdOrderByIdAsc(Long employeeId);

    List<EmployeeSkill> findAllBySkillIdOrderByEmployeeIdAsc(Long skillId);

    Optional<EmployeeSkill> findByEmployeeIdAndSkillId(Long employeeId, Long skillId);
}
