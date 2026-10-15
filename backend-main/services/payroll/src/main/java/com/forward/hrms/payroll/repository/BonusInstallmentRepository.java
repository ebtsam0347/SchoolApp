package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.BonusInstallment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BonusInstallmentRepository extends JpaRepository<BonusInstallment, Long> {

    List<BonusInstallment> findAllByBonusEventIdOrderByInstallmentNo(long eventId);

    Optional<BonusInstallment> findByBonusEventIdAndInstallmentNo(long eventId, int no);

    @Query("""
            select coalesce(sum(b.percentage), 0) from BonusInstallment b
            where b.bonusEventId = :eventId
            """)
    BigDecimal sumPercentageForEvent(long eventId);
}
