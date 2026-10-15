package com.forward.hrms.payroll.client;

import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.payroll.client.dto.attendace.AnnualLeaveCalcBalanceResponseDto;
import com.forward.hrms.payroll.client.dto.attendace.EmployeeLeavePayslipSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.TerminationAttendanceSummaryDto;
import com.forward.hrms.payroll.config.PayrollDownstreamProperties;
import com.forward.hrms.payroll.dto.response.TerminationLeavesSummaryDto;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.service.AttendanceSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Reads per-day attendance for an employee over a payroll period and
 * aggregates the bits compute needs:
 *
 * <ul>
 *   <li><b>days_present</b> — count of {@code PRESENT} + 0.5 × count of {@code HALF_DAY}</li>
 *   <li><b>OT hours</b> — sum of {@code otNormalMinutes + otExtraMinutes + otSpecialMinutes} / 60</li>
 *   <li><b>LOP days</b> — count of {@code ABSENT} plus {@code LEAVE} rows with
 *       {@code paid == false}. §7.payroll #23 — the daily record now carries
 *       a {@code paid} boolean populated at leave-approval time from the
 *       leave type. Paid leaves stay in working days; unpaid leaves drop
 *       into LOP just like absences. {@code paid == null} on a LEAVE row
 *       (only possible from the legacy backfill default) is treated as
 *       paid.</li>
 *   <li><b>WEEKEND</b> — counted regardless of which day of the week the
 *       weekly-off falls on (Forward Sports has Friday-off shifts at some
 *       sites). §7.payroll #25 — was misnamed {@code sundayCount}.</li>
 * </ul>
 *
 * <p>Identity headers are forwarded by the commons-web
 * {@code RestClientCustomizer} on request-bound threads. Saga-thread
 * callers must pass the kickoff caller's {@code X-User-*} snapshot
 * explicitly via the identity-taking overload — without it,
 * attendance-service rejects the call as unauthenticated (403).
 * Failures (upstream error, empty response) raise {@link UpstreamError}
 * — the prior "fall back to full month present" silently paid
 * terminated/absent/sick employees full salary whenever attendance
 * hiccupped. HR must see the failure and decide how to proceed.
 */
@Slf4j
@Component
public class AttendanceClient {

    private static final String OT_NORMAL = "otNormalMinutes";
    private static final String OT_EXTRA = "otExtraMinutes";
    private static final String OT_SPECIAL = "otSpecialMinutes";

    private final RestClient http;

    public AttendanceClient(PayrollDownstreamProperties props, RestClient.Builder builder) {
        this.http = builder.baseUrl(props.attendance().baseUrl()).build();
    }

    /**
     * Name of the shift assigned to the employee on {@code asOf}, from
     * {@code GET /shifts/employees/{id}} (newest assignment first). Display
     * only — a printed slip's shift line — so, unlike the payroll reads
     * here, it is best-effort: null on no assignment or any failure.
     */
    public String shiftNameOn(long employeeId, LocalDate asOf) {
        try {
            List<Map<String, Object>> rows = http.get().uri("/shifts/employees/{id}", employeeId)
                    .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {});
            if (rows == null) return null;
            for (Map<String, Object> r : rows) {
                LocalDate from = r.get("effectiveFrom") == null ? null : LocalDate.parse(r.get("effectiveFrom").toString());
                LocalDate to = r.get("effectiveTo") == null ? null : LocalDate.parse(r.get("effectiveTo").toString());
                if ((from == null || !from.isAfter(asOf)) && (to == null || !to.isBefore(asOf))) {
                    Object name = r.get("shiftName");
                    return name == null ? null : name.toString();
                }
            }
            return null;
        } catch (Exception ex) {
            log.warn("shift lookup failed for employee {}: {}", employeeId, ex.getMessage());
            return null;
        }
    }

    public AttendanceSnapshot snapshot(long employeeId, LocalDate periodStart, LocalDate periodEnd) {
        return snapshot(employeeId, periodStart, periodEnd, null);
    }

    public AttendanceSnapshot snapshot(long employeeId, LocalDate periodStart, LocalDate periodEnd,
                                       Map<String, String> identity) {
        int daysInPeriod = (int) ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        List<Map<String, Object>> rows;
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = http.get()
                    .uri(uri -> uri.path("/attendance/employee/{id}")
                            .queryParam("from", periodStart.toString())
                            .queryParam("to", periodEnd.toString())
                            .build(employeeId))
                    .headers(h -> {
                        if (identity != null) identity.forEach(h::set);
                    })
                    .retrieve()
                    .body(List.class);
            rows = body;
        } catch (Exception ex) {
            throw new UpstreamError(PayrollErrorCode.ATTENDANCE_SNAPSHOT_FAILED,
                    "attendance snapshot failed for employee %d (%s..%s): %s"
                            .formatted(employeeId, periodStart, periodEnd, ex.getMessage()),
                    ex);
        }
        if (rows == null || rows.isEmpty()) {
            throw new UpstreamError(PayrollErrorCode.ATTENDANCE_SNAPSHOT_FAILED,
                    "attendance snapshot returned no rows for employee %d (%s..%s)"
                            .formatted(employeeId, periodStart, periodEnd),
                    null);
        }

        int presentCount = 0;
        int halfDayCount = 0;
        int absentCount = 0;
        int holidayCount = 0;
        int weekendCount = 0;
        int unpaidLeaveCount = 0;
        long otMinutes = 0L;

        for (Map<String, Object> r : rows) {
            String status = String.valueOf(r.getOrDefault("status", ""));
            switch (status) {
                case "PRESENT" -> presentCount++;
                case "HALF_DAY" -> halfDayCount++;
                case "ABSENT" -> absentCount++;
                case "HOLIDAY" -> holidayCount++;
                case "WEEKEND" -> weekendCount++;
                case "LEAVE" -> {
                    // §7.payroll #23 — paid leaves don't add to LOP; unpaid leaves do.
                    Object paid = r.get("paid");
                    if (paid instanceof Boolean b && !b) unpaidLeaveCount++;
                }
                default -> { /* nothing */ }
            }
            otMinutes += longValue(r, OT_NORMAL)
                    + longValue(r, OT_EXTRA)
                    + longValue(r, OT_SPECIAL);
        }

        BigDecimal lopDays = BigDecimal.valueOf(absentCount + unpaidLeaveCount);
        BigDecimal otHours = BigDecimal.valueOf(otMinutes)
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.CEILING);
        // Half-day attendance contributes 0.5 to daysPresent — the prior
        // int math rounded odd half-day counts UP, over-paying PER_DAY
        // allowances by a half-day every month for any employee with an
        // odd number of half-day rows.
        BigDecimal daysPresent = BigDecimal.valueOf(presentCount)
                .add(BigDecimal.valueOf(halfDayCount).divide(BigDecimal.valueOf(2)))
                .setScale(2, RoundingMode.UNNECESSARY);
        int workingDays = Math.max(0, daysInPeriod - holidayCount - weekendCount);
        BigDecimal absentDays = BigDecimal.valueOf(absentCount + unpaidLeaveCount)
                .add(BigDecimal.valueOf(halfDayCount).divide(BigDecimal.valueOf(2)));
        BigDecimal paidDays = BigDecimal.valueOf(workingDays).subtract(absentDays);
        if (paidDays.signum() < 0) paidDays = BigDecimal.ZERO;
        return new AttendanceSnapshot(lopDays, daysPresent, otHours,
                workingDays, paidDays, absentDays, holidayCount, weekendCount);
    }

    /**
     * Per-employee canteen usage for the payroll period. Returns
     * {@code (days, weeks)} where weeks are 7-day chunks counted from
     * the 1st (days 1–7, 8–14, ..., 29–end). Used by compute to apply
     * the grade-conditional canteen deduction (management → weekly,
     * supervisor / labour → per-day). Returns {@code (0, 0)} on any
     * upstream failure — the compute treats that as "no canteen usage
     * this period" rather than failing the whole run for a non-critical
     * lookup.
     */
    public CanteenUsage canteenUsage(long employeeId, LocalDate periodStart, LocalDate periodEnd,
                                     Map<String, String> identity) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.get()
                    .uri(uri -> uri.path("/attendance/employee/{id}/canteen-usage")
                            .queryParam("from", periodStart.toString())
                            .queryParam("to", periodEnd.toString())
                            .build(employeeId))
                    .headers(h -> {
                        if (identity != null) identity.forEach(h::set);
                    })
                    .retrieve()
                    .body(Map.class);
            if (body == null) return CanteenUsage.empty();
            int days = intValue(body, "days");
            int weeks = intValue(body, "weeks");
            return new CanteenUsage(days, weeks);
        } catch (Exception ex) {
            log.warn("canteen usage failed for employee {} ({}..{}): {}",
                    employeeId, periodStart, periodEnd, ex.getMessage());
            return CanteenUsage.empty();
        }
    }

    public record CanteenUsage(int days, int weeks) {
        public static CanteenUsage empty() {
            return new CanteenUsage(0, 0);
        }
    }

    private static int intValue(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Number n) return n.intValue();
        return 0;
    }


    /**
     * Verifies the attendance period is locked. The operator owns the lock
     * now (attendance-side month close-out); payroll only follows, so this
     * reads {@code GET /attendance/periods/locks} and checks the month
     * rather than commanding the lock. A reachability failure raises
     * {@link UpstreamError} — "couldn't reach attendance" must not be read
     * as "not locked", which would let payroll proceed against a period
     * whose lock state we never confirmed.
     */
    public boolean isPeriodLocked(int year, int month) {
        List<Map<String, Object>> locks;
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = http.get()
                    .uri(uri -> uri.path("/attendance/periods/locks")
                            .queryParam("year", year)
                            .build())
                    .retrieve()
                    .body(List.class);
            locks = body;
        } catch (Exception ex) {
            throw new UpstreamError(PayrollErrorCode.ATTENDANCE_LOCK_CHECK_FAILED,
                    "attendance lock check failed (%d-%02d): %s".formatted(year, month, ex.getMessage()), ex);
        }
        if (locks == null) return false;
        return locks.stream().anyMatch(l ->
                intValue(l, "periodYear") == year && intValue(l, "periodMonth") == month);
    }

    private static long longValue(Map<String, Object> r, String key) {
        Object v = r.get(key);
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }


    // new API in Attendance service to calculate the annual leaves calculation for the employee called in payroll service

    /**
     * Reads the employee's annual leave settlement information from
     * attendance-service for Final Settlement preview.
     * <p>
     * Attendance owns leave-cycle calculations; Payroll only converts
     * payable/recoverable days into money.
     */
    /**
     * Reads all leave settlement information required for employee termination.
     * Attendance service owns all leave-cycle calculations.
     * Payroll only consumes the returned summaries.
     */
    public TerminationLeavesSummaryDto terminationLeavesSummary(
            long employeeId,
            LocalDate lastWorkingDay,
            Map<String, String> identity) {

        try {
            return http.get()
                    .uri(uri -> uri
                            .path("/leaves/employee/leaves-termination-summary/{employeeId}")
                            .queryParam("lastWorkingDay", lastWorkingDay)
                            .build(employeeId))
                    .headers(headers -> {
                        if (identity != null) {
                            identity.forEach(headers::set);
                        }
                    })
                    .retrieve()
                    .body(TerminationLeavesSummaryDto.class);

        } catch (Exception ex) {

            throw new UpstreamError(
                    PayrollErrorCode.LEAVE_ENCASHMENT_INVALID_STATE,
                    "Termination leave summary request failed for employee %d (lastWorkingDay=%s): %s"
                            .formatted(employeeId, lastWorkingDay, ex.getMessage()),
                    ex
            );
        }
    }

    /**
     * Convenience overload for internal payroll calls.
     * helping method of terminationLeaveSummary to call the API without identity headers, for normal payroll runs
     */
    public TerminationLeavesSummaryDto terminationLeavesSummary(
            long employeeId,
            LocalDate lastWorkingDay) {

        return terminationLeavesSummary(
                employeeId,
                lastWorkingDay,
                null
        );
    }


    /**
     * Reads attendance and overtime summary for employee final settlement.
     * Attendance service owns attendance/overtime calculations.
     * Payroll only displays and stores the returned snapshot.
     */
    // new client method to access attendance controller method to get attendance summary for termination process
    public TerminationAttendanceSummaryDto terminationAttendanceSummary(
            long employeeId,
            LocalDate from,
            LocalDate to) {

        try {
            return http.get()
                    .uri(uri -> uri
                            .path("/attendance/employee/{employeeId}/termination-summary")
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .build(employeeId))
                    .retrieve()
                    .body(TerminationAttendanceSummaryDto.class);

        } catch (Exception ex) {
            throw new UpstreamError(
                    PayrollErrorCode.ATTENDANCE_TERMINATION_SUMMARY_FAILED,
                    "termination attendance summary failed for employee %d (%s..%s): %s"
                            .formatted(employeeId, from, to, ex.getMessage()),
                    ex
            );
        }
    }

    /**
     * Attendance classification + leave usage for a page of employees over
     * one payroll period — one HTTP call per 200 ids (attendance-service's
     * cap) instead of one per employee. Best-effort like
     * {@link #canteenUsage}: on any upstream failure the affected chunk is
     * simply absent from the result, so a run-detail read degrades to "no
     * attendance/leave data" rather than failing when attendance-service
     * hiccups or the caller lacks {@code attendance.read}.
     */
    public Map<Long, EmployeeLeavePayslipSummaryDto> payslipSummaries(java.util.Collection<Long> employeeIds,
                                                                      LocalDate periodStart, LocalDate periodEnd) {
        return payslipSummaries(employeeIds, periodStart, periodEnd, null);
    }

    /** Saga-thread overload — payslip PDF generation has no inbound request to copy identity from. */
    public Map<Long, EmployeeLeavePayslipSummaryDto> payslipSummaries(java.util.Collection<Long> employeeIds,
                                                                      LocalDate periodStart, LocalDate periodEnd,
                                                                      Map<String, String> identity) {
        Map<Long, EmployeeLeavePayslipSummaryDto> out = new java.util.LinkedHashMap<>();
        if (employeeIds == null || employeeIds.isEmpty()) return out;
        List<Long> distinct = employeeIds.stream().distinct().toList();
        for (int i = 0; i < distinct.size(); i += 200) {
            List<Long> chunk = distinct.subList(i, Math.min(i + 200, distinct.size()));
            try {
                List<EmployeeLeavePayslipSummaryDto> rows = http.get()
                        .uri(uri -> uri.path("/leaves/employees/payslip-summary")
                                .queryParam("ids", chunk.stream().map(String::valueOf)
                                        .collect(java.util.stream.Collectors.joining(",")))
                                .queryParam("periodStart", periodStart)
                                .queryParam("periodEnd", periodEnd)
                                .build())
                        .headers(h -> { if (identity != null) identity.forEach(h::set); })
                        .retrieve()
                        .body(new org.springframework.core.ParameterizedTypeReference<>() {});
                if (rows != null) rows.forEach(r -> out.put(r.employeeId(), r));
            } catch (Exception ex) {
                log.warn("payslip attendance/leave summaries failed for {} employees ({}..{}): {}",
                        chunk.size(), periodStart, periodEnd, ex.getMessage());
            }
        }
        return out;
    }

    public AnnualLeaveCalcBalanceResponseDto annualLeaveBalance(
            long employeeId,
            long leaveTypeId,
            LocalDate calculationMonth) {

        try {
            return http.get()
                    .uri(uri -> uri
                            .path("/leaves/employeeId/{employeeId}/annual-leave/{leaveTypeId}/calculation-month/{month}/balance")
                            .build(
                                    employeeId,
                                    leaveTypeId,
                                    calculationMonth
                            ))
                    .retrieve()
                    .body(AnnualLeaveCalcBalanceResponseDto.class);

        } catch (HttpClientErrorException.NotFound ex) {

            // No balance found — handled as normal business case
            return null;

        } catch (Exception ex) {

            // Actual Attendance/service failure
            throw new UpstreamError(
                    PayrollErrorCode.ANNUAL_LEAVE_BALANCE_NOT_FOUND,
                    "annual leave balance request failed for employee %d, leaveTypeId=%d, calculationMonth=%s: %s"
                            .formatted(
                                    employeeId,
                                    leaveTypeId,
                                    calculationMonth,
                                    ex.getMessage()
                            ),
                    ex
            );
        }
    }

    /**
     * Creates a leave balance entry for the next cycle with carry-over for unpaid annual leave.
     * This is called when HR marks an annual leave transaction as unpaid.
     */
    public void createAnnualLeaveUnpaidCarry(
            long annualLeaveTransactionId,
            long employeeId,
            long leaveTypeId,
            BigDecimal carryDays,
            LocalDate joiningDate) {
        try {
            http.post()
                    .uri("/leaves/annual-leave/unpaid-carry")
                    .body(new AnnualLeaveUnpaidCarryRequest(
                            annualLeaveTransactionId,
                            employeeId,
                            leaveTypeId,
                            carryDays,
                            joiningDate
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw new UpstreamError(
                    PayrollErrorCode.ANNUAL_LEAVE_BALANCE_NOT_FOUND,
                    "annual leave unpaid carry creation failed for tx %d, employee %d, leaveTypeId=%d, days=%s: %s"
                            .formatted(annualLeaveTransactionId, employeeId, leaveTypeId, carryDays, ex.getMessage()),
                    ex
            );
        }
    }

    /**
     * Removes a leave balance entry created from annual leave unpaid.
     * This is called when HR changes an unpaid annual leave transaction back to paid.
     */
    public void removeAnnualLeaveUnpaidCarry(long annualLeaveTransactionId) {
        try {
            http.delete()
                    .uri(uri -> uri
                            .path("/leaves/annual-leave/unpaid-carry/{annualLeaveTransactionId}")
                            .build(annualLeaveTransactionId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw new UpstreamError(
                    PayrollErrorCode.ANNUAL_LEAVE_BALANCE_NOT_FOUND,
                    "annual leave unpaid carry removal failed for tx %d: %s"
                            .formatted(annualLeaveTransactionId, ex.getMessage()),
                    ex
            );
        }
    }

    public record AnnualLeaveUnpaidCarryRequest(
            Long annualLeaveTransactionId,
            Long employeeId,
            Long leaveTypeId,
            BigDecimal carryDays,
            LocalDate joiningDate
    ) {
    }
}
