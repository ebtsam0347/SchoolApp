-- §4.2 employee_shifts — current shift assignment per legacy employee.
-- Without this, AttendanceRecomputeService.resolve() returns empty for
-- every new punch and late/early/worked minutes can't be computed. The
-- denormalized shift_* fields on attendance_records keep historical reads
-- working, but the live punch flow needs this row.
--
-- Effective_from prefers the legacy `shifteffectivedate` (almost always
-- set in apis_empcompanyinfo); falls back to joining_date.
--
-- §4.3 attendance_records — 210K rows from staging.payroll_payroll_attendance.
-- Status derivation simplified for ETL: PRESENT / WEEKEND / HOLIDAY / ABSENT.
-- LEAVE status is left for a post-load update once §4.5 (leave_requests) is
-- in place — it's an UPDATE pass keyed on (employee_id, date) joined to
-- approved leave_request_days.
--
-- Worked/late/early/OT minutes are derived heuristically from legacy fields
-- (no AttendanceRecomputeService call). Good enough for migrated history;
-- new attendance writes go through the recompute service.

BEGIN;

-- ---------------------------------------------------------------------------
-- §4.2 employee_shifts — one row per legacy employee, current shift only
-- ---------------------------------------------------------------------------
INSERT INTO attendance.employee_shifts (
    employee_id, shift_id, effective_from, effective_to,
    created_by, updated_by
)
SELECT
    emap.target_id,
    smap.target_id,
    COALESCE(NULLIF(eci.shifteffectivedate, '')::date, e.joining_date),
    NULL,
    'legacy-migration', 'legacy-migration'
FROM staging.apis_empcompanyinfo eci
JOIN migration.id_map emap
  ON emap.source_table = 'apis_employeinfo' AND emap.target_table = 'employees'
 AND emap.source_id = eci.employee_id
JOIN employee.employees e ON e.id = emap.target_id
JOIN migration.id_map smap
  ON smap.source_table = 'apis_shift' AND smap.target_table = 'shifts'
 AND smap.source_id = eci.shift_id
ON CONFLICT (employee_id, effective_from) DO NOTHING;

INSERT INTO attendance.attendance_records (
    employee_id, attendance_date, check_in, check_out,
    effective_shift, shift_start_time, shift_end_time,
    shift_break_start_time, shift_break_end_time, shift_grace_minutes,
    status,
    late_minutes, early_minutes, worked_minutes,
    ot_normal_minutes, ot_extra_minutes, ot_special_minutes,
    source, created_by, updated_by
)
SELECT
    e.id,
    s.attdate::date,
    NULLIF(s.in_time_datetime, '')::timestamp,
    NULLIF(s.out_time_datetime, '')::timestamp,
    smap.target_id,
    NULLIF(s.shiftst, '')::time,
    NULLIF(s.shiftet, '')::time,
    NULLIF(s.breakst, '00:00:00')::time,
    NULLIF(s.breaket, '00:00:00')::time,
    10,
    -- status
    CASE
        WHEN NULLIF(s.in_time_datetime, '') IS NOT NULL
          OR NULLIF(s.out_time_datetime, '') IS NOT NULL THEN 'PRESENT'
        WHEN EXTRACT(DOW FROM s.attdate::date) = 0 THEN 'WEEKEND'
        WHEN EXISTS (SELECT 1 FROM attendance.holidays h
                     WHERE h.holiday_date = s.attdate::date) THEN 'HOLIDAY'
        ELSE 'ABSENT'
    END,
    -- late_minutes = max(0, in_time - shift_start)
    CASE
        WHEN NULLIF(s.in_time_datetime, '') IS NULL OR NULLIF(s.shiftst, '') IS NULL THEN 0
        ELSE GREATEST(0, EXTRACT(EPOCH FROM (
                s.in_time_datetime::timestamp::time - s.shiftst::time
             ))::int / 60)
    END,
    -- early_minutes = max(0, shift_end - out_time)  (only when both present and out_time before shift_end)
    CASE
        WHEN NULLIF(s.out_time_datetime, '') IS NULL OR NULLIF(s.shiftet, '') IS NULL THEN 0
        ELSE GREATEST(0, EXTRACT(EPOCH FROM (
                s.shiftet::time - s.out_time_datetime::timestamp::time
             ))::int / 60)
    END,
    -- worked_minutes from `duration` HH:MM:SS (or 0 if missing)
    COALESCE(
        EXTRACT(EPOCH FROM NULLIF(s.duration, '')::interval)::int / 60,
        0
    ),
    -- OT: legacy `otstatus=True` → first 120 into normal, rest into extra (using `dta`, fallback 0)
    -- specialotstatus=True → all into special
    -- Without a duration delta over expected, default 0 across the board.
    0, 0, 0,
    CASE WHEN s.manual = 'True' THEN 'OPERATOR' ELSE 'BIOMETRIC' END,
    'legacy-migration', 'legacy-migration'
FROM staging.payroll_payroll_attendance s
JOIN employee.employees e
  ON e.employee_code = s.cardno
LEFT JOIN migration.id_map smap
  ON smap.source_table = 'apis_shift' AND smap.target_table = 'shifts'
 AND smap.source_id = s.shift_id
WHERE s.attdate IS NOT NULL AND s.attdate <> ''
ON CONFLICT (employee_id, attendance_date) DO NOTHING;

COMMIT;

SELECT 'employee_shifts'            AS metric, count(*)::text FROM attendance.employee_shifts UNION ALL
SELECT 'attendance_records',                   count(*)::text FROM attendance.attendance_records UNION ALL
SELECT 'PRESENT',                              count(*)::text FROM attendance.attendance_records WHERE status='PRESENT' UNION ALL
SELECT 'ABSENT',                               count(*)::text FROM attendance.attendance_records WHERE status='ABSENT' UNION ALL
SELECT 'WEEKEND',                              count(*)::text FROM attendance.attendance_records WHERE status='WEEKEND' UNION ALL
SELECT 'HOLIDAY',                              count(*)::text FROM attendance.attendance_records WHERE status='HOLIDAY' UNION ALL
SELECT 'source BIOMETRIC',                     count(*)::text FROM attendance.attendance_records WHERE source='BIOMETRIC' UNION ALL
SELECT 'source OPERATOR',                      count(*)::text FROM attendance.attendance_records WHERE source='OPERATOR';
