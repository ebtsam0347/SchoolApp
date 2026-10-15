package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.BonusNoSettings;
import com.forward.hrms.payroll.enums.BonusNoSettingsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BonusNoSettingsRepository extends JpaRepository<BonusNoSettings, Long> {

    boolean existsByFiscalYearCodeAndBonusNo(String fiscalYearCode, int bonusNo);

    Optional<BonusNoSettings> findByFiscalYearCodeAndBonusNo(String fiscalYearCode, int bonusNo);

    @Query("""
            select s from BonusNoSettings s
            where (:fiscalYearCode is null or s.fiscalYearCode = :fiscalYearCode)
              and (:status is null or s.status = :status)
            order by s.fiscalYearCode desc, s.bonusNo asc
            """)
    List<BonusNoSettings> filter(String fiscalYearCode, BonusNoSettingsStatus status);
}
