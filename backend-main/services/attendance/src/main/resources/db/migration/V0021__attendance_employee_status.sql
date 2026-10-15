-- Track employment status on the attendance-side employee mirror so the
-- nightly close can select active employees from this local table instead
-- of paging employee-service over HTTP at 02:00 (with a service-account
-- bearer token). Kept in sync by the employee.* event consumer:
--   * employee.created                          → ACTIVE (column default)
--   * employee.updated carrying
--     changes.employmentStatus                  → the new status
--   * employee.terminated                       → TERMINATED
-- The nightly close filters employment_status = 'ACTIVE', which mirrors the
-- old `GET /employees?status=ACTIVE` filter exactly (employee-service's
-- spec matches employmentStatus == ACTIVE, so ON_NOTICE / ON_LEAVE /
-- SUSPENDED were — and stay — excluded from absent-marking).
alter table attendance_employees
    add column employment_status varchar(16) not null default 'ACTIVE';

-- One-shot backfill from the employee schema. Cross-schema reads at runtime
-- are forbidden; this is a migration-time bootstrap only, the same trade-off
-- V0014 made when it first populated this table. employee.employment_status
-- is @Enumerated(STRING), so the value is the enum name verbatim.
update attendance_employees ae
    set employment_status = e.employment_status
    from employee.employees e
    where e.id = ae.employee_id;

create index idx_attendance_employees_status
    on attendance_employees (employment_status);
