-- Grant chat-service permissions. Per the project pattern, all permissions
-- are registered in auth so the JWT carries them on the same `permissions`
-- claim that downstream services' @RequirePermission aspect checks.
--
-- DM and group access is gated on conversation membership inside chat-service,
-- not on a permission code — every authenticated user can DM peers and create
-- groups by default.

insert into permissions (code, name, created_by, updated_by) values
    ('chat.broadcast.write', 'Post to org-wide announcements channel',  'system', 'system'),
    ('chat.admin.delete',    'Hard-delete any chat message',            'system', 'system'),
    ('chat.admin.read',      'Read any conversation (admin/HR review)', 'system', 'system');

-- ADMIN gets every chat permission.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
cross join permissions p
where r.code = 'ADMIN' and p.code like 'chat.%';

-- HR can post broadcasts and review any conversation. HR cannot
-- hard-delete messages by default (admins can, after audit).
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('chat.broadcast.write', 'chat.admin.read')
where r.code = 'HR';
