package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.ShortTermAdvance;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read-only load behind the temporary-advance accounts reports (B1–B5).
 *
 * <p>"Paid" = the line's batch is {@code APPROVED} and the line itself is
 * {@code APPROVED} — the same rule the payroll run deducts on. Lines a final
 * settlement recovered stay {@code APPROVED} and still count.
 *
 * <p>Months are compared as {@code year * 12 + month} so a range spanning a
 * year boundary works.
 */
public interface ShortTermAdvanceReportRepository extends Repository<ShortTermAdvance, Long> {

    @Query("""
            select s from ShortTermAdvance s, ShortTermAdvanceBatch b
            where b.id = s.batchId
              and b.status = com.forward.hrms.payroll.enums.AdvanceStatus.APPROVED
              and s.status = com.forward.hrms.payroll.enums.ShortTermAdvanceStatus.APPROVED
              and s.targetYear * 12 + s.targetMonth between :fromKey and :toKey
            order by s.employeeId, s.targetYear, s.targetMonth, s.id
            """)
    List<ShortTermAdvance> findPaidBetween(@Param("fromKey") int fromMonthKey, @Param("toKey") int toMonthKey);
}
