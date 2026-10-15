-- ---------------------------------------------------------------------
-- Shift start / end as full date-times on each attendance record, for
-- payroll. Start = attendance_date + shift_start_time; end = the same date
-- + shift_end_time, plus one day when the shift crosses midnight
-- (shift_end_time <= shift_start_time — same rule as ShiftResolution).
-- Set by AttendanceRecord's @PrePersist / @PreUpdate from the time
-- columns; this backfills existing rows.
-- ---------------------------------------------------------------------

alter table attendance_records
    add column shift_start_date_time timestamp,
    add column shift_end_date_time   timestamp;

update attendance_records
   set shift_start_date_time = attendance_date + shift_start_time,
       shift_end_date_time   = attendance_date + shift_end_time
                               + case when shift_end_time <= shift_start_time
                                      then interval '1 day' else interval '0' end
 where shift_start_time is not null
   and shift_end_time is not null;
