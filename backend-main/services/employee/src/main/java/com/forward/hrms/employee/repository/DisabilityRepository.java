package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.Disability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisabilityRepository extends JpaRepository<Disability, Long> {

    Optional<Disability> findByCode(String code);

    boolean existsByCode(String code);

    List<Disability> findAllByActiveTrueOrderByCodeAsc();

    List<Disability> findAllByOrderByCodeAsc();
}
