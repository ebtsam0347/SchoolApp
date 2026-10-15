-- attendance.reports.run + leaves.reports.run permissions for the
-- attendance-service report endpoints (attendance, leaves, OT,
-- regularization, shifts, etc. — same permission gates every standard
-- report under each namespace). Granted to ADMIN, HR, MANAGER. Managers
-- see only their own reportees — that filter is enforced server-side
-- per docs/reports.md §6.

insert into permissions (code, name, created_by, updated_by) values
    ('attendance.reports.run', 'Run attendance-service standard reports', 'system', 'system'),
    ('leaves.reports.run',     'Run leave-service standard reports',      'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('attendance.reports.run', 'leaves.reports.run')
where r.code in ('ADMIN', 'HR', 'MANAGER');
