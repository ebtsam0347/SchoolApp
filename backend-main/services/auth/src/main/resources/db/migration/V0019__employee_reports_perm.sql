-- employee.reports.run permission for the employee-service report endpoints
-- (employee directory CSV is the first; same permission gates every standard
-- employee report). Granted to ADMIN, HR, and MANAGER. Managers see only
-- their own team — the filter is enforced server-side in the report query,
-- per docs/reports.md §6.

insert into permissions (code, name, created_by, updated_by) values
    ('employee.reports.run', 'Run employee-service standard reports', 'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'employee.reports.run'
where r.code in ('ADMIN', 'HR', 'MANAGER');
