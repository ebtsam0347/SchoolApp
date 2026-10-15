package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.response.AnnualLeaveSummaryDto;
import com.forward.hrms.attendance.dto.response.EmployeeLeavePayslipSummaryDto;
import com.forward.hrms.attendance.dto.response.LeaveTypeAvailedDto;
import com.forward.hrms.attendance.dto.response.PayrollAttendanceAuditDto;
import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.entity.LeaveBalance;
import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.HolidayRepository;
import com.forward.hrms.attendance.repository.LeaveBalanceRepository;
import com.forward.hrms.attendance.repository.LeaveRequestDayRepository;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;
import com.forward.hrms.attendance.repository.projection.ApprovedLeaveDayProjection;
import com.forward.hrms.attendance.service.PayrollAttendanceClassifier.DayRecord;
import com.forward.hrms.attendance.service.PayrollAttendanceClassifier.LeaveDay;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Payslip inputs from attendance for a whole page of employees at once
 * (payroll's run-detail table): the derived per-day attendance
 * classification plus per-leave-type usage. Five set-based queries serve
 * the page regardless of its size — attendance records, holidays,
 * approved leave-days (one range query covering both the month and every
 * leave cycle), joining dates, and cycle balances.
 *
 * <p>Leave usage is derived from approved leave records, not from
 * {@code leave_balances.used}: that counter is a maintained cache that can
 * be missing or stale (it is empty for migrated data), while the approved
 * leave-day rows are the source of truth. Balances are read only for the
 * entitlement figures (total / encashed / pending).
 */
@Service
@RequiredArgsConstructor
public class PayslipAttendanceService {

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final String ANNUAL = "ANNUAL";

    private final AttendanceEmployeeRepository employees;
    private final AttendanceRecordRepository records;
    private final HolidayRepository holidays;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveBalanceRepository balances;
    private final LeaveRequestDayRepository leaveDays;

    @Transactional(readOnly = true)
    public List<EmployeeLeavePayslipSummaryDto> summaries(Collection<Long> employeeIds, LocalDate from, LocalDate to) {
        Set<Long> ids = new LinkedHashSet<>(employeeIds);
        if (ids.isEmpty()) return List.of();

        Map<Long, LocalDate> joining = new HashMap<>();
        for (AttendanceEmployee e : employees.findAllByEmployeeIdIn(ids)) {
            joining.put(e.getEmployeeId(), e.getJoiningDate());
        }
        Set<LocalDate> holidayDates = holidays.findAllByHolidayDateBetweenOrderByHolidayDateAsc(from, to).stream()
                .map(h -> h.getHolidayDate()).collect(Collectors.toSet());

        Map<Long, Map<LocalDate, DayRecord>> recordsByEmployee = new HashMap<>();
        for (AttendanceRecord r : records
                .findAllByEmployeeIdInAndAttendanceDateBetweenOrderByEmployeeIdAscAttendanceDateAsc(ids, from, to)) {
            recordsByEmployee.computeIfAbsent(r.getEmployeeId(), k -> new HashMap<>())
                    .put(r.getAttendanceDate(), new DayRecord(r.getStatus(), r.getPaid()));
        }

        List<LeaveType> types = leaveTypes.findAll();
        Map<Long, LeaveType> typeById = types.stream().collect(Collectors.toMap(LeaveType::getId, t -> t));

        // Earliest cycle start any of these employees can need, so ONE range
        // query covers the month and every leave cycle.
        LocalDate earliest = from;
        Map<Long, Map<Long, LocalDate>> cycleStart = new HashMap<>(); // employee -> type -> cycle start
        for (Long id : ids) {
            Map<Long, LocalDate> perType = new HashMap<>();
            for (LeaveType t : types) {
                LocalDate cs = LeaveCycleResolver.windowFor(t.getCycleAnchor(), joining.get(id), to).start();
                perType.put(t.getId(), cs);
                if (cs.isBefore(earliest)) earliest = cs;
            }
            cycleStart.put(id, perType);
        }
        Map<Long, List<ApprovedLeaveDayProjection>> leaveRows = leaveDays
                .approvedDaysForEmployees(ids, earliest, to).stream()
                .collect(Collectors.groupingBy(ApprovedLeaveDayProjection::getEmployeeId));

        Map<String, LeaveBalance> balanceByKey = new HashMap<>();
        for (LeaveBalance b : balances
                .findAllByEmployeeIdInAndCycleStartLessThanEqualAndCycleEndGreaterThanEqual(ids, to, to)) {
            balanceByKey.putIfAbsent(b.getEmployeeId() + ":" + b.getLeaveTypeId(), b);
        }

        List<EmployeeLeavePayslipSummaryDto> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            List<ApprovedLeaveDayProjection> rows = leaveRows.getOrDefault(id, List.of());

            Map<LocalDate, List<LeaveDay>> monthLeaves = new HashMap<>();
            Map<Long, BigDecimal> monthByType = new HashMap<>();
            Map<Long, BigDecimal> cycleByType = new HashMap<>();
            for (ApprovedLeaveDayProjection row : rows) {
                LeaveType t = typeById.get(row.getLeaveTypeId());
                if (t == null) continue;
                BigDecimal fraction = Boolean.TRUE.equals(row.getHalfDay()) ? HALF : BigDecimal.ONE;
                LocalDate d = row.getAttendanceDate();
                if (!d.isBefore(from)) {
                    monthLeaves.computeIfAbsent(d, k -> new ArrayList<>()).add(new LeaveDay(fraction, t.isPaid()));
                    monthByType.merge(t.getId(), fraction, BigDecimal::add);
                }
                if (!d.isBefore(cycleStart.get(id).get(t.getId()))) {
                    cycleByType.merge(t.getId(), fraction, BigDecimal::add);
                }
            }

            PayrollAttendanceAuditDto audit = PayrollAttendanceClassifier.classify(from, to, joining.get(id),
                    holidayDates, recordsByEmployee.getOrDefault(id, Map.of()), monthLeaves);

            List<LeaveTypeAvailedDto> leaves = new ArrayList<>();
            AnnualLeaveSummaryDto annual = null;
            for (LeaveType t : types) {
                if (!t.isActive() || !t.isPaid()) continue;
                BigDecimal month = monthByType.getOrDefault(t.getId(), BigDecimal.ZERO);
                BigDecimal cycle = cycleByType.getOrDefault(t.getId(), BigDecimal.ZERO);
                if (ANNUAL.equalsIgnoreCase(t.getCode())) {
                    LeaveBalance b = balanceByKey.get(id + ":" + t.getId());
                    annual = annualBlock(b, cycle);
                    continue;
                }
                leaves.add(new LeaveTypeAvailedDto(t.getCode(), t.getName(), month, cycle));
            }
            out.add(new EmployeeLeavePayslipSummaryDto(id, leaves, annual, audit));
        }
        return out;
    }

    private static AnnualLeaveSummaryDto annualBlock(LeaveBalance b, BigDecimal availedFromRecords) {
        if (b == null) return new AnnualLeaveSummaryDto(null, availedFromRecords, null, null);
        BigDecimal total = b.getAllocated().add(b.getCarried());
        BigDecimal paid = b.getEncashedDays() == null ? BigDecimal.ZERO : b.getEncashedDays();
        BigDecimal balance = total.subtract(availedFromRecords).subtract(b.getPending());
        return new AnnualLeaveSummaryDto(total, availedFromRecords, paid, balance);
    }
}
