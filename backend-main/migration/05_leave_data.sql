-- §4.5 leave_requests + leave_request_days from staging.payroll_payroll_leaves.
-- §4.6 short_leave_requests from staging.payroll_payroll_shortleaves.
--
-- request_code: synthesized as `LR-{padded legacy id}` (and `SL-...`).
-- Skips approval_steps synthesis — historical workflow rows aren't needed
-- for migrated data; new leaves go through the workflow normally.

BEGIN;

-- ---------------------------------------------------------------------------
-- §4.5 leave_requests (parent rows)
-- Resolve cardno→employee, leaves_id→leave_type_id via legacy quota name.
-- ---------------------------------------------------------------------------
WITH legacy AS (
    SELECT
        l.id AS source_id,
        e.id AS employee_id,
        lt.id AS leave_type_id,
        l.leavestdate::date AS from_date,
        l.leaveeddate::date AS to_date,
        (l.halfdaystatus = 'True') AS half_day,
        CASE upper(trim(l.halfdaytime))
            WHEN '1ST HALF' THEN 'AM'
            WHEN '2ND HALF' THEN 'PM'
            ELSE NULL
        END AS half_day_part,
        COALESCE(NULLIF(l.totaldays, '')::numeric, 1) AS days,
        COALESCE(
            NULLIF(left(trim(l.leaveremarks), 4096), ''),
            NULLIF(left(trim(l.appovalremarks), 4096), '')
        ) AS reason,
        CASE
            WHEN l.leavestatus = 'False' OR l.hrstatus = 'False' OR l.payrollstatus = 'False'
                THEN 'REJECTED'
            WHEN COALESCE(NULLIF(l.leavestatus, ''), 'True') = 'True'
             AND COALESCE(NULLIF(l.hrstatus, ''), 'True') = 'True'
             AND COALESCE(NULLIF(l.payrollstatus, ''), 'True') = 'True'
                THEN 'APPROVED'
            ELSE 'PENDING'
        END AS status,
        COALESCE(NULLIF(l.entrydatetime, '')::timestamp, now()) AS created_at
    FROM staging.payroll_payroll_leaves l
    JOIN employee.employees e ON e.employee_code = l.cardno
    JOIN staging.apis_hrm_hr_leavesquota q ON q.id = l.leaves_id
    JOIN attendance.leave_types lt
      ON lt.code = CASE upper(trim(q.name))
                     WHEN 'MEDICAL / M1'     THEN 'MEDICAL_M1'  -- renamed from 'M1' by attendance V0025
                     WHEN 'M2'               THEN 'M2'
                     WHEN 'CASUAL'           THEN 'CASUAL'
                     WHEN 'ANNUAL'           THEN 'ANNUAL'
                     WHEN 'SPECIAL'          THEN 'SPECIAL'
                     WHEN 'FACTORY DUTY'     THEN 'FACTORY_DUTY'
                     WHEN 'MATERNITY LEAVES' THEN 'MATERNITY'
                     WHEN 'REST DAY'         THEN 'REST_DAY'
                     WHEN 'CASUAL UNPAID'    THEN 'UNPAID'
                     ELSE NULL
                   END
    WHERE l.leavestdate IS NOT NULL AND l.leavestdate <> ''
      AND l.leaveeddate IS NOT NULL AND l.leaveeddate <> ''
      AND l.leaveeddate::date >= l.leavestdate::date  -- drop the 1 inverted row
), inserted AS (
    INSERT INTO attendance.leave_requests (
        request_code, employee_id, leave_type_id, from_date, to_date,
        half_day, half_day_part, days, reason, status,
        created_at, updated_at, created_by, updated_by
    )
    SELECT
        'LR-' || lpad(source_id::text, 8, '0'),
        employee_id, leave_type_id, from_date, to_date,
        half_day, half_day_part, days, reason, status,
        created_at, created_at, 'legacy-migration', 'legacy-migration'
    FROM legacy
    RETURNING id, request_code
), mapped AS (
    INSERT INTO migration.id_map (source_table, source_id, target_table, target_id)
    SELECT 'payroll_payroll_leaves', l.source_id::text, 'leave_requests', i.id
    FROM inserted i JOIN legacy l
      ON i.request_code = 'LR-' || lpad(l.source_id::text, 8, '0')
    RETURNING 1
)
SELECT count(*) AS leave_requests_loaded FROM mapped;

-- ---------------------------------------------------------------------------
-- leave_request_days: expand each request's date range into one row per
-- working day (skip Sundays + holidays). Status mirrors the parent.
-- ---------------------------------------------------------------------------
INSERT INTO attendance.leave_request_days (
    leave_request_id, attendance_date, status,
    decided_by, decided_at, decided_at_stage,
    created_by, updated_by
)
SELECT
    lr.id,
    d::date,
    lr.status,
    NULL, NULL, NULL,
    'legacy-migration', 'legacy-migration'
FROM attendance.leave_requests lr
CROSS JOIN LATERAL generate_series(lr.from_date, lr.to_date, '1 day'::interval) d
WHERE lr.created_by = 'legacy-migration'
  AND EXTRACT(DOW FROM d::date) <> 0
  AND NOT EXISTS (
      SELECT 1 FROM attendance.holidays h WHERE h.holiday_date = d::date
  );

-- ---------------------------------------------------------------------------
-- §4.6 short_leave_requests
-- ---------------------------------------------------------------------------
INSERT INTO attendance.short_leave_requests (
    request_code, employee_id, leave_date, start_time, end_time,
    reason, status, created_at, updated_at, created_by, updated_by
)
SELECT
    'SL-' || lpad(s.id::text, 8, '0'),
    e.id,
    s.leavedate::date,
    NULLIF(s.starttime, '')::time,
    NULLIF(s.endtime, '')::time,
    NULLIF(left(trim(s.slremarks), 4096), ''),
    -- Blank approvestatus/hrstatus coalesces to 'True', same as the
    -- leave_requests CASE above — legacy rows left these unset when the
    -- short-leave workflow predated the separate HR sign-off, not because
    -- the request was ever actually left pending. Without the coalesce,
    -- 112 historical rows land on PENDING with no approval_steps synthesized
    -- for them (see comment at top of file) — permanently stuck, invisible
    -- to every manager's inbox since that's chain-driven, not status-driven.
    CASE
        WHEN COALESCE(NULLIF(s.approvestatus, ''), 'True') = 'False'
          OR COALESCE(NULLIF(s.hrstatus, ''), 'True') = 'False' THEN 'REJECTED'
        WHEN COALESCE(NULLIF(s.approvestatus, ''), 'True') = 'True'
         AND COALESCE(NULLIF(s.hrstatus, ''), 'True') = 'True' THEN 'APPROVED'
        ELSE 'PENDING'
    END,
    COALESCE(NULLIF(s.entrydatetime, '')::timestamp, now()),
    COALESCE(NULLIF(s.entrydatetime, '')::timestamp, now()),
    'legacy-migration', 'legacy-migration'
FROM staging.payroll_payroll_shortleaves s
JOIN employee.employees e ON e.employee_code = s.cardno
WHERE s.leavedate IS NOT NULL AND s.leavedate <> ''
  AND NULLIF(s.starttime, '') IS NOT NULL
  AND NULLIF(s.endtime, '') IS NOT NULL
  -- chk_short_leave_window: end_time > start_time
  AND s.endtime::time > s.starttime::time;

COMMIT;

-- ---------------------------------------------------------------------------
-- Patch §4.3 attendance_records: flip ABSENT→LEAVE for dates with an
-- approved leave_request_day. (Currently 0 ABSENT rows since legacy only
-- stored present days, but run it anyway in case future iterations seed
-- absent rows.)
-- ---------------------------------------------------------------------------
UPDATE attendance.attendance_records ar
SET status = 'LEAVE',
    paid = lt.paid
FROM attendance.leave_request_days lrd
JOIN attendance.leave_requests lr ON lr.id = lrd.leave_request_id
JOIN attendance.leave_types lt    ON lt.id = lr.leave_type_id
WHERE ar.status = 'ABSENT'
  AND lrd.status = 'APPROVED'
  AND lrd.attendance_date = ar.attendance_date
  AND lr.employee_id = ar.employee_id;

SELECT 'leave_requests'         AS metric, count(*)::text FROM attendance.leave_requests UNION ALL
SELECT 'lr APPROVED',                      count(*)::text FROM attendance.leave_requests WHERE status='APPROVED' UNION ALL
SELECT 'lr REJECTED',                      count(*)::text FROM attendance.leave_requests WHERE status='REJECTED' UNION ALL
SELECT 'lr PENDING',                       count(*)::text FROM attendance.leave_requests WHERE status='PENDING' UNION ALL
SELECT 'leave_request_days',               count(*)::text FROM attendance.leave_request_days UNION ALL
SELECT 'short_leave_requests',             count(*)::text FROM attendance.short_leave_requests;
