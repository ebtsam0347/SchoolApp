package com.forward.hrms.payroll.repository;

import com.forward.hrms.payroll.entity.OffCyclePayment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OffCyclePaymentRepository extends JpaRepository<OffCyclePayment, Long> {

    /**
     * {@code source} and {@code status} are bound as Strings and compared
     * through {@code str(...)}: Postgres cannot infer the type of a null
     * bind against an enum-mapped column, so the pass-null-for-"any"
     * branch breaks if the parameter is typed as the enum itself.
     */
    @Query("""
               select p from OffCyclePayment p
            where (:employeeId is null or p.employeeId = :employeeId)
              and p.paidOn >= coalesce(:from, p.paidOn)
              and p.paidOn <= coalesce(:to, p.paidOn)
              and (:source is null or str(p.source) = :source)
              and (:status is null or str(p.status) = :status)
            """)
    Page<OffCyclePayment> filter(Long employeeId, LocalDate from, LocalDate to,
                                 String source, String status, Pageable pageable);

    List<OffCyclePayment> findAllByEmployeeIdAndPaidOnBetweenOrderByPaidOnDesc(
            long employeeId, LocalDate from, LocalDate to);

    List<OffCyclePayment> findAllByEmployeeIdOrderByPaidOnDesc(long employeeId);

    boolean existsBySourceInstallmentId(long sourceInstallmentId);

    Optional<OffCyclePayment> findBySourceInstallmentIdAndEmployeeId(long sourceInstallmentId, long employeeId);

    /**
     * Bonus money actually released to this employee.
     *
     * <p>Keyed on {@code source = BONUS}, which only
     * {@code recordBonusInstallment} produces. Identical rows to the
     * pre-V0057 {@code category = 'BONUS'} match — same column, same
     * stored strings, and V0057 leaves BONUS rows untouched. An
     * HR-created payment is {@code HR} however its selected master type
     * is named, so HR cannot move money into or out of this figure by
     * naming a dropdown entry "Bonus".
     *
     * <p>{@code status = PAID} excludes authorized-but-unpaid and
     * canceled rows: neither has been released, so neither can reduce
     * what a leaver is still owed. Bonus rows are born PAID and no API
     * path moves them off it, so today this is defense in depth.
     */
    @Query("""
                select coalesce(sum(o.amount),0)
                from OffCyclePayment o
                where o.employeeId=:employeeId
                  and o.source = com.forward.hrms.payroll.enums.OffCyclePaymentSource.BONUS
                  and o.status = com.forward.hrms.payroll.enums.OffCyclePaymentStatus.PAID
            """)
    BigDecimal totalBonusPaid(Long employeeId);

    /**
     * Per-event counterpart of {@link #totalBonusPaid}; same rules.
     */
    @Query("""
            select coalesce(sum(o.amount),0)
            from OffCyclePayment o
            where o.employeeId = :employeeId
            and o.sourceEventId = :eventId
            and o.source = com.forward.hrms.payroll.enums.OffCyclePaymentSource.BONUS
            and o.status = com.forward.hrms.payroll.enums.OffCyclePaymentStatus.PAID
            """)
    BigDecimal totalBonusPaidForEvent(
            Long employeeId,
            Long eventId);


    /**
     * Bonus money already released to this employee <em>for a given
     * fiscal year</em> — the sum of every PAID bonus installment whose
     * {@code BonusEvent} belongs to that year.
     *
     * <p>Window is matched on the <em>event's</em> reference date, not on
     * {@code paidOn}: an Eid that falls in late June can legitimately be
     * disbursed in early July, and that money still belongs to the fiscal
     * year the event was declared in. Matching on {@code paidOn} would
     * charge it to the wrong year on both sides of the 1 July boundary.
     *
     * <p>Same {@code source}/{@code status} rules as
     * {@link #totalBonusPaidForEvent}: only system-generated BONUS rows
     * that have actually been released count. Every such row carries a
     * non-null {@code sourceEventId} (see
     * {@code OffCyclePaymentService.recordBonusInstallment}), so the join
     * drops nothing.
     *
     * <p>Consumed by {@code SettlementBonusCalculator} to net the
     * fiscal-year bonus accrual down to what a leaver is still owed.
     */
    @Query("""
            select coalesce(sum(o.amount),0)
            from OffCyclePayment o
            join BonusEvent e
                on e.id = o.sourceEventId
            where o.employeeId = :employeeId
            and o.source = com.forward.hrms.payroll.enums.OffCyclePaymentSource.BONUS
            and o.status = com.forward.hrms.payroll.enums.OffCyclePaymentStatus.PAID
            and e.referenceDate >= :fiscalYearStart
            and e.referenceDate <= :fiscalYearEnd
            """)
    BigDecimal totalBonusPaidForFiscalYear(
            Long employeeId,
            LocalDate fiscalYearStart,
            LocalDate fiscalYearEnd);


    @Query("""
            select p
            from OffCyclePayment p
            where p.paidOn >= :from
              and p.paidOn <= :to
              and (:source is null or str(p.source) = :source)
            """)
    Page<OffCyclePayment> findForReport(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("source") String source,
            Pageable pageable);
}
