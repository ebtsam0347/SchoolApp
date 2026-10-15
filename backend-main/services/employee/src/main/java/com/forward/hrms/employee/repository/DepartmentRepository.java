package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.Department;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    boolean existsByCode(String code);
    boolean existsByHeadId(Long headId);
    Optional<Department> findByCode(String code);
    List<Department> findAllByOrderByNameAsc();
    List<Department> findAllByActiveOrderByNameAsc(boolean active);
}
