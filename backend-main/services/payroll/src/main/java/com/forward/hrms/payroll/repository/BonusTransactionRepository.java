package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.BonusTransaction;
import com.forward.hrms.payroll.enums.BonusTransactionPaymentMode;
import com.forward.hrms.payroll.enums.BonusTransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public interface BonusTransactionRepository extends JpaRepository<BonusTransaction, Long> {

    @Query("""
            select t.employeeId from BonusTransaction t
            where t.fiscalYearCode = :fiscalYearCode and t.bonusNo = :bonusNo
            """)
        Set<Long> findEmployeeIdsByFiscalYearCodeAndBonusNo(String fiscalYearCode, int bonusNo);

    @Query("""
            select t from BonusTransaction t
            where t.fiscalYearCode = :fiscalYearCode
              and t.bonusNo = :bonusNo
              and (:status is null or t.status = :status)
              and (:paymentMode is null or t.bonusTransactionPaymentMode = :paymentMode)
              and (:q is null or lower(t.employeeCode) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<BonusTransaction> search(String fiscalYearCode, int bonusNo, BonusTransactionStatus status,
                                  BonusTransactionPaymentMode paymentMode, String q, Pageable pageable);

    /**
     * Gross bonus already granted to this employee for a fiscal year,
     * across every bonus run (bonus_no 1..4) of that year.
     *
     * <p>{@code PAID} and {@code RELEASED} are the two states in which the
     * money has actually reached the employee — {@code BonusNoSettingsService.approve}
     * flips a whole run to PAID, and a HELD row that is later let go ends
     * up RELEASED. {@code DRAFT}, {@code HELD} and {@code RELEASE_PENDING}
     * are deliberately excluded: nothing has been released, so they cannot
     * reduce what a leaver is still owed.
     *
     * <p>Sums {@code percentageSalaryAmount} (the bonus earned) rather than
     * {@code netAmount} (after long-term-advance recovery). The settlement
     * nets this off a gross entitlement, so the gross figure is the
     * comparable one; the two are equal today because
     * {@code deductionAsLongTermAdvance} is always zero.
     *
     * <p>Consumed by {@code SettlementBonusCalculator}.
     */
    @Query("""
            select coalesce(sum(t.percentageSalaryAmount),0)
            from BonusTransaction t
            where t.employeeId = :employeeId
              and t.fiscalYearCode = :fiscalYearCode
              and t.status in (com.forward.hrms.payroll.enums.BonusTransactionStatus.PAID,
                               com.forward.hrms.payroll.enums.BonusTransactionStatus.RELEASED)
            """)
    BigDecimal totalPaidForFiscalYear(
            @Param("employeeId") Long employeeId,
            @Param("fiscalYearCode") String fiscalYearCode);

    int countByFiscalYearCodeAndBonusNo(String fiscalYearCode, int bonusNo);

    List<BonusTransaction> findAllByFiscalYearCodeAndBonusNoAndRfdAdvanceAddedGreaterThan(
            String fiscalYearCode,
            int bonusNo,
            BigDecimal rfdAdvanceAdded
    );

    @Modifying
    @Query("""
            delete from BonusTransaction t
            where t.fiscalYearCode = :fiscalYearCode and t.bonusNo = :bonusNo
            """)
    int deleteAllByFiscalYearCodeAndBonusNo(String fiscalYearCode, int bonusNo);

    @Modifying
    @Query("""
    UPDATE BonusTransaction t
    SET t.status = :bonusTransactionStatus,
        t.paidAt = :dateTime
    WHERE t.fiscalYearCode = :fiscalYearCode
      AND t.bonusNo = :bonusNo
""")
    void approveAllBonusTransactionByFiscalYearAndBonusNo(
            @Param("fiscalYearCode") String fiscalYearCode,
            @Param("bonusNo") int bonusNo,
            @Param("bonusTransactionStatus") BonusTransactionStatus bonusTransactionStatus,
            @Param("dateTime") LocalDateTime dateTime
    );
}
