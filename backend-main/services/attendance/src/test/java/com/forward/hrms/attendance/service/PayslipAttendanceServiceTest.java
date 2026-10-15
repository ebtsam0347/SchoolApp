package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.response.AnnualLeaveSummaryDto;
import com.forward.hrms.attendance.dto.response.EmployeeLeavePayslipSummaryDto;
import com.forward.hrms.attendance.dto.response.LeaveTypeAvailedDto;
import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.entity.Holiday;
import com.forward.hrms.attendance.entity.LeaveBalance;
import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.enums.LeaveCycleAnchor;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.HolidayRepository;
import com.forward.hrms.attendance.repository.LeaveBalanceRepository;
import com.forward.hrms.attendance.repository.LeaveRequestDayRepository;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;
import com.forward.hrms.attendance.repository.projection.ApprovedLeaveDayProjection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the bulk payslip service's leave derivation against employee
 * 13411's real leave history (dev DB): CASUAL on Jul 7, Jul 21 and Aug 8
 * (FISCAL cycle starts Jul 1), so August usage is 1 and cycle usage is 3
 * — even though {@code leave_balances} is empty for everyone.
 */
class PayslipAttendanceServiceTest {

    private static final long EMP = 6295L;
    private static final long CASUAL = 3L, SICK = 2L, ANNUAL = 1L, UNPAID = 4L;
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);

    private LeaveBalanceRepository balances;
    private List<ApprovedLeaveDayProjection> leaveRows;
    private PayslipAttendanceService service;

    private static LeaveType type(long id, String code, boolean paid, LeaveCycleAnchor anchor) {
        LeaveType t = new LeaveType();
        t.setId(id);
        t.setCode(code);
        t.setName(code + " name");
        t.setPaid(paid);
        t.setCycleAnchor(anchor);
        t.setActive(true);
        return t;
    }

    private static ApprovedLeaveDayProjection leaveDay(long typeId, LocalDate date, boolean half) {
        return new ApprovedLeaveDayProjection() {
            public Long getEmployeeId() { return EMP; }
            public LocalDate getAttendanceDate() { return date; }
            public Long getLeaveTypeId() { return typeId; }
            public Boolean getHalfDay() { return half; }
        };
    }

    @BeforeEach
    void setUp() {
        AttendanceEmployeeRepository employees = mock(AttendanceEmployeeRepository.class);
        AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
        HolidayRepository holidays = mock(HolidayRepository.class);
        LeaveTypeRepository leaveTypes = mock(LeaveTypeRepository.class);
        balances = mock(LeaveBalanceRepository.class);
        LeaveRequestDayRepository leaveDays = mock(LeaveRequestDayRepository.class);

        AttendanceEmployee e = new AttendanceEmployee();
        e.setEmployeeId(EMP);
        e.setJoiningDate(LocalDate.of(2025, 2, 17));
        when(employees.findAllByEmployeeIdIn(any())).thenReturn(List.of(e));

        Holiday h1 = new Holiday();
        h1.setHolidayDate(LocalDate.of(2026, 8, 14));
        Holiday h2 = new Holiday();
        h2.setHolidayDate(LocalDate.of(2026, 8, 26));
        when(holidays.findAllByHolidayDateBetweenOrderByHolidayDateAsc(FROM, TO)).thenReturn(List.of(h1, h2));

        List<AttendanceRecord> recs = new ArrayList<>();
        for (int d : new int[]{1, 3, 4, 5, 6, 7, 10, 11, 12, 13, 15, 17, 18, 19, 20, 21, 22, 24, 25, 27, 28, 29, 31}) {
            AttendanceRecord r = new AttendanceRecord();
            r.setEmployeeId(EMP);
            r.setAttendanceDate(LocalDate.of(2026, 8, d));
            r.setStatus(AttendanceStatus.PRESENT);
            recs.add(r);
        }
        when(records.findAllByEmployeeIdInAndAttendanceDateBetweenOrderByEmployeeIdAscAttendanceDateAsc(any(), any(), any()))
                .thenReturn(recs);

        when(leaveTypes.findAll()).thenReturn(List.of(
                type(ANNUAL, "ANNUAL", true, LeaveCycleAnchor.JOINING),
                type(SICK, "MEDICAL_M1", true, LeaveCycleAnchor.FISCAL),
                type(CASUAL, "CASUAL", true, LeaveCycleAnchor.FISCAL),
                type(UNPAID, "UNPAID", false, LeaveCycleAnchor.NONE)));

        leaveRows = new ArrayList<>(List.of(
                leaveDay(CASUAL, LocalDate.of(2026, 7, 7), false),
                leaveDay(CASUAL, LocalDate.of(2026, 7, 21), false),
                leaveDay(CASUAL, LocalDate.of(2026, 8, 8), false)));
        when(leaveDays.approvedDaysForEmployees(any(), any(), any())).thenAnswer(inv -> leaveRows);
        when(balances.findAllByEmployeeIdInAndCycleStartLessThanEqualAndCycleEndGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of());

        service = new PayslipAttendanceService(employees, records, holidays, leaveTypes, balances, leaveDays);
    }

    private static BigDecimal availed(EmployeeLeavePayslipSummaryDto s, String code, boolean month) {
        LeaveTypeAvailedDto l = s.leaves().stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow();
        return month ? l.availedThisMonth() : l.availedThisCycle();
    }

    @Test
    void casualUsage_monthIsOne_cycleIsThree_evenWithNoBalanceRows() {
        EmployeeLeavePayslipSummaryDto s = service.summaries(List.of(EMP), FROM, TO).get(0);

        assertThat(availed(s, "CASUAL", true)).isEqualByComparingTo("1");
        assertThat(availed(s, "CASUAL", false)).isEqualByComparingTo("3");
        assertThat(availed(s, "MEDICAL_M1", true)).isEqualByComparingTo("0");
    }

    @Test
    void unpaidType_neverListedAsLeaveLine_annualExcludedFromList() {
        EmployeeLeavePayslipSummaryDto s = service.summaries(List.of(EMP), FROM, TO).get(0);

        assertThat(s.leaves()).extracting(LeaveTypeAvailedDto::code)
                .containsExactlyInAnyOrder("MEDICAL_M1", "CASUAL");
    }

    @Test
    void halfDayLeave_countsHalf() {
        leaveRows.add(leaveDay(SICK, LocalDate.of(2026, 8, 12), true));

        EmployeeLeavePayslipSummaryDto s = service.summaries(List.of(EMP), FROM, TO).get(0);

        assertThat(availed(s, "MEDICAL_M1", true)).isEqualByComparingTo("0.5");
        assertThat(availed(s, "MEDICAL_M1", false)).isEqualByComparingTo("0.5");
    }

    @Test
    void annualLeave_noBalanceRow_availedFromRecordsAndEntitlementUnknown() {
        leaveRows.add(leaveDay(ANNUAL, LocalDate.of(2026, 3, 3), false));   // inside 2026-02-17..2027-02-16
        leaveRows.add(leaveDay(ANNUAL, LocalDate.of(2026, 8, 20), false));

        AnnualLeaveSummaryDto annual = service.summaries(List.of(EMP), FROM, TO).get(0).annualLeave();

        assertThat(annual.availed()).isEqualByComparingTo("2");
        assertThat(annual.total()).isNull();
        assertThat(annual.paid()).isNull();
        assertThat(annual.balance()).isNull();
    }

    @Test
    void annualLeave_withBalanceRow_totalAndBalanceComeFromEntitlement() {
        leaveRows.add(leaveDay(ANNUAL, LocalDate.of(2026, 3, 3), false));
        leaveRows.add(leaveDay(ANNUAL, LocalDate.of(2026, 8, 20), false));
        LeaveBalance b = new LeaveBalance();
        b.setEmployeeId(EMP);
        b.setLeaveTypeId(ANNUAL);
        b.setAllocated(new BigDecimal("14"));
        b.setCarried(new BigDecimal("2"));
        b.setPending(BigDecimal.ONE);
        when(balances.findAllByEmployeeIdInAndCycleStartLessThanEqualAndCycleEndGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(b));

        AnnualLeaveSummaryDto annual = service.summaries(List.of(EMP), FROM, TO).get(0).annualLeave();

        assertThat(annual.total()).isEqualByComparingTo("16");
        assertThat(annual.availed()).isEqualByComparingTo("2");
        assertThat(annual.paid()).isEqualByComparingTo("0");
        assertThat(annual.balance()).isEqualByComparingTo("13");   // 16 - 2 availed - 1 pending
    }

    @Test
    void attendanceAudit_isAttachedAndMatchesLegacyDaysWorked() {
        EmployeeLeavePayslipSummaryDto s = service.summaries(List.of(EMP), FROM, TO).get(0);

        assertThat(s.attendance().workingDays()).isEqualTo(24);
        assertThat(s.attendance().presentDays()).isEqualByComparingTo("23");
        assertThat(s.attendance().paidLeaveDays()).isEqualByComparingTo("1");
        assertThat(s.attendance().paidDays()).isEqualByComparingTo("26");
    }

    @Test
    void emptyIds_returnsEmpty_withoutQueries() {
        assertThat(service.summaries(List.of(), FROM, TO)).isEmpty();
    }
}
