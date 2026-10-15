-- §8 leave_balances: legacy WebHRMS doesn't keep a per-employee balance row
-- (quotas live at the leave-type level only — `apis_hrm_hr_leavesquota`),
-- so balances are derived. One row per (ACTIVE employee, leave_type) for
-- the cycle containing today.
--
-- Balances are NOT calendar-year any more — attendance V0022 introduced the
-- per-leave-type cycle model (`leave_types.cycle_anchor`), so the window is
-- JOINING (anniversary), FISCAL (Jul–Jun) or NONE (calendar) depending on
-- the type. The helpers below mirror `LeaveCycleResolver` exactly; if that
-- class changes, change these with it.
--
-- Mapping:
--   allocated = leave_types.default_per_year, prorated for FISCAL types
--     with prorate_on_entry when the joining date falls inside the window
--     (matches `LeaveCycleResolver.allocationFor`).
--   used      = sum(days) over leave_requests with status='APPROVED'
--               whose to_date falls inside the employee's own cycle window
--               (legacy migrated all historical leave with
--               status='APPROVED'; rejected requests aren't counted).
--   pending   = 0. Legacy migration set every imported request to APPROVED
--               (see 05_leave_data.sql §4.5 status logic — anything not
--               REJECTED becomes APPROVED). No pending rows to count.
--   carried   = 0. Legacy didn't capture explicit carry-forward; HR can
--               adjust if a year-end carryover applies.
--
-- Skips TERMINATED employees — they don't get fresh allocations.

BEGIN;

-- Cycle math must agree exactly with attendance's LeaveCycleResolver,
-- otherwise the first balance the service touches opens a *second* row for
-- a different window. Helpers live in the scratch `migration` schema and go
-- away with it.

-- Joining anniversary in `y`, clamping Feb-29 to Feb-28 in non-leap years.
CREATE OR REPLACE FUNCTION migration.leave_anniversary(j date, y int)
RETURNS date LANGUAGE sql IMMUTABLE AS $fn$
    SELECT make_date(
        y,
        extract(month from j)::int,
        least(
            extract(day from j)::int,
            extract(day from (make_date(y, extract(month from j)::int, 1)
                              + interval '1 month - 1 day'))::int
        )
    );
$fn$;

-- Inclusive [start, end] cycle window containing `on_date`.
--   JOINING (with a known joining date) — anniversary window
--   FISCAL                              — Jul 1 → Jun 30 straddle
--   NONE, or JOINING without a date     — calendar year
CREATE OR REPLACE FUNCTION migration.leave_cycle(
    anchor text, j date, on_date date,
    OUT cycle_start date, OUT cycle_end date)
LANGUAGE sql IMMUTABLE AS $fn$
    WITH w AS (
        SELECT CASE
            WHEN anchor = 'JOINING' AND j IS NOT NULL THEN
                CASE WHEN on_date < migration.leave_anniversary(j, extract(year from on_date)::int)
                     THEN migration.leave_anniversary(j, extract(year from on_date)::int - 1)
                     ELSE migration.leave_anniversary(j, extract(year from on_date)::int)
                END
            WHEN anchor = 'FISCAL' THEN
                make_date(CASE WHEN extract(month from on_date) >= 7
                               THEN extract(year from on_date)::int
                               ELSE extract(year from on_date)::int - 1 END, 7, 1)
            ELSE make_date(extract(year from on_date)::int, 1, 1)
        END AS cs
    )
    SELECT w.cs,
           CASE
               WHEN anchor = 'JOINING' AND j IS NOT NULL THEN
                   migration.leave_anniversary(j, extract(year from w.cs)::int + 1) - 1
               WHEN anchor = 'FISCAL' THEN (w.cs + interval '1 year - 1 day')::date
               ELSE make_date(extract(year from w.cs)::int, 12, 31)
           END
    FROM w;
$fn$;

-- Entry-cycle proration, mirroring LeaveCycleResolver.allocationFor /
-- prorate: full x remaining/full days, rounded UP to the nearest half-day.
CREATE OR REPLACE FUNCTION migration.leave_allocation(
    full_days numeric, anchor text, prorate boolean, j date, cs date, ce date)
RETURNS numeric LANGUAGE sql IMMUTABLE AS $fn$
    SELECT CASE
        WHEN anchor = 'FISCAL' AND prorate AND j IS NOT NULL AND j BETWEEN cs AND ce THEN
            CASE
                WHEN (ce - j + 1) >= (ce - cs + 1) THEN full_days
                WHEN (ce - j + 1) <= 0             THEN 0::numeric
                ELSE ceil(round(full_days * (ce - j + 1)::numeric
                                          / (ce - cs + 1)::numeric, 6) * 2) / 2
            END
        ELSE full_days
    END;
$fn$;

WITH base AS (
    SELECT e.id                AS employee_id,
           ae.joining_date     AS joining_date,
           lt.id               AS leave_type_id,
           lt.cycle_anchor     AS cycle_anchor,
           lt.prorate_on_entry AS prorate_on_entry,
           lt.default_per_year AS default_per_year
    FROM employee.employees e
    LEFT JOIN attendance.attendance_employees ae ON ae.employee_id = e.id
    CROSS JOIN attendance.leave_types lt
    WHERE e.employment_status = 'ACTIVE'
      AND lt.active = true
), grid AS (
    SELECT b.*,
           w.cycle_start,
           w.cycle_end,
           migration.leave_allocation(b.default_per_year, b.cycle_anchor,
                                      b.prorate_on_entry, b.joining_date,
                                      w.cycle_start, w.cycle_end) AS allocated
    FROM base b
    CROSS JOIN LATERAL migration.leave_cycle(b.cycle_anchor, b.joining_date, current_date) w
)
INSERT INTO attendance.leave_balances (
    employee_id, leave_type_id, year, cycle_start, cycle_end,
    allocated, used, pending, carried,
    created_by, updated_by
)
SELECT g.employee_id,
       g.leave_type_id,
       extract(year FROM g.cycle_start)::int,   -- LeaveBalanceService: year = cycleStart.getYear()
       g.cycle_start,
       g.cycle_end,
       g.allocated,
       -- Cap used at allocated to avoid the legacy "took more than quota"
       -- artifact (e.g. unpaid casual rolled into casual). FSHRMS doesn't
       -- model overdraw; HR adjusts if needed.
       LEAST(COALESCE(u.used, 0), g.allocated)::numeric(5,2),
       0,
       0,
       'legacy-migration', 'legacy-migration'
FROM grid g
LEFT JOIN LATERAL (
    -- Usage is counted inside the employee's own cycle window, not the
    -- calendar year — the two differ for ANNUAL (JOINING) and the
    -- FISCAL-anchored types.
    SELECT sum(COALESCE(lr.days, 0))::numeric(8,2) AS used
    FROM attendance.leave_requests lr
    WHERE lr.employee_id   = g.employee_id
      AND lr.leave_type_id = g.leave_type_id
      AND lr.status        = 'APPROVED'
      AND lr.to_date BETWEEN g.cycle_start AND g.cycle_end
) u ON true
ON CONFLICT (employee_id, leave_type_id, cycle_start) DO NOTHING;

COMMIT;

-- summary
SELECT 'leave_balances'                 AS metric, count(*) FROM attendance.leave_balances UNION ALL
SELECT 'distinct employees',                      count(DISTINCT employee_id) FROM attendance.leave_balances UNION ALL
SELECT 'rows with used > 0',                      count(*) FROM attendance.leave_balances WHERE used > 0 UNION ALL
SELECT 'rows fully used',                         count(*) FROM attendance.leave_balances WHERE used >= allocated AND allocated > 0;
