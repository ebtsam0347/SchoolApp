-- Grant HR `auth.roles.read`. V0003 only gave it to ADMIN, but V0006's
-- own comment notes HR "can read the catalog and assign roles to users":
-- the admin-user UI calls GET /admin/roles to populate the role dropdown
-- when HR attaches roles via POST /admin/users/{id}/roles. Without this
-- grant, HR can attach roles (auth.users.write) but can't list which
-- ones exist. EMPLOYEE / MANAGER stay out — they don't manage users.

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'auth.roles.read'
where r.code = 'HR'
  and not exists (
      select 1 from role_permissions rp
      where rp.role_id = r.id
        and rp.permission_id = p.id
  );
