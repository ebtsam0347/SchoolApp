package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.Bank;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankRepository extends JpaRepository<Bank, Long> {

    Optional<Bank> findByCode(String code);

    boolean existsByCode(String code);

    List<Bank> findAllByActiveTrueOrderByCodeAsc();

    List<Bank> findAllByOrderByCodeAsc();
}
