-- §0 reset: wipe everything the §1–§7 ETL inserted so the migration can be
-- re-run end-to-end after a fix to mapping rules (e.g. cardno normalization,
-- attendance device codes). Filters by created_by='legacy-migration' /
-- granted_by='legacy-migration' so seed rows shipped by Flyway stay put,
-- and `auth.users.admin` (created_by='system') / `auth.users.poller-svc`
-- (created_by='manual-bootstrap') survive.
--
-- Idempotent. Safe to run on a half-loaded DB. Run as the DB owner so
-- TRUNCATE … RESTART IDENTITY can reset sequences across schemas.

BEGIN;

-- ---------------------------------------------------------------------------
-- §0.1 auth side: drop only legacy ESS users + their role grants. Keep
-- admin (id=1, created_by=system) and any service accounts.
-- ---------------------------------------------------------------------------
DELETE FROM auth.user_roles
WHERE user_id IN (SELECT id FROM auth.users WHERE created_by = 'legacy-migration');

DELETE FROM auth.sessions
WHERE user_id IN (SELECT id FROM auth.users WHERE created_by = 'legacy-migration');

-- Break employees.user_id FK to legacy-migration auth users before deleting them.
UPDATE employee.employees e
SET user_id = NULL,
    updated_by = 'legacy-migration-reset'
FROM auth.users u
WHERE e.user_id = u.id
  AND u.created_by = 'legacy-migration';

DELETE FROM auth.users WHERE created_by = 'legacy-migration';

-- ---------------------------------------------------------------------------
-- §0.2 attendance side: punches, records, leaves, OT, regularization,
-- shift overrides, employee_shifts, holidays, devices+sync. Order matters
-- only where there are FKs; everything else can truncate in one shot.
-- ---------------------------------------------------------------------------
TRUNCATE attendance.attendance_punch_unmatched RESTART IDENTITY CASCADE;
TRUNCATE attendance.attendance_punches         RESTART IDENTITY CASCADE;
TRUNCATE attendance.attendance_device_sync_tasks RESTART IDENTITY CASCADE;
TRUNCATE attendance.attendance_device_users    RESTART IDENTITY CASCADE;
TRUNCATE attendance.attendance_employee_templates RESTART IDENTITY CASCADE;
TRUNCATE attendance.attendance_device_poll_state RESTART IDENTITY CASCADE;
TRUNCATE attendance.attendance_employees       RESTART IDENTITY CASCADE;
TRUNCATE attendance.attendance_records         RESTART IDENTITY CASCADE;
TRUNCATE attendance.short_leave_requests       RESTART IDENTITY CASCADE;
TRUNCATE attendance.leave_request_days         RESTART IDENTITY CASCADE;
TRUNCATE attendance.leave_requests             RESTART IDENTITY CASCADE;
TRUNCATE attendance.leave_balances             RESTART IDENTITY CASCADE;
TRUNCATE attendance.overtime_requests          RESTART IDENTITY CASCADE;
TRUNCATE attendance.regularization_requests    RESTART IDENTITY CASCADE;
TRUNCATE attendance.attendance_shift_overrides RESTART IDENTITY CASCADE;
TRUNCATE attendance.employee_shifts            RESTART IDENTITY CASCADE;
TRUNCATE attendance.approval_steps             RESTART IDENTITY CASCADE;
TRUNCATE attendance.approval_delegations       RESTART IDENTITY CASCADE;
TRUNCATE attendance.outbox                     RESTART IDENTITY;

-- legacy-loaded catalogs
DELETE FROM attendance.holidays           WHERE created_by = 'legacy-migration';
DELETE FROM attendance.attendance_devices WHERE created_by = 'legacy-migration';
DELETE FROM attendance.shifts             WHERE created_by = 'legacy-migration';

-- ---------------------------------------------------------------------------
-- §0.3 payroll side: payslips, runs, advances, bonuses, deductions,
-- per-employee settings + components + salary + bank.
-- ---------------------------------------------------------------------------
TRUNCATE payroll.payslip_lines                  RESTART IDENTITY CASCADE;
TRUNCATE payroll.payslips                       RESTART IDENTITY CASCADE;
TRUNCATE payroll.payroll_runs                   RESTART IDENTITY CASCADE;
TRUNCATE payroll.permanent_advance_deductions   RESTART IDENTITY CASCADE;
TRUNCATE payroll.permanent_advances             RESTART IDENTITY CASCADE;
TRUNCATE payroll.short_term_advances            RESTART IDENTITY CASCADE;
TRUNCATE payroll.short_term_advance_batches     RESTART IDENTITY CASCADE;
TRUNCATE payroll.bonus_installments             RESTART IDENTITY CASCADE;
TRUNCATE payroll.bonus_event_amounts            RESTART IDENTITY CASCADE;
TRUNCATE payroll.bonus_events                   RESTART IDENTITY CASCADE;
TRUNCATE payroll.off_cycle_payments             RESTART IDENTITY CASCADE;
TRUNCATE payroll.leave_encashments              RESTART IDENTITY CASCADE;
TRUNCATE payroll.increment_batch_lines          RESTART IDENTITY CASCADE;
TRUNCATE payroll.increment_batches              RESTART IDENTITY CASCADE;
TRUNCATE payroll.employee_salary_components     RESTART IDENTITY CASCADE;
TRUNCATE payroll.employee_salaries              RESTART IDENTITY CASCADE;
TRUNCATE payroll.employee_bank_details          RESTART IDENTITY CASCADE;
TRUNCATE payroll.employee_payroll_settings      RESTART IDENTITY CASCADE;
TRUNCATE payroll.outbox                         RESTART IDENTITY;

-- Advance approval history (V0041 moved it off the hand-rolled decision
-- columns onto commons-approval). The advances themselves are truncated
-- above, so every step we wrote has to go with them; the V0041 seed rows
-- in approval_workflow_stage are created_by='system' and stay.
DELETE FROM payroll.approval_steps WHERE created_by = 'legacy-migration';

-- legacy-loaded catalogs
DELETE FROM payroll.salary_components WHERE created_by = 'legacy-migration';
DELETE FROM payroll.banks             WHERE created_by = 'legacy-migration';
DELETE FROM payroll.fiscal_years      WHERE created_by = 'legacy-migration';

-- ---------------------------------------------------------------------------
-- §0.4 employee side: per-employee child rows then employees, then
-- catalogs that the legacy load filled. The seeded provinces/skills stay.
-- ---------------------------------------------------------------------------
TRUNCATE employee.employee_documents       RESTART IDENTITY CASCADE;
TRUNCATE employee.employee_suspensions     RESTART IDENTITY CASCADE;
TRUNCATE employee.disciplinary_actions     RESTART IDENTITY CASCADE;
TRUNCATE employee.emergency_contacts       RESTART IDENTITY CASCADE;
TRUNCATE employee.employee_skills          RESTART IDENTITY CASCADE;
TRUNCATE employee.employee_disabilities    RESTART IDENTITY CASCADE;
TRUNCATE employee.employee_prior_experience RESTART IDENTITY CASCADE;
TRUNCATE employee.employee_certifications  RESTART IDENTITY CASCADE;
TRUNCATE employee.employee_qualifications  RESTART IDENTITY CASCADE;
TRUNCATE employee.employee_exits           RESTART IDENTITY CASCADE;
TRUNCATE employee.employee_history         RESTART IDENTITY CASCADE;
TRUNCATE employee.employment_contracts     RESTART IDENTITY CASCADE;

-- Drop manager FK + department head FK before truncating the employees row.
UPDATE employee.departments SET head_id = NULL WHERE head_id IS NOT NULL;

TRUNCATE employee.employees RESTART IDENTITY CASCADE;
TRUNCATE employee.outbox    RESTART IDENTITY;
TRUNCATE employee.saga_state RESTART IDENTITY;

-- legacy-loaded catalog rows; seeded ones (created_by='system') stay.
DELETE FROM employee.sections      WHERE created_by = 'legacy-migration';
DELETE FROM employee.departments   WHERE created_by = 'legacy-migration';
DELETE FROM employee.designations  WHERE created_by = 'legacy-migration';
DELETE FROM employee.grades        WHERE created_by = 'legacy-migration';
DELETE FROM employee.qualifications WHERE created_by = 'legacy-migration';
DELETE FROM employee.cities        WHERE created_by = 'legacy-migration';

-- ---------------------------------------------------------------------------
-- §0.5 migration scratch
-- ---------------------------------------------------------------------------
TRUNCATE migration.id_map;

-- Initial ESS passwords belong to the auth.users rows deleted in §0.1;
-- keeping them would hand HR credentials for accounts that no longer
-- exist. 07 regenerates the table on the next run.
TRUNCATE migration.ess_initial_passwords;

COMMIT;

-- summary
SELECT 'auth.users (kept)'      AS metric, count(*) FROM auth.users UNION ALL
SELECT 'auth.user_roles (kept)',          count(*) FROM auth.user_roles UNION ALL
SELECT 'employees',                       count(*) FROM employee.employees UNION ALL
SELECT 'attendance_devices',              count(*) FROM attendance.attendance_devices UNION ALL
SELECT 'attendance_records',              count(*) FROM attendance.attendance_records UNION ALL
SELECT 'id_map',                          count(*) FROM migration.id_map;
