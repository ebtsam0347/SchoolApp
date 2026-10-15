-- Grant notification-service permissions to existing roles. Permissions
-- are centrally registered here so the JWT issued by auth carries them
-- on the same `permissions` claim that notification-service's
-- @RequirePermission aspect checks. (Seed migrations live in the owning
-- service even when the permission name is namespaced to another
-- service — same pattern as the other V000x perm migrations.)

insert into permissions (code, name, created_by, updated_by) values
    ('notification.template.read',  'Read notification rendering templates',         'system', 'system'),
    ('notification.template.write', 'Create / update notification templates',        'system', 'system'),
    ('notification.announce',       'Broadcast a notification to a user-id list',    'system', 'system');

-- ADMIN gets everything.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
cross join permissions p
where r.code = 'ADMIN' and p.code like 'notification.%';

-- HR can read templates and broadcast announcements (no template edits;
-- copy/branding stays with admins to keep the comms voice consistent).
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('notification.template.read', 'notification.announce')
where r.code = 'HR';

-- Per-user notification reads + preference toggles in notification-service
-- are gated on isAuthenticated() + caller == owner, not on a
-- notification.* permission.
