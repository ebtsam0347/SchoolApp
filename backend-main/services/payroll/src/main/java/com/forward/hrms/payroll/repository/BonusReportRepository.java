package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.BonusTransaction;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read-only load behind the bonus accounts reports (E2, E3, E7).
 *
 * <p>A bonus is reportable once its {@code bonus_no_settings} row has been
 * computed ({@code COMPUTED}, {@code APPROVED}, {@code POSTING},
 * {@code POSTED}) — the same cut the salary reports use for payroll runs.
 * Every transaction of that bonus is included, whatever its own status.
 */
public interface BonusReportRepository extends Repository<BonusTransaction, Long> {

    @Query("""
            select t from BonusTransaction t, BonusNoSettings s
            where s.fiscalYearCode = t.fiscalYearCode and s.bonusNo = t.bonusNo
              and t.fiscalYearCode = :fiscalYearCode and t.bonusNo = :bonusNo
              and s.status in (com.forward.hrms.payroll.enums.BonusNoSettingsStatus.COMPUTED,
                               com.forward.hrms.payroll.enums.BonusNoSettingsStatus.APPROVED,
                               com.forward.hrms.payroll.enums.BonusNoSettingsStatus.POSTING,
                               com.forward.hrms.payroll.enums.BonusNoSettingsStatus.POSTED)
            order by t.employeeId, t.id
            """)
    List<BonusTransaction> findComputed(@Param("fiscalYearCode") String fiscalYearCode,
                                        @Param("bonusNo") int bonusNo);
}
