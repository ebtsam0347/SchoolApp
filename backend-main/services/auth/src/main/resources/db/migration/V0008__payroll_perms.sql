-- Grant payroll-service permissions to existing roles. Permissions are
-- centrally registered here so the JWT issued by auth carries them on
-- the same `permissions` claim that payroll-service's @RequirePermission
-- aspect checks. (Seed migrations live in the owning service even when
-- the permission name is namespaced to another service — same pattern
-- as V0004__audit_perms.sql / V0005__employee_perms.sql /
-- V0007__attendance_perms.sql.)

insert into permissions (code, name, created_by, updated_by) values
    ('payroll.read',  'Read payroll runs, payslips, salary structure, off-cycle payments', 'system', 'system'),
    ('payroll.write', 'Submit off-cycle payments, capture payroll inputs',                  'system', 'system'),
    ('payroll.admin', 'Run payroll batches, manage catalogs, statutory config, increments', 'system', 'system');

-- ADMIN gets every payroll permission.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
cross join permissions p
where r.code = 'ADMIN' and p.code like 'payroll.%';

-- HR runs payroll end-to-end (per CLAUDE.md: HR enters bonus events,
-- runs payroll batches, manages salary components and statutory config).
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('payroll.read', 'payroll.write', 'payroll.admin')
where r.code = 'HR';

-- Managers and employees do not get payroll perms — payslip self-serve
-- in payroll-service is gated on isAuthenticated() + caller == owner,
-- not on a payroll.* permission.
