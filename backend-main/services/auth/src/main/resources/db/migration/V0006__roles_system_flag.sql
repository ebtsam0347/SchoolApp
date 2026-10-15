-- Mark Flyway-seeded roles as `system` so the runtime role write APIs
-- (POST/PATCH/DELETE /admin/roles) refuse to delete them. Names and
-- descriptions remain editable; permission attachments do too.

alter table roles
    add column system boolean not null default false;

update roles set system = true where code in ('EMPLOYEE', 'MANAGER', 'HR', 'ADMIN');

-- New custom roles (created via POST /admin/roles) get system = false
-- by default — the column default handles that case.

-- Auth permission for write APIs. ADMIN gets it; HR does not (HR can
-- read the catalog and assign roles to users, not edit role definitions).
insert into permissions (code, name, created_by, updated_by) values
    ('auth.roles.write', 'Create / update / delete roles', 'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'auth.roles.write'
where r.code = 'ADMIN';
