-- Factory tour permissions.
-- employee.tours.write                 HOD raises the request (MANAGER + ADMIN)
-- employee.tours.local.approve         Deputy Director approves LOCAL tours
-- employee.tours.international.approve D1 Director approves INTERNATIONAL tours

insert into permissions (code, name, created_by, updated_by) values
    ('employee.tours.write',                 'Create and submit factory tour requests',          'system', 'system'),
    ('employee.tours.local.approve',         'Approve local factory tour requests (DD)',         'system', 'system'),
    ('employee.tours.international.approve', 'Approve international factory tour requests (D1)', 'system', 'system');

-- ADMIN gets all three.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in (
    'employee.tours.write',
    'employee.tours.local.approve',
    'employee.tours.international.approve'
)
where r.code = 'ADMIN';

-- MANAGER role = HODs — can raise requests only.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'employee.tours.write'
where r.code = 'MANAGER';
