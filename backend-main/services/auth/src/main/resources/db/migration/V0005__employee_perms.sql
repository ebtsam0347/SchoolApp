-- Grant employee-service permissions to existing roles. Permissions are
-- centrally registered here so the JWT issued by auth carries them on
-- the same `permissions` claim that employee-service's
-- @RequirePermission aspect checks. (Seed migrations live in the owning
-- service even when the permission name is namespaced to another
-- service — same pattern as V0004__audit_perms.sql.)

insert into permissions (code, name, created_by, updated_by) values
    ('employee.read',  'Read employees, org structure, catalogs, history',     'system', 'system'),
    ('employee.write', 'Create / update employees, catalogs, lifecycle ops',   'system', 'system'),
    ('employee.admin', 'Saga retry, ESS grant/revoke',                         'system', 'system');

-- ADMIN gets everything.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('employee.read', 'employee.write', 'employee.admin')
where r.code = 'ADMIN';

-- HR runs day-to-day employee + catalog operations.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('employee.read', 'employee.write')
where r.code = 'HR';

-- Managers can read their team; no write access (HR owns the record).
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'employee.read'
where r.code = 'MANAGER';

-- HR also needs auth.users.write so the onboarding saga in
-- employee-service can call POST /admin/users to provision ESS.
-- V0003__auth_seed.sql granted HR read-only access on the working
-- assumption that an admin would create users by hand; with the
-- employee-first onboarding flow that policy isn't workable.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'auth.users.write'
where r.code = 'HR'
  and not exists (
      select 1 from role_permissions rp
      where rp.role_id = r.id
        and rp.permission_id = p.id
  );
