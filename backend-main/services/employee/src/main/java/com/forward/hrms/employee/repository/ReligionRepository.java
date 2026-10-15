package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.Religion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReligionRepository extends JpaRepository<Religion, Long> {

    Optional<Religion> findByNameIgnoreCase(String name);
    List<Religion> findAllByOrderByNameAsc();
    List<Religion> findAllByActiveOrderByNameAsc(Boolean active);
    boolean existsByNameIgnoreCase(String name);
}