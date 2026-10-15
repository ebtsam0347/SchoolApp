-- Payroll salary-visibility roles/permissions.
--
-- 1) ACCOUNTS persona — finance reads every employee's calculated salary
--    but never runs payroll or edits inputs. Read-only: payroll.read plus
--    the read-side report / analytics permissions. Deliberately NO
--    payroll.write / payroll.admin (that stays HR / ADMIN).
--
-- 2) payroll.read.team — lets a MANAGER read the payslips of their direct
--    reports (resolved server-side in payroll via /employees/me/team).
--    Scoped, not org-wide: holding it alone reads nothing; payroll also
--    checks the target is one of the caller's reports. HR / ADMIN already
--    hold payroll.read (all employees) so they don't need it.
--
-- (Payroll permissions are seeded in auth so the JWT carries them on the
-- same `permissions` claim payroll's @RequirePermission aspect checks —
-- same pattern as V0008__payroll_perms.sql.)

insert into permissions (code, name, created_by, updated_by) values
    ('payroll.read.team', 'Read payslips of the caller''s direct reports', 'system', 'system');

insert into roles (code, name, description, system, created_by, updated_by) values
    ('ACCOUNTS',
     'Accounts',
     'Finance / accounts persona. Reads every employee''s payslips, payroll runs, '
       || 'salary structure, reports and analytics. Read-only — does not run payroll, '
       || 'capture inputs, or manage catalogs.',
     true, 'system', 'system');

-- ACCOUNTS: read-only payroll surface.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in (
    'payroll.read',
    'payroll.reports.run',
    'payroll.analytics.read')
where r.code = 'ACCOUNTS';

-- MANAGER: team-scoped payslip read.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'payroll.read.team'
where r.code = 'MANAGER';
