package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.PayrollConfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollConfigRepository extends JpaRepository<PayrollConfig, Long> {

    @Query("""
            select s from PayrollConfig s
            where s.effectiveFrom <= :asOf
              and (s.effectiveTo is null or s.effectiveTo >= :asOf)
            order by s.effectiveFrom desc
            """)
    List<PayrollConfig> findEffectiveOn(LocalDate asOf);

    default Optional<PayrollConfig> findEffectiveOnDate(LocalDate asOf) {
        List<PayrollConfig> rows = findEffectiveOn(asOf);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    List<PayrollConfig> findAllByOrderByEffectiveFromDesc();
}
