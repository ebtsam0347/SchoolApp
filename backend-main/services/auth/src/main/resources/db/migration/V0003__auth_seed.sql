-- Seed roles + auth-owned permissions. The initial admin user is
-- bootstrapped at runtime by AdminBootstrap (so we don't ship a known
-- bcrypt hash in the migration).
--
-- Other services' permissions live in their own seed migrations.

insert into roles (code, name, description, created_by, updated_by) values
    ('EMPLOYEE', 'Employee', 'Baseline role granted to every employee-linked user.', 'system', 'system'),
    ('MANAGER',  'Manager',  'Approves time-off and reviews team attendance.',         'system', 'system'),
    ('HR',       'HR',       'HR operations: employees, payroll inputs, ESS access.',  'system', 'system'),
    ('ADMIN',    'Admin',    'Full system administration.',                            'system', 'system');

insert into permissions (code, name, created_by, updated_by) values
    ('auth.users.read',    'Read users',                       'system', 'system'),
    ('auth.users.write',   'Create / update users',            'system', 'system'),
    ('auth.users.reset',   'Reset user passwords',             'system', 'system'),
    ('auth.roles.read',    'Read roles and role assignments',  'system', 'system'),
    ('auth.sessions.admin','Admin view + revoke any session',  'system', 'system');

-- ADMIN gets every auth permission.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
cross join permissions p
where r.code = 'ADMIN' and p.code like 'auth.%';

-- HR can read users + sessions + reset passwords (not change permissions/roles).
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('auth.users.read', 'auth.users.reset', 'auth.sessions.admin')
where r.code = 'HR';
