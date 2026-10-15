-- Attendance-service permissions. Same convention as
-- V0005__employee_perms.sql — the permission rows live in auth so the
-- JWT carries them on the `permissions` claim that attendance-service's
-- @RequirePermission aspect checks.

insert into permissions (code, name, created_by, updated_by) values
    ('attendance.read',           'Read shifts, attendance, leaves, OT, regularizations', 'system', 'system'),
    ('attendance.write',          'Self-serve punch / leave / OT / regularization apply',  'system', 'system'),
    ('attendance.admin',          'HR shift catalog, holidays, balances, force operations','system', 'system'),
    ('attendance.operator.punch', 'Operator-added punches off paper proof',                'system', 'system'),
    ('attendance.overtime.bulk',  'Bulk OT submission for a department',                   'system', 'system'),
    ('attendance.hr.approve',     'Final-stage HR approval on leave / OT / regularization','system', 'system'),
    ('attendance.approval.override', 'Force-finalize a request, delegate on behalf of others', 'system', 'system');

-- ADMIN gets every attendance permission.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
cross join permissions p
where r.code = 'ADMIN' and p.code like 'attendance.%';

-- HR runs day-to-day attendance ops + final-stage approvals + override.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in (
    'attendance.read', 'attendance.write', 'attendance.admin',
    'attendance.operator.punch', 'attendance.overtime.bulk',
    'attendance.hr.approve', 'attendance.approval.override')
where r.code = 'HR';

-- Managers read team data, submit personal requests, and bulk-OT for their dept.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in (
    'attendance.read', 'attendance.write', 'attendance.overtime.bulk')
where r.code = 'MANAGER';

-- Every employee can read own data + self-apply.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('attendance.read', 'attendance.write')
where r.code = 'EMPLOYEE';
