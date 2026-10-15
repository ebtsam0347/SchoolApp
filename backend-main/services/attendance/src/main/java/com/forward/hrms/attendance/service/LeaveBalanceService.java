package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.response.*;
import com.forward.hrms.attendance.entity.LeaveBalance;
import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.enums.LeaveCycleAnchor;
import com.forward.hrms.attendance.events.AttendanceEvents;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.LeaveBalanceRepository;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cycle-scoped leave balances per (employee, type, cycle window). The
 * cycle anchor on the leave type decides the window:
 * <ul>
 *   <li>{@code JOINING} — annual leave, anniversary cycle, full from the
 *       joining date, unused encashed at renewal;</li>
 *   <li>{@code FISCAL} — M1 / Casual, Jul–Jun cycle, prorated on a
 *       mid-cycle join, unused lapses at renewal;</li>
 *   <li>{@code NONE} — occasional / statutory, calendar-year window,
 *       full default granted lazily, no scheduled renewal.</li>
 * </ul>
 *
 * <p>The {@code employee.created} consumer provisions the entry cycle;
 * {@link LeaveRenewalJob} opens the next cycle (encashing / lapsing the
 * prior) at each boundary. Lookups during the request flow resolve the
 * cycle window containing the leave date, creating it on the fly if a
 * provisioning / renewal pass hasn't yet (self-healing).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final LeaveBalanceRepository balances;
    private final LeaveTypeRepository leaveTypes;
    private final AttendanceEmployeeRepository attendanceEmployees;
    private final Outbox outbox;
    private final AuditEmitter audit;

    /**
     * Provision the entry-cycle balance for every active type at hire.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void provisionOnHire(long employeeId, LocalDate joiningDate) {
        LocalDate ref = joiningDate != null ? joiningDate : LocalDate.now();
        for (LeaveType t : leaveTypes.findAll()) {
            if (!t.isActive()) continue;
            ensureCycle(employeeId, t, joiningDate, ref);
        }
    }

    /**
     * Balances for the FE. {@code year == null} returns the currently
     * active cycle per type (window contains today); a non-null year
     * returns the historical cycles that started in that calendar year.
     */
    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> myBalances(long employeeId, Integer year) {
        Map<Long, String> codes = leaveTypes.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, LeaveType::getCode, (a, b) -> a));
        List<LeaveBalance> rows;
        if (year == null) {
            LocalDate today = LocalDate.now();
            rows = currentCyclePerType(employeeId, today,
                    balances.findAllByEmployeeIdAndCycleStartLessThanEqualAndCycleEndGreaterThanEqualOrderByLeaveTypeIdAsc(
                            employeeId, today, today));
        } else {
            rows = balances.findAllByEmployeeIdAndYearOrderByLeaveTypeIdAsc(employeeId, year);
        }

//        return rows.stream()
//                .map(b -> LeaveBalanceDtoMapper.of(b, codes.get(b.getLeaveTypeId())))
//                .toList();

        return rows.stream()
                .filter(b -> !"MATERNITY".equalsIgnoreCase(codes.get(b.getLeaveTypeId())))
                .map(b -> LeaveBalanceDtoMapper.of(b, codes.get(b.getLeaveTypeId())))
                .toList();
    }

    // new method to get the annual leaves
    @Transactional(readOnly = true)
    public TerminationLeaveSummaryDto terminationSummaryOld(
            long employeeId,
            LocalDate lastWorkingDay) {

        LeaveType type = leaveTypes.findByCode("ANNUAL")
                .orElseThrow(() -> new NotFoundError(
                        AttendanceErrorCode.LEAVE_TYPE_NOT_FOUND,
                        "Annual leave type not configured"));

        LeaveBalance balance = balances.findCurrentCycle(
                        employeeId,
                        type.getId(),
                        lastWorkingDay)
                .orElseThrow(() -> new NotFoundError(
                        AttendanceErrorCode.LEAVE_BALANCE_NOT_FOUND,
                        "Annual leave balance not found"));

        BigDecimal accrued = balance.getAllocated();

        BigDecimal available = available(balance);

        BigDecimal payable = available.max(BigDecimal.ZERO);

        BigDecimal recoverable =
                available.signum() < 0
                        ? available.abs()
                        : BigDecimal.ZERO;

        return new TerminationLeaveSummaryDto(
                type.getId(),
                type.getCode(),
                type.getName(),
                type.isEncashable(),

                balance.getCycleStart(),
                balance.getCycleEnd(),

                balance.getAllocated(),
                accrued,
                balance.getUsed(),
                balance.getPending(),
                payable,
                recoverable
        );
    }


    // Only these leave types matter for a termination settlement:
    // Annual (encashable, kept separate) and Medical(Medical_M1) + Casual
    // (combined into one bucket). Any other active type (unpaid,
    // maternity, etc.) is irrelevant to the payout/recovery math.
    private static final String CODE_ANNUAL = "ANNUAL";
    private static final String CODE_MEDICAL = "MEDICAL_M1";
    private static final String CODE_CASUAL = "CASUAL";

    @Transactional(readOnly = true)
    public TerminationLeavesSummaryDto terminationLeavesSummary(
            long employeeId,
            LocalDate lastWorkingDay) {

        Map<String, LeaveType> typesByCode = leaveTypes.findAllByActiveTrueOrderByCodeAsc()
                .stream()
                .filter(t -> CODE_ANNUAL.equalsIgnoreCase(t.getCode())
                        || CODE_MEDICAL.equalsIgnoreCase(t.getCode())
                        || CODE_CASUAL.equalsIgnoreCase(t.getCode()))
                .collect(Collectors.toMap(t -> t.getCode().toUpperCase(), Function.identity()));

        TerminationLeaveSummaryDto annualLeave = buildOrNull(
                employeeId,
                typesByCode.get(CODE_ANNUAL),
                lastWorkingDay);

        TerminationLeaveSummaryDto medicalLeave = buildOrNull(
                employeeId,
                typesByCode.get(CODE_MEDICAL),
                lastWorkingDay);

        TerminationLeaveSummaryDto casualLeave = buildOrNull(
                employeeId,
                typesByCode.get(CODE_CASUAL),
                lastWorkingDay);

        return new TerminationLeavesSummaryDto(
                annualLeave,
                combine(medicalLeave, casualLeave)
        );
    }

    private TerminationLeaveSummaryDto buildOrNull(
            long employeeId,
            LeaveType type,
            LocalDate lastWorkingDay) {

        if (type == null) {
            return null;
        }

        return buildTerminationSummary(employeeId, type, lastWorkingDay);
    }

    /**
     * Sums Medical(M1) + Casual balances into one bucket. Either side
     * may be absent (type not configured / no current-cycle balance) —
     * treated as zero contribution rather than failing the whole
     * termination summary.
     */
    private CombinedLeaveSummaryDto combine(
            TerminationLeaveSummaryDto medical,
            TerminationLeaveSummaryDto casual) {

        if (medical == null && casual == null) {
            return null;
        }

        BigDecimal combinedEarned = sum(medical, casual, TerminationLeaveSummaryDto::earned);
        BigDecimal combinedUsed = sum(medical, casual, TerminationLeaveSummaryDto::used);
        BigDecimal combinedPending = sum(medical, casual, TerminationLeaveSummaryDto::pending);

        // recoverableDays must NOT be a straight sum of the per-type
        // recoverableDays: each side is individually floored at zero
        // (calculateRecoverableDays), which discards any unused earned
        // credit on one side that should offset over-usage on the
        // other (e.g. Medical over-used by 4, Casual under-used with
        // 1 day of earned credit unspent -> combined recovery is 3,
        // not 4). Recompute from the combined totals instead.
        BigDecimal combinedConsumed = combinedUsed.add(combinedPending);
        BigDecimal combinedRecoverable = combinedConsumed
                .subtract(combinedEarned)
                .max(BigDecimal.ZERO);

        return new CombinedLeaveSummaryDto(
                sum(medical, casual, TerminationLeaveSummaryDto::allocated),
                combinedEarned,
                combinedUsed,
                combinedPending,
                sum(medical, casual, TerminationLeaveSummaryDto::payableDays),
                combinedRecoverable
        );
    }

    private BigDecimal sum(
            TerminationLeaveSummaryDto a,
            TerminationLeaveSummaryDto b,
            Function<TerminationLeaveSummaryDto, BigDecimal> extractor) {

        BigDecimal total = BigDecimal.ZERO;

        if (a != null && extractor.apply(a) != null) {
            total = total.add(extractor.apply(a));
        }

        if (b != null && extractor.apply(b) != null) {
            total = total.add(extractor.apply(b));
        }

        return total;
    }

    private TerminationLeaveSummaryDto buildTerminationSummary(
            long employeeId,
            LeaveType type,
            LocalDate lastWorkingDay) {

        LeaveBalance balance = balances.findCurrentCycle(
                employeeId,
                type.getId(),
                lastWorkingDay
        ).orElse(null);

        if (balance == null) {
            return null;
        }

        BigDecimal payable;
        BigDecimal recoverable;
        BigDecimal earned;

        if (CODE_ANNUAL.equalsIgnoreCase(type.getCode())) {

            // Annual leave has its own termination rule — never share
            // the medical/casual "excess over earned" formula with it
            // (see the Javadoc on annualLeaveTermination).
            earned = calculateEarnedDays(type, balance, lastWorkingDay);

            AnnualLeaveTermination result = annualLeaveTermination(
                    balance,
                    earned,
                    lastWorkingDay);

            payable = result.payable();
            recoverable = result.recoverable();

        } else {

            // Medical(M1) / Casual settlement entitlement is a fixed
            // calendar-month policy, NOT the day-based prorated
            // LeaveBalance.allocated used for normal accrual — see
            // calculateMedicalCasualEarned's Javadoc.
            earned = calculateMedicalCasualEarned(employeeId, type, balance, lastWorkingDay);

            recoverable = calculateRecoverableDays(
                    balance,
                    earned);

            payable = calculatePayableDays(
                    type,
                    balance,
                    earned,
                    recoverable);
        }

        return new TerminationLeaveSummaryDto(

                type.getId(),
                type.getCode(),
                type.getName(),
                type.isEncashable(),

                balance.getCycleStart(),
                balance.getCycleEnd(),

                balance.getAllocated(),
                earned,
                balance.getUsed(),
                balance.getPending(),
                payable,
                recoverable
        );
    }

    // Leave is granted/consumed in whole or half-day increments — an
    // earned balance like "8.63 days" can never actually be taken, so
    // every earned/payable value is rounded to the nearest half day.
    private static final BigDecimal HALF_DAY = BigDecimal.valueOf(0.5);

    /**
     * Helper methods
     */
    // Annual-leave-only now — Medical(M1)/Casual termination entitlement
    // is computed by calculateMedicalCasualEarned instead (fixed
    // calendar-month policy, not this method's prorated-allocation /
    // completed-whole-month math). Kept here for Annual because Annual's
    // "earned" figure is purely informational on the DTO — the actual
    // Annual payable/recoverable decision is made entirely inside
    // annualLeaveTermination() from balance.getAllocated()/used/pending,
    // never from this "earned" value.
    private BigDecimal calculateEarnedDays(
            LeaveType type,
            LeaveBalance balance,
            LocalDate lastWorkingDay) {

        // Monthly accrual, not day-of-cycle proration: e.g. Annual
        // (14/year) earns ~1.17/month. A partial/incomplete month
        // (including the termination month itself, when joining and
        // terminating fall in the same month) earns nothing yet.
        long totalCycleMonths = ChronoUnit.MONTHS.between(
                balance.getCycleStart(),
                balance.getCycleEnd().plusDays(1));

        if (totalCycleMonths <= 0) {
            totalCycleMonths = 12;
        }

        long completedMonths = ChronoUnit.MONTHS.between(
                balance.getCycleStart(),
                lastWorkingDay.plusDays(1));

        if (completedMonths < 0) {
            completedMonths = 0;
        }

        if (completedMonths > totalCycleMonths) {
            completedMonths = totalCycleMonths;
        }

        BigDecimal monthlyRate = balance.getAllocated()
                .divide(BigDecimal.valueOf(totalCycleMonths), 6, RoundingMode.HALF_UP);

        BigDecimal earned = monthlyRate.multiply(BigDecimal.valueOf(completedMonths));

        return roundToHalfDay(earned.min(balance.getAllocated()));
    }

    /**
     * Rounds to the nearest half day (0, 0.5, 1, 1.5, ...) — leave is
     * never granted or consumed in smaller increments.
     */
    private static BigDecimal roundToHalfDay(BigDecimal value) {
        return value.divide(HALF_DAY, 0, RoundingMode.HALF_UP)
                .multiply(HALF_DAY)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Medical(M1)/Casual settlement entitlement — company policy: a fixed
     * 8 (Medical) + 10 (Casual) = 18 days/year, i.e. 1.5 days/month
     * combined, counting BOTH the joining month and the termination
     * month in full. This deliberately does <b>not</b> use
     * {@link LeaveBalance#getAllocated()} (the day-of-cycle prorated
     * figure stamped at hire by {@link LeaveCycleResolver#allocationFor}
     * for normal accrual/display) and does <b>not</b> use
     * {@link #calculateEarnedDays} (completed-whole-month gate via
     * {@code ChronoUnit.MONTHS.between}, which excludes any partial
     * month — including the join/termination month). Per-type rate is
     * {@code type.getDefaultPerYear() / 12} (the leave-type catalogue
     * default, e.g. 8/12 or 10/12) so Medical + Casual always sum to
     * exactly the policy's combined 1.5/month once both are rounded to
     * the nearest half day (verified for every month count 0-36).
     */
    private BigDecimal calculateMedicalCasualEarned(
            long employeeId,
            LeaveType type,
            LeaveBalance balance,
            LocalDate lastWorkingDay) {

        int eligibleMonths = medicalCasualEligibleMonths(employeeId, balance, lastWorkingDay);
        if (eligibleMonths <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal monthlyRate = type.getDefaultPerYear()
                .divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);

        return roundToHalfDay(monthlyRate.multiply(BigDecimal.valueOf(eligibleMonths)));
    }

    /**
     * Inclusive calendar-month count from the employee's joining month
     * through the termination month, clamped to the current Medical/
     * Casual cycle window. Day-of-month is irrelevant on either end —
     * joining Aug 31 counts August in full; terminating Sep 1 counts
     * September in full. No 1-10/11-20/21-31 banding, no day-based
     * proration, no "completed month" requirement.
     */
    private int medicalCasualEligibleMonths(
            long employeeId,
            LeaveBalance balance,
            LocalDate lastWorkingDay) {

        LocalDate joiningDate = joiningDateOf(employeeId);

        LocalDate periodStart = (joiningDate == null || joiningDate.isBefore(balance.getCycleStart()))
                ? balance.getCycleStart()
                : joiningDate;

        LocalDate periodEnd = lastWorkingDay.isAfter(balance.getCycleEnd())
                ? balance.getCycleEnd()
                : lastWorkingDay;

        if (periodEnd.isBefore(periodStart)) {
            return 0;
        }

        return (periodEnd.getYear() - periodStart.getYear()) * 12
                + (periodEnd.getMonthValue() - periodStart.getMonthValue())
                + 1;
    }

    private BigDecimal calculateRecoverableDays(
            LeaveBalance balance,
            BigDecimal earned) {

        BigDecimal consumed =
                balance.getUsed()
                        .add(balance.getPending());

        BigDecimal recovery =
                consumed.subtract(earned);

        return recovery.max(BigDecimal.ZERO);
    }

    /**
     * Payable (encashable) days at termination for Medical(M1) / Casual
     * — earned-to-date minus what's already used/pending, floored at
     * zero. Neither type is encashable today ({@code type.isEncashable()}
     * is false for both), so this always resolves to zero in practice;
     * kept generic in case that policy ever changes. <b>Never call this
     * for Annual leave</b> — see {@link #annualLeaveTermination}.
     */
    private BigDecimal calculatePayableDays(
            LeaveType type,
            LeaveBalance balance,
            BigDecimal earned,
            BigDecimal recoverable) {

        if (!type.isEncashable()) {
            return BigDecimal.ZERO;
        }

        if (recoverable.signum() > 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal consumed = balance.getUsed().add(balance.getPending());

        return earned.subtract(consumed).max(BigDecimal.ZERO);
    }

    private record AnnualLeaveTermination(BigDecimal payable, BigDecimal recoverable) {
    }

    /**
     * Annual leave's termination rule is deliberately not the
     * medical/casual "excess over earned" formula — it's an all-or-
     * nothing gate on the <b>current cycle's ending month</b> (the
     * calendar year+month of {@code balance.getCycleEnd()}), not the
     * employee's joining month. Joining month == cycle-end month only
     * for an employee who has already completed at least one full
     * cycle; checking the joining month directly would (wrongly) treat
     * a same-day termination-on-joining as a completed-cycle payout.
     *
     * <ul>
     *   <li><b>Terminating in the cycle-end month</b> (any day of that
     *       month — not just the exact {@code cycleEnd} date) —
     *       encashable. Payable days = the full cycle's allocation
     *       minus days already used and pending, floored at zero —
     *       <i>not</i> the prorated {@code earned} figure. The employee
     *       is being paid out for completing the cycle, not for
     *       whatever fraction had accrued month-by-month, and nothing
     *       is recovered in this case.</li>
     *   <li><b>Terminating in any other month</b> — not encashable.
     *       Payable days = 0, and the <i>entire</i> used balance for the
     *       cycle is recovered (not merely the excess over earned — that
     *       distinction is the whole point of this branch existing
     *       separately from the medical/casual formula).</li>
     * </ul>
     */
    private AnnualLeaveTermination annualLeaveTermination(
            LeaveBalance balance,
            BigDecimal earned,
            LocalDate lastWorkingDay) {

        LocalDate cycleEnd = balance.getCycleEnd();

        boolean cycleEndingMonth = cycleEnd != null
                && lastWorkingDay.getYear() == cycleEnd.getYear()
                && lastWorkingDay.getMonth() == cycleEnd.getMonth();

        if (cycleEndingMonth) {

            // Cycle-end-month termination pays out all remaining
            // allocation, not merely what has accrued month-by-month —
            // the employee has completed the full cycle, so prorated
            // "earned" must not cap the payout. Pending days are
            // already-reserved leave, so they're treated the same as
            // used for this settlement (not double-counted, and not
            // still payable).
            BigDecimal payable = balance.getAllocated()
                    .subtract(balance.getUsed())
                    .subtract(balance.getPending())
                    .max(BigDecimal.ZERO);

            return new AnnualLeaveTermination(payable, BigDecimal.ZERO);
        }

        return new AnnualLeaveTermination(BigDecimal.ZERO, balance.getUsed());
    }


    /**
     * Collapse overlapping rows to one per leave type, preferring the row
     * whose window matches the type's current anchored cycle. Steady state
     * has exactly one overlapping row per type (no-op); the dedupe only
     * matters during the one-time calendar→cycle migration, where a stale
     * calendar-year row can briefly overlap a freshly-anchored cycle.
     */
    private List<LeaveBalance> currentCyclePerType(long employeeId, LocalDate today, List<LeaveBalance> overlapping) {
        if (overlapping.size() < 2) return overlapping;
        Map<Long, LeaveType> typeById = leaveTypes.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, t -> t, (a, b) -> a));
        LocalDate joiningDate = joiningDateOf(employeeId);
        Map<Long, LeaveBalance> best = new java.util.LinkedHashMap<>();
        for (LeaveBalance b : overlapping) {
            LeaveBalance cur = best.get(b.getLeaveTypeId());
            if (cur == null) {
                best.put(b.getLeaveTypeId(), b);
                continue;
            }
            LeaveType type = typeById.get(b.getLeaveTypeId());
            LocalDate anchored = type == null ? null
                    : LeaveCycleResolver.windowFor(type.getCycleAnchor(), joiningDate, today).start();
            if (anchored != null && anchored.equals(b.getCycleStart()) && !anchored.equals(cur.getCycleStart())) {
                best.put(b.getLeaveTypeId(), b);
            }
        }
        return new java.util.ArrayList<>(best.values());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void hold(long employeeId, long leaveTypeId, LocalDate onDate, BigDecimal amount) {
        LeaveBalance b = require(employeeId, leaveTypeId, onDate);
        b.setPending(b.getPending().add(amount));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long employeeId, long leaveTypeId, LocalDate onDate, BigDecimal amount) {
        LeaveBalance b = require(employeeId, leaveTypeId, onDate);
        b.setPending(b.getPending().subtract(amount).max(BigDecimal.ZERO));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(long employeeId, long leaveTypeId, LocalDate onDate, BigDecimal amount) {
        LeaveBalance b = require(employeeId, leaveTypeId, onDate);
        b.setPending(b.getPending().subtract(amount).max(BigDecimal.ZERO));
        b.setUsed(b.getUsed().add(amount));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void unconsume(long employeeId, long leaveTypeId, LocalDate onDate, BigDecimal amount) {
        LeaveBalance b = require(employeeId, leaveTypeId, onDate);
        b.setUsed(b.getUsed().subtract(amount).max(BigDecimal.ZERO));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal availableFor(long employeeId, long leaveTypeId, LocalDate onDate) {
        return available(require(employeeId, leaveTypeId, onDate));
    }

    /**
     * Backfill the joining date onto the attendance-side mirror when it's
     * missing (employee mirrored before the column existed). Called from
     * the leave-submit path, which already holds the employee view.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void captureJoiningDate(long employeeId, LocalDate joiningDate) {
        if (joiningDate == null) return;
        attendanceEmployees.findByEmployeeId(employeeId).ifPresent(row -> {
            if (row.getJoiningDate() == null) row.setJoiningDate(joiningDate);
        });
    }

    /**
     * Open the next cycle for every anchored type whose boundary is today,
     * encashing (JOINING + encashable) or lapsing (everything else) the
     * prior cycle. Idempotent: re-running on the same day is a no-op once
     * the new cycle row exists. Runs inside the renewal job's per-employee
     * transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int renewCycles(long employeeId, LocalDate joiningDate, LocalDate today) {
        int renewed = 0;
        for (LeaveType t : leaveTypes.findAll()) {
            if (!t.isActive() || t.getCycleAnchor() == LeaveCycleAnchor.NONE) continue;
            if (t.getCycleAnchor() == LeaveCycleAnchor.JOINING && joiningDate == null) {
                log.warn("skipping JOINING renewal for employeeId={} leaveType={} — no joining date on mirror",
                        employeeId, t.getCode());
                continue;
            }
            LeaveCycleResolver.Window w = LeaveCycleResolver.windowFor(t.getCycleAnchor(), joiningDate, today);
            if (!today.equals(w.start())) continue; // not a boundary for this type today
            if (balances.findByEmployeeIdAndLeaveTypeIdAndCycleStart(employeeId, t.getId(), w.start()).isPresent()) {
                continue; // already renewed today
            }
            LeaveCycleResolver.Window prior =
                    LeaveCycleResolver.windowFor(t.getCycleAnchor(), joiningDate, w.start().minusDays(1));
            balances.findByEmployeeIdAndLeaveTypeIdAndCycleStart(employeeId, t.getId(), prior.start())
                    .ifPresent(p -> closeCycle(employeeId, t, p));
            ensureCycle(employeeId, t, joiningDate, today);
            renewed++;
        }
        return renewed;
    }

    public Function<Long, String> codeLookup() {
        Map<Long, String> codes = leaveTypes.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, LeaveType::getCode, (a, b) -> a));
        return codes::get;
    }

    // -- internals --------------------------------------------------------

    /**
     * Close the prior cycle: encash unused for encashable types, else lapse.
     */
    private void closeCycle(long employeeId, LeaveType type, LeaveBalance prior) {
        if (!type.isEncashable()) return; // lapse — unused simply doesn't carry
        BigDecimal unused = available(prior);
        if (unused.signum() <= 0) return;
        prior.setEncashedDays(unused);
        outbox.publish(AttendanceEvents.TOPIC,
                "leave-encash:" + employeeId + ":" + prior.getCycleStart(),
                AttendanceEvents.LEAVE_ENCASHED,
                new AttendanceEvents.LeaveEncashed(employeeId, type.getCode(), unused,
                        prior.getCycleStart(), prior.getCycleEnd()));
        audit.emit("leave_balance", String.valueOf(prior.getId()), "encashed",
                Map.of("available", unused.toString()),
                Map.of("encashedDays", unused.toString(),
                        "leaveType", type.getCode(),
                        "cycleStart", prior.getCycleStart().toString(),
                        "cycleEnd", prior.getCycleEnd().toString()),
                true);
        log.info("encashed {} day(s) of {} for employeeId={} cycle {}..{}",
                unused, type.getCode(), employeeId, prior.getCycleStart(), prior.getCycleEnd());
    }

    private LeaveBalance require(long employeeId, long leaveTypeId, LocalDate onDate) {
        LeaveType type = leaveTypes.findById(leaveTypeId)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.LEAVE_TYPE_NOT_FOUND, "leave type not found"));
        return ensureCycle(employeeId, type, joiningDateOf(employeeId), onDate);
    }

    private LeaveBalance ensureCycle(long employeeId, LeaveType type, LocalDate joiningDate, LocalDate onDate) {
        LeaveCycleResolver.Window w = LeaveCycleResolver.windowFor(type.getCycleAnchor(), joiningDate, onDate);
        return balances.findByEmployeeIdAndLeaveTypeIdAndCycleStart(employeeId, type.getId(), w.start())
                .orElseGet(() -> {
                    LeaveBalance b = new LeaveBalance();
                    b.setEmployeeId(employeeId);
                    b.setLeaveTypeId(type.getId());
                    b.setCycleStart(w.start());
                    b.setCycleEnd(w.end());
                    b.setYear(w.start().getYear());
                    b.setAllocated(LeaveCycleResolver.allocationFor(type, joiningDate, w));
                    log.debug("opening cycle {}..{} for employeeId={} leaveType={} allocated={}",
                            w.start(), w.end(), employeeId, type.getCode(), b.getAllocated());
                    return balances.save(b);
                });
    }

    private LocalDate joiningDateOf(long employeeId) {
        return attendanceEmployees.findByEmployeeId(employeeId)
                .map(com.forward.hrms.attendance.entity.AttendanceEmployee::getJoiningDate)
                .orElse(null);
    }

    private static BigDecimal available(LeaveBalance b) {
        return b.getAllocated()
                .add(b.getCarried())
                .subtract(b.getUsed())
                .subtract(b.getPending());
    }
    public AnnualLeaveCalcBalanceResponseDto getEmployeeAnnualLeaveBalance(long employeeId, long leaveTypeId, LocalDate calculationDate
    ) {
        return balances.findByEmployeeIdAndLeaveTypeIdAndCycleStartLessThanEqualAndCycleEndGreaterThanEqual(
                        employeeId,
                        leaveTypeId,
                        calculationDate,
                        calculationDate
                )
                .map ( b -> new AnnualLeaveCalcBalanceResponseDto(
                b.getId(),
                b.getEmployeeId(),
                b.getLeaveTypeId(),
                b.getYear(),
                b.getAllocated(),
                b.getUsed(),
                b.getPending(),
                b.getCarried(),
                available(b),
                b.getCycleStart(),
                b.getCycleEnd()
        )).orElse(null);
    }

    /**
     * Creates a leave balance entry for the next cycle with carry-over days from unpaid annual leave.
     * This is called when HR marks an annual leave transaction as unpaid.
     *
     * Cycle calculation for ANNUAL (JOINING anchor):
     * - cycleStart = next year, joining month, joining day (adjusted if needed for Feb 29)
     * - cycleEnd = cycleStart + 1 year - 1 day (next joining anniversary - 1 day)
     * - carried field = the unpaid leave days
     * - marked with isAnnualLeaveUnpaidCarry = TRUE and annual_leave_transaction_id for undo
     */
    @Transactional
    public LeaveBalance createAnnualLeaveUnpaidCarry(long annualLeaveTransactionId, long employeeId, long leaveTypeId,
            BigDecimal carryDays, LocalDate joiningDate) {

        LeaveType leaveType = leaveTypes.findById(leaveTypeId).orElseThrow(() -> new NotFoundError(
                AttendanceErrorCode.LEAVE_TYPE_NOT_FOUND, "Leave type not found: %d".formatted(leaveTypeId)));

        if (!leaveType.isActive()) {throw new UnprocessableError(
                AttendanceErrorCode.LEAVE_TYPE_INACTIVE, "Leave type is inactive: %s".formatted(leaveType.getName()));}

        // Calculate next cycle: joining anniversary year + 1
        LocalDate cycleStart = calculateNextCycleStart(joiningDate);
        LocalDate cycleEnd = cycleStart.plusYears(1).minusDays(1);

        // Check if a balance already exists for this cycle (should not happen, but be safe)
        balances.findByEmployeeIdAndLeaveTypeIdAndCycleStart(employeeId, leaveTypeId, cycleStart).ifPresent(existing -> {
                    throw new UnprocessableError(AttendanceErrorCode.LEAVE_BALANCE_ALREADY_EXISTS,
                            "Leave balance already exists for employee %d, type %d, cycle %s".formatted(employeeId, leaveTypeId, cycleStart));});

        // Create the balance entry
        LeaveBalance balance = new LeaveBalance();
        balance.setEmployeeId(employeeId);
        balance.setLeaveTypeId(leaveTypeId);
        balance.setYear(cycleStart.getYear());
        balance.setCycleStart(cycleStart);
        balance.setCycleEnd(cycleEnd);
        balance.setAllocated(BigDecimal.ZERO);
        balance.setUsed(BigDecimal.ZERO);
        balance.setPending(BigDecimal.ZERO);
        balance.setCarried(carryDays);  // Unpaid days go to carried
        balance.setIsAnnualLeaveUnpaidCarry(true);
        balance.setAnnualLeaveTransactionId(annualLeaveTransactionId);

        LeaveBalance saved = balances.save(balance);
        log.info("Created unpaid annual leave carry balance: employeeId={}, txId={}, days={}, cycle={} to {}",
                employeeId, annualLeaveTransactionId, carryDays, cycleStart, cycleEnd);

        // Emit audit event
        audit.emit("leave_balance", String.valueOf(saved.getId()), "annual_leave_unpaid_carry_created",
                null, Map.of("employeeId", employeeId, "annualLeaveTransactionId", annualLeaveTransactionId,
                        "carryDays", carryDays,
                        "cycleStart", cycleStart,
                        "cycleEnd", cycleEnd
                ), true);

        return saved;
    }

    /**
     * Removes a leave balance entry created from annual leave unpaid transaction.
     * This is called when HR toggles from unpaid back to pay.
     */
    @Transactional
    public void removeAnnualLeaveUnpaidCarry(long annualLeaveTransactionId) {
        long deletedCount = balances.deleteByAnnualLeaveTransactionId(annualLeaveTransactionId);

        if (deletedCount == 0) {
            log.warn("No leave balance found to delete for annual leave transaction: {}", annualLeaveTransactionId);
        } else {
            log.info("Deleted {} unpaid annual leave carry balance(s) for transaction: {}", deletedCount, annualLeaveTransactionId);

            // Emit audit event
            audit.emit("leave_balance", String.valueOf(annualLeaveTransactionId), "annual_leave_unpaid_carry_removed",
                    null, Map.of("annualLeaveTransactionId", annualLeaveTransactionId), true);
        }
    }

    /**
     * Calculate the start date of the next joining anniversary cycle.
     * 
     * For JOINING anchor leave types (ANNUAL):
     * - Start = next calendar year, same month/day as joining date
     * - Adjusted for edge cases (e.g., Feb 29 → Feb 28 on non-leap years)
     */
    private LocalDate calculateNextCycleStart(LocalDate joiningDate) {
        int nextYear = LocalDate.now().getYear();
        
        // If joining date hasn't occurred yet this year, start from current year
        LocalDate thisYearAnniversary = LocalDate.of(nextYear, joiningDate.getMonthValue(), 1)
                .withDayOfMonth(Math.min(joiningDate.getDayOfMonth(), 28));
        
        if (thisYearAnniversary.isAfter(LocalDate.now())) {
            return thisYearAnniversary;
        }
        
        // Otherwise start from next year
        nextYear++;
        return LocalDate.of(nextYear, joiningDate.getMonthValue(), 1)
                .withDayOfMonth(Math.min(joiningDate.getDayOfMonth(), 28));
    }
}
