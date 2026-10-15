-- Grant audit-service permissions to existing roles. Permissions are
-- centrally registered here so the JWT issued by auth carries them on
-- the same `permissions` claim that audit-service's @RequirePermission
-- aspect checks. (Seed migrations live in the owning service even when
-- the permission name is namespaced to another service.)

insert into permissions (code, name, created_by, updated_by) values
    ('audit.read',  'Read audit log + timelines',         'system', 'system'),
    ('audit.admin', 'Replay topic + view consumer health', 'system', 'system');

-- ADMIN gets both. HR gets read-only.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('audit.read', 'audit.admin')
where r.code = 'ADMIN';

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'audit.read'
where r.code = 'HR';
