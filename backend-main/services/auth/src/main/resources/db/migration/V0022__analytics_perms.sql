-- Analytics permissions for the JSON aggregation endpoints under
-- /analytics/**. Granted to ADMIN, HR, MANAGER. ESS users do not get
-- these — they see their own scorecard via a separate narrower
-- endpoint per docs/reports.md §6.

insert into permissions (code, name, created_by, updated_by) values
    ('employee.analytics.read',   'Read people analytics JSON',     'system', 'system'),
    ('attendance.analytics.read', 'Read attendance analytics JSON', 'system', 'system'),
    ('leaves.analytics.read',     'Read leave analytics JSON',      'system', 'system'),
    ('payroll.analytics.read',    'Read payroll analytics JSON',    'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in (
    'employee.analytics.read',
    'attendance.analytics.read',
    'leaves.analytics.read',
    'payroll.analytics.read')
where r.code in ('ADMIN', 'HR', 'MANAGER');
