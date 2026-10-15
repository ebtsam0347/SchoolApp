package com.forward.hrms.attendance.scheduler;

import com.forward.hrms.attendance.entity.AttendanceRecord;
import com.forward.hrms.attendance.entity.Shift;
import com.forward.hrms.attendance.enums.AttendanceStatus;
import com.forward.hrms.attendance.repository.AttendanceRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceRecordInitializationService {

    private final AttendanceRecordRepository attendanceRecordRepository;

    @Transactional
    public void initialize(Long employeeId,
                           LocalDate attendanceDate,
                           Shift shift) {

        // Safety check
        if (attendanceRecordRepository.existsByEmployeeIdAndAttendanceDate(
                employeeId,
                attendanceDate)) {
            return;
        }

        attendanceRecordRepository.save(buildAttendanceRecord(
                employeeId,
                attendanceDate,
                shift));

        log.debug("Attendance initialized for employee {}", employeeId);
    }

    private AttendanceRecord buildAttendanceRecord(Long employeeId,
                                                   LocalDate attendanceDate,
                                                   Shift shift) {

        AttendanceRecord record = new AttendanceRecord();

        record.setEmployeeId(employeeId);
        record.setAttendanceDate(attendanceDate);

        /*
         * Shift snapshot
         */
        record.setEffectiveShift(shift.getId());
        record.setShiftStartTime(shift.getStartTime());
        record.setShiftEndTime(shift.getEndTime());
        record.setShiftBreakStartTime(shift.getBreakStartTime());
        record.setShiftBreakEndTime(shift.getBreakEndTime());
        record.setShiftGraceMinutes(shift.getGraceMinutes());

        /*
         * Initial state
         */
        record.setStatus(AttendanceStatus.ABSENT);

        /*
         * Defaults
         */
        record.setLateMinutes(0);
        record.setEarlyMinutes(0);
        record.setWorkedMinutes(0);

        record.setOtNormalMinutes(0);
        record.setOtExtraMinutes(0);
        record.setOtSpecialMinutes(0);

        record.setPaid(null);
        record.setSource(null);
        record.setNotes("Automatically initialized by scheduler.");

        return record;
    }
}