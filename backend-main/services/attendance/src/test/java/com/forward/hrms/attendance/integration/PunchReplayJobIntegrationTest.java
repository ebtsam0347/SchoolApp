package com.forward.hrms.attendance.integration;

import com.forward.hrms.attendance.entity.AttendanceDevice;
import com.forward.hrms.attendance.entity.AttendanceEmployee;
import com.forward.hrms.attendance.entity.PunchUnmatched;
import com.forward.hrms.attendance.repository.AttendanceDeviceRepository;
import com.forward.hrms.attendance.repository.AttendanceEmployeeRepository;
import com.forward.hrms.attendance.repository.AttendancePunchRepository;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.attendance.repository.PunchUnmatchedRepository;
import com.forward.hrms.attendance.service.DevicePunchReplayJob;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the OOM guard on the replay job. The job must honor
 * {@code findResolvable(Pageable)} — pulling everything in one shot
 * killed the JVM when the queue grew to ~560k rows after a poller
 * date-parsing bug. This test seeds 3× the configured batch size and
 * runs one tick; should drain exactly {@code batchSize} rows, leaving
 * the rest for the next tick.
 */
@SpringBootTest
@WithTestcontainers
class PunchReplayJobIntegrationTest {

    @Autowired DevicePunchReplayJob replayJob;
    @Autowired PunchUnmatchedRepository unmatched;
    @Autowired AttendanceEmployeeRepository attendanceEmployees;
    @Autowired AttendancePunchRepository punches;
    @Autowired AttendanceRecordRepository records;
    @Autowired AttendanceDeviceRepository devices;

    private long deviceId;

    @BeforeEach
    void cleanAndSeed() {
        records.deleteAll();
        punches.deleteAll();
        unmatched.deleteAll();
        attendanceEmployees.deleteAll();
        devices.deleteAll();

        AttendanceDevice d = new AttendanceDevice();
        d.setCode("TEST-DEV-REPLAY");
        d.setName("Test Replay Device");
        deviceId = devices.save(d).getId();
    }

    @Test
    void replayRespectsBatchSize_drainsOneBatchPerTick() {
        // Drop the configured batch size for this test so we don't have to
        // seed 10k rows. The default is 10000; we run with 50.
        ReflectionTestUtils.setField(replayJob, "batchSize", 50);
        int total = 150;

        seedAttendanceEmployee(9001L, "REPLAY-EMP", "Replay Emp");

        LocalDate base = LocalDate.now().minusDays(3);
        for (int i = 0; i < total; i++) {
            PunchUnmatched u = new PunchUnmatched();
            u.setDeviceId(deviceId);
            u.setEmployeeCode("REPLAY-EMP");
            // Distinct punched_at values to satisfy the unique key.
            u.setPunchedAt(LocalDateTime.of(base, LocalTime.of(0, 0)).plusMinutes(i));
            u.setFirstSeenAt(LocalDateTime.now());
            unmatched.save(u);
        }

        // First tick: drains exactly batchSize.
        replayJob.replay();
        assertThat(unmatched.count()).isEqualTo(total - 50);
        assertThat(punches.count()).isEqualTo(50);

        // Second tick: drains another batchSize.
        replayJob.replay();
        assertThat(unmatched.count()).isEqualTo(total - 100);
        assertThat(punches.count()).isEqualTo(100);

        // Third tick: drains the rest.
        replayJob.replay();
        assertThat(unmatched.count()).isZero();
        assertThat(punches.count()).isEqualTo(total);
    }

    @Test
    void noResolvableRows_isANoop() {
        // Unmatched row with no matching snapshot — never resolvable.
        PunchUnmatched u = new PunchUnmatched();
        u.setDeviceId(deviceId);
        u.setEmployeeCode("STILL-UNKNOWN");
        u.setPunchedAt(LocalDateTime.now().minusDays(1));
        u.setFirstSeenAt(LocalDateTime.now());
        unmatched.save(u);

        replayJob.replay();

        assertThat(unmatched.count()).isOne();
        assertThat(punches.count()).isZero();
        assertThat(unmatched.findResolvable(PageRequest.of(0, 100))).isEmpty();
    }

    @Test
    void rowsOverMaxAttempts_areDroppedNotReplayed() {
        ReflectionTestUtils.setField(replayJob, "maxAttempts", 3);
        seedAttendanceEmployee(9100L, "MAXED-EMP", "Maxed Emp");

        PunchUnmatched u = new PunchUnmatched();
        u.setDeviceId(deviceId);
        u.setEmployeeCode("MAXED-EMP");
        u.setPunchedAt(LocalDateTime.now().minusDays(1));
        u.setFirstSeenAt(LocalDateTime.now());
        u.setAttempts(3); // already at the limit; next attempt would be 4
        unmatched.save(u);

        replayJob.replay();

        // Dropped (audit-logged), not replayed.
        assertThat(unmatched.count()).isZero();
        assertThat(punches.count()).isZero();
        assertThat(records.count()).isZero();
    }

    private void seedAttendanceEmployee(long employeeId, String code, String fullName) {
        AttendanceEmployee e = new AttendanceEmployee();
        e.setEmployeeId(employeeId);
        e.setEmployeeCode(code);
        e.setFullName(fullName);
        attendanceEmployees.save(e);
    }
}
