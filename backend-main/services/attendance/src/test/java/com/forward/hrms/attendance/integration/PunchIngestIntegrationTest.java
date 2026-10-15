package com.forward.hrms.attendance.integration;

import com.forward.hrms.attendance.entity.AttendanceDevice;
import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.entity.PunchUnmatched;
import com.forward.hrms.attendance.repository.AttendanceDeviceRepository;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.AttendancePunchRepository;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.PunchUnmatchedRepository;
import com.forward.hrms.attendance.service.PunchService;
import com.forward.hrms.attendance.service.PunchService.BiometricRow;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end biometric ingest. Validates two doctrine-level invariants:
 *
 * <ul>
 *   <li><b>Recompute is per-day, not per-punch</b> — N punches across M
 *       distinct {@code (employee, date)} pairs produces M
 *       {@code attendance_records} rows. Per-row recompute would still
 *       work for correctness but kills replay-job throughput
 *       (~12min for 10k rows vs ~100s).</li>
 *   <li><b>Unknown employee_codes land in the unmatched queue</b> — and
 *       are flagged as resolvable once the {@code attendance_employees}
 *       snapshot row catches up via the {@code employee.created} consumer.</li>
 * </ul>
 *
 * <p>Real Postgres + Kafka via Testcontainers. No shift / holiday / leave
 * seeding — recompute lands status=ABSENT and 0 minutes when shift is
 * unresolved. Sufficient for the per-day-aggregation assertion.
 */
@SpringBootTest
@WithTestcontainers
class PunchIngestIntegrationTest {

    @Autowired PunchService punchService;
    @Autowired AttendanceEmployeeRepository attendanceEmployees;
    @Autowired AttendancePunchRepository punches;
    @Autowired AttendanceRecordRepository records;
    @Autowired PunchUnmatchedRepository unmatched;
    @Autowired AttendanceDeviceRepository devices;

    private LocalDate dayOne;
    private LocalDate dayTwo;
    private long deviceId;

    @BeforeEach
    void cleanAndSeed() {
        unmatched.deleteAll();
        records.deleteAll();
        punches.deleteAll();
        attendanceEmployees.deleteAll();
        devices.deleteAll();

        // Both fully in the past so an evening punch isn't tripped by
        // PunchService.rejectFuture (which silently drops future rows
        // from a biometric batch).
        dayOne = LocalDate.now().minusDays(2);
        dayTwo = LocalDate.now().minusDays(1);

        AttendanceDevice d = new AttendanceDevice();
        d.setCode("TEST-DEV-1");
        d.setName("Test Device");
        deviceId = devices.save(d).getId();
    }

    @Test
    void multiplePunchesAcrossDays_yieldOneRecordPerEmployeeDay() {
        seedAttendanceEmployee(1001L, "EMP-1001", "Alice");
        seedAttendanceEmployee(1002L, "EMP-1002", "Bob");

        List<BiometricRow> rows = new ArrayList<>();
        // Alice — 5 punches dayOne
        for (int h : new int[]{8, 12, 13, 17, 19}) rows.add(row("EMP-1001", dayOne, h, deviceId));
        // Bob — 3 punches dayOne + 2 punches dayTwo
        for (int h : new int[]{8, 13, 17})         rows.add(row("EMP-1002", dayOne, h, deviceId));
        for (int h : new int[]{9, 18})             rows.add(row("EMP-1002", dayTwo, h, deviceId));

        var result = punchService.ingestBiometricBatch(rows);

        assertThat(result.ingested()).hasSize(rows.size());
        assertThat(result.skipped()).isEmpty();

        // Per-day-aggregation invariant.
        assertThat(records.findAll())
                .extracting(AttendanceRecord::getEmployeeId, AttendanceRecord::getAttendanceDate)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1001L, dayOne),
                        org.assertj.core.groups.Tuple.tuple(1002L, dayOne),
                        org.assertj.core.groups.Tuple.tuple(1002L, dayTwo));

        // Each record's check_in / check_out come from min / max of that day's punches.
        AttendanceRecord aliceDayOne = records.findByEmployeeIdAndAttendanceDate(1001L, dayOne).orElseThrow();
        assertThat(aliceDayOne.getCheckIn()).isEqualTo(dayOne.atTime(8, 0));
        assertThat(aliceDayOne.getCheckOut()).isEqualTo(dayOne.atTime(19, 0));
    }

    @Test
    void unknownEmployeeCode_landsInUnmatchedQueue() {
        seedAttendanceEmployee(2001L, "EMP-2001", "Carol");

        var result = punchService.ingestBiometricBatch(List.of(
                row("EMP-2001",  dayTwo, 9,  deviceId),
                row("UNKNOWN-1", dayTwo, 10, deviceId),
                row("UNKNOWN-2", dayTwo, 11, deviceId)
        ));

        assertThat(result.ingested()).hasSize(1);
        assertThat(result.skipped()).hasSize(2);
        assertThat(result.skipped()).extracting(s -> s.employeeCode())
                .containsExactlyInAnyOrder("UNKNOWN-1", "UNKNOWN-2");

        // Unmatched rows queued, awaiting a snapshot row.
        assertThat(unmatched.findAll()).extracting(PunchUnmatched::getEmployeeCode)
                .containsExactlyInAnyOrder("UNKNOWN-1", "UNKNOWN-2");
        // Not yet resolvable — no AttendanceEmployee row yet.
        assertThat(unmatched.findResolvable(PageRequest.of(0, 100))).isEmpty();

        // Snapshot row arrives (via employee.created consumer in prod).
        seedAttendanceEmployee(2002L, "UNKNOWN-1", "DelayedCarol");
        assertThat(unmatched.findResolvable(PageRequest.of(0, 100)))
                .extracting(PunchUnmatched::getEmployeeCode)
                .containsExactly("UNKNOWN-1");
    }

    @Test
    void duplicatePunch_isSilentlyDropped() {
        seedAttendanceEmployee(3001L, "EMP-3001", "Dan");

        BiometricRow once = row("EMP-3001", dayTwo, 10, deviceId);
        var first = punchService.ingestBiometricBatch(List.of(once));
        assertThat(first.ingested()).hasSize(1);

        // Same employeeCode + same instant → existing row returned, no
        // new insert, no second punch.
        var second = punchService.ingestBiometricBatch(List.of(once));
        assertThat(second.ingested()).isEmpty();

        assertThat(punches.findAll()).hasSize(1);
    }

    // ---------------------------------------------------------------- helpers

    private void seedAttendanceEmployee(long employeeId, String code, String fullName) {
        AttendanceEmployee e = new AttendanceEmployee();
        e.setEmployeeId(employeeId);
        e.setEmployeeCode(code);
        e.setFullName(fullName);
        attendanceEmployees.save(e);
    }

    private static BiometricRow row(String employeeCode, LocalDate date, int hour, long deviceId) {
        return new BiometricRow(employeeCode, LocalDateTime.of(date, LocalTime.of(hour, 0)), deviceId);
    }
}
