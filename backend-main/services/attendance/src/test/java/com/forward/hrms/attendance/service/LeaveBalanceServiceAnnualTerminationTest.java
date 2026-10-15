package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.response.TerminationLeaveSummaryDto;
import com.forward.hrms.attendance.dto.response.TerminationLeavesSummaryDto;
import com.forward.hrms.attendance.entity.LeaveBalance;
import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.LeaveBalanceRepository;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Annual leave termination settlement is gated on the employee's
 * <b>current cycle's ending month</b> ({@link LeaveBalance#getCycleEnd()}),
 * never on the joining month directly — see
 * {@link LeaveBalanceService#terminationLeavesSummary(long, LocalDate)}.
 */
class LeaveBalanceServiceAnnualTerminationTest {

    private static final long EMPLOYEE_ID = 1L;
    private static final long ANNUAL_TYPE_ID = 100L;

    private LeaveBalanceRepository balances;
    private LeaveTypeRepository leaveTypes;
    private LeaveBalanceService service;

    @BeforeEach
    void setUp() {
        balances = mock(LeaveBalanceRepository.class);
        leaveTypes = mock(LeaveTypeRepository.class);
        AttendanceEmployeeRepository attendanceEmployees = mock(AttendanceEmployeeRepository.class);
        Outbox outbox = mock(Outbox.class);
        AuditEmitter audit = mock(AuditEmitter.class);

        service = new LeaveBalanceService(balances, leaveTypes, attendanceEmployees, outbox, audit);

        LeaveType annual = new LeaveType();
        annual.setId(ANNUAL_TYPE_ID);
        annual.setCode("ANNUAL");
        annual.setName("Annual Leave");
        annual.setEncashable(true);
        annual.setActive(true);

        // Only ANNUAL is configured — MEDICAL_M1/CASUAL lookups resolve to
        // null and the combined bucket collapses to null, which is fine:
        // these tests only assert on annualLeave().
        when(leaveTypes.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(annual));
    }

    private LeaveBalance balance(
            LocalDate cycleStart,
            LocalDate cycleEnd,
            BigDecimal allocated,
            BigDecimal used,
            BigDecimal pending) {

        LeaveBalance b = new LeaveBalance();
        b.setEmployeeId(EMPLOYEE_ID);
        b.setLeaveTypeId(ANNUAL_TYPE_ID);
        b.setYear(cycleStart.getYear());
        b.setCycleStart(cycleStart);
        b.setCycleEnd(cycleEnd);
        b.setAllocated(allocated);
        b.setUsed(used);
        b.setPending(pending);
        return b;
    }

    private TerminationLeaveSummaryDto annualLeaveFor(LeaveBalance leaveBalance, LocalDate lastWorkingDay) {
        when(balances.findCurrentCycle(anyLong(), anyLong(), any(LocalDate.class)))
                .thenReturn(Optional.of(leaveBalance));

        TerminationLeavesSummaryDto response =
                service.terminationLeavesSummary(EMPLOYEE_ID, lastWorkingDay);

        return response.annualLeave();
    }

    @Test
    void terminationOnJoiningDay_isNotCycleEndMonth_noPayout() {
        // Joining 2026-08-18, terminating same day -> cycle just opened,
        // nowhere near its end (cycleEnd 2027-08-17). Must NOT pay out
        // merely because both dates fall in August.
        LeaveBalance b = balance(
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2027, 8, 17),
                BigDecimal.valueOf(14),
                BigDecimal.valueOf(3),
                BigDecimal.ZERO);

        TerminationLeaveSummaryDto annual = annualLeaveFor(b, LocalDate.of(2026, 8, 18));

        assertThat(annual.payableDays()).isEqualByComparingTo("0");
        assertThat(annual.recoverableDays()).isEqualByComparingTo("3");
    }

    @Test
    void terminationBeforeCycleEndMonth_noPayout_recoversUsed() {
        LeaveBalance b = balance(
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2027, 8, 17),
                BigDecimal.valueOf(14),
                BigDecimal.valueOf(3),
                BigDecimal.ZERO);

        TerminationLeaveSummaryDto annual = annualLeaveFor(b, LocalDate.of(2027, 7, 15));

        assertThat(annual.payableDays()).isEqualByComparingTo("0");
        assertThat(annual.recoverableDays()).isEqualByComparingTo("3");
    }

    @ParameterizedTest
    @CsvSource({
            "2027-08-01",
            "2027-08-17",
            "2027-08-31"
    })
    void terminationInCycleEndMonth_paysRemainingAllocation(String lastWorkingDay) {
        LeaveBalance b = balance(
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2027, 8, 17),
                BigDecimal.valueOf(14),
                BigDecimal.valueOf(3),
                BigDecimal.ZERO);

        TerminationLeaveSummaryDto annual = annualLeaveFor(b, LocalDate.parse(lastWorkingDay));

        assertThat(annual.payableDays()).isEqualByComparingTo("11");
        assertThat(annual.recoverableDays()).isEqualByComparingTo("0");
    }

    @Test
    void cycleEndMonthPayout_subtractsPendingTooWithoutDoubleCounting() {
        LeaveBalance b = balance(
                LocalDate.of(2026, 8, 18),
                LocalDate.of(2027, 8, 17),
                BigDecimal.valueOf(14),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(2));

        TerminationLeaveSummaryDto annual = annualLeaveFor(b, LocalDate.of(2027, 8, 10));

        // 14 - 3 (used) - 2 (pending) = 9, recovered nothing.
        assertThat(annual.payableDays()).isEqualByComparingTo("9");
        assertThat(annual.recoverableDays()).isEqualByComparingTo("0");
    }
}
