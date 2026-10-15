package com.forward.hrms.attendance.integration;

import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * shift_start_date_time / shift_end_date_time are derived by
 * AttendanceRecord's @PrePersist / @PreUpdate. Reads go back to the DB so
 * the assertion proves Hibernate wrote the derived values, not just that
 * the in-memory entity holds them.
 */
@SpringBootTest
@WithTestcontainers
class RecordShiftDateTimeIntegrationTest {

    @Autowired AttendanceRecordRepository records;

    private final LocalDate day = LocalDate.of(2026, 10, 12);

    @BeforeEach
    void clean() {
        records.deleteAll();
    }

    @Test
    void dayShift_endsSameDay_andNightShiftOnUpdate_endsNextDay() {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployeeId(9001L);
        r.setAttendanceDate(day);
        r.setStatus(AttendanceStatus.PRESENT);
        r.setShiftStartTime(LocalTime.of(7, 45));
        r.setShiftEndTime(LocalTime.of(16, 30));
        long id = records.save(r).getId();

        AttendanceRecord saved = records.findById(id).orElseThrow();
        assertThat(saved.getShiftStartDateTime()).isEqualTo(day.atTime(7, 45));
        assertThat(saved.getShiftEndDateTime()).isEqualTo(day.atTime(16, 30));

        // Same row moved to a night shift — the update path must re-derive.
        saved.setShiftStartTime(LocalTime.of(20, 0));
        saved.setShiftEndTime(LocalTime.of(5, 0));
        records.save(saved);

        AttendanceRecord night = records.findById(id).orElseThrow();
        assertThat(night.getShiftStartDateTime()).isEqualTo(day.atTime(20, 0));
        assertThat(night.getShiftEndDateTime()).isEqualTo(day.plusDays(1).atTime(5, 0));
    }

    @Test
    void noShift_leavesDateTimesNull() {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployeeId(9002L);
        r.setAttendanceDate(day);
        r.setStatus(AttendanceStatus.ABSENT);
        long id = records.save(r).getId();

        AttendanceRecord saved = records.findById(id).orElseThrow();
        assertThat(saved.getShiftStartDateTime()).isNull();
        assertThat(saved.getShiftEndDateTime()).isNull();
    }
}
