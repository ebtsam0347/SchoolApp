-- Narrow MANAGER's employee-service access. V0005 granted MANAGER
-- `employee.read`, which unlocks every read surface in employee-service
-- (full directory, org tree, any employee's record, history). A manager's
-- legitimate surfaces are auth-only (/employees/me, /me/team, /me/manager,
-- /picker, /by-ids, /delegation-candidates) or manager-scoped server-side
-- (/reports/employees/directory via ReportsController.resolveManagerScope).
-- Stripping employee.read keeps those working and stops org-wide reads.
--
-- MANAGER keeps employee.reports.run and employee.analytics.read; those
-- carry their own scoping and aren't affected.

delete from role_permissions
where role_id     = (select id from roles where code = 'MANAGER')
  and permission_id = (select id from permissions where code = 'employee.read');
