-- attendance-poller service-account permission. The poller hits
-- attendance-service surfaces gated by @RequirePermission("attendance.poller")
-- — without this row the perm doesn't exist in auth and the JWT can't
-- carry it. POLLER role isolates the poller's privilege from HR/ADMIN
-- so the service-account JWT is a tight scope (just punches + heartbeat
-- + sync-tasks + templates), not a backdoor.

insert into permissions (code, name, created_by, updated_by) values
    ('attendance.poller', 'attendance-poller sidecar — ingest punches, drain sync queue, push templates', 'system', 'system');

-- Dedicated role for the poller's service account.
insert into roles (code, name, system, created_by, updated_by) values
    ('POLLER', 'attendance-poller service account', true, 'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
cross join permissions p
where r.code = 'POLLER' and p.code = 'attendance.poller';

-- ADMIN gets every attendance permission by V0007 convention.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
cross join permissions p
where r.code = 'ADMIN' and p.code = 'attendance.poller';
