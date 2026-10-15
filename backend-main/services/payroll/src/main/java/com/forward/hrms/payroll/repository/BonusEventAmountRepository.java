package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.BonusEventAmount;

import com.forward.hrms.payroll.settlement.context.SettlementBonusRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BonusEventAmountRepository extends JpaRepository<BonusEventAmount, Long> {

    List<BonusEventAmount> findAllByBonusEventIdOrderByEmployeeId(long eventId);

    List<BonusEventAmount> findAllByEmployeeIdOrderByIdDesc(long employeeId);

    boolean existsByBonusEventId(long eventId);

    @Modifying
    @Query("delete from BonusEventAmount a where a.bonusEventId = :eventId")
    void deleteAllByBonusEventId(long eventId);

    // new methods ****************
    @Query("""
            select coalesce(sum(a.fullBonus),0)
            from BonusEventAmount a
            join BonusEvent e
              on e.id = a.bonusEventId
            where a.employeeId = :employeeId
              and e.status = com.forward.hrms.payroll.enums.BonusEventStatus.ACTIVE
            """)
    BigDecimal totalEligibleBonus(Long employeeId);

    @Query("""
                select coalesce(sum(a.fullBonus), 0)
                from BonusEventAmount a
                join BonusEvent e
                  on e.id = a.bonusEventId
                where a.employeeId = :employeeId
                  and e.status = com.forward.hrms.payroll.enums.BonusEventStatus.ACTIVE
                  and e.eventType in (
                      com.forward.hrms.payroll.enums.BonusEventType.EID_UL_FITR,
                      com.forward.hrms.payroll.enums.BonusEventType.EID_UL_ADHA
                  )
            """)
    BigDecimal totalEligibleSettlementBonus(Long employeeId);


    @Query("""
            select a
            from BonusEventAmount a
            join BonusEvent e
                on e.id = a.bonusEventId
            where a.employeeId = :employeeId
            and e.status = com.forward.hrms.payroll.enums.BonusEventStatus.ACTIVE
            and e.referenceDate <= :lastWorkingDay
            and e.eventType in (
                com.forward.hrms.payroll.enums.BonusEventType.EID_UL_FITR,
                com.forward.hrms.payroll.enums.BonusEventType.EID_UL_ADHA
            )
            """)
    List<BonusEventAmount> findSettlementBonusesOld(Long employeeId, LocalDate lastWorkingDay);

    @Query("""
            select a
            from BonusEventAmount a
            join BonusEvent e
                on e.id = a.bonusEventId
            where a.employeeId = :employeeId
            and e.status = com.forward.hrms.payroll.enums.BonusEventStatus.ACTIVE
            and e.referenceDate <= :lastWorkingDay
            and e.eventType in (
                com.forward.hrms.payroll.enums.BonusEventType.EID_UL_FITR,
                com.forward.hrms.payroll.enums.BonusEventType.EID_UL_ADHA
            )
            """)
    List<BonusEventAmount> findSettlementBonusesOldNew(Long employeeId, LocalDate lastWorkingDay);

    @Query("""
            select new com.forward.hrms.payroll.settlement.context.SettlementBonusRow(
                    e,
                    a
            )
            from BonusEventAmount a
            join BonusEvent e
                on e.id = a.bonusEventId
            where a.employeeId = :employeeId
            and e.status = com.forward.hrms.payroll.enums.BonusEventStatus.ACTIVE
            and e.referenceDate <= :lastWorkingDay
            and e.eventType in (
                com.forward.hrms.payroll.enums.BonusEventType.EID_UL_FITR,
                com.forward.hrms.payroll.enums.BonusEventType.EID_UL_ADHA
            )
            order by e.referenceDate
            """)
    List<SettlementBonusRow> findSettlementBonuses(
            Long employeeId,
            LocalDate lastWorkingDay);

    // for reports
    List<BonusEventAmount> findAllByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<BonusEventAmount> findAllByBonusEventId(Long bonusEventId);
    
    Optional<BonusEventAmount> findByBonusEventIdAndEmployeeId(Long bonusEventId, Long employeeId);
}
