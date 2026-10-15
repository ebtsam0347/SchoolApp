-- Canteen role + permissions. The canteen clerk maintains the meal
-- register (attendance_canteen in the attendance schema): views the
-- day's entries, searches a date range / one employee, adds an entry
-- for an active employee, edits and deletes.
--
-- Split four ways rather than a single canteen.admin so the counter
-- operator can be add-only (read + create) while corrections stay with
-- a supervisor (write + delete).

insert into permissions (code, name, created_by, updated_by) values
    ('canteen.read',   'Read canteen (meal) attendance',   'system', 'system'),
    ('canteen.create', 'Add a canteen (meal) entry',       'system', 'system'),
    ('canteen.write',  'Edit a canteen (meal) entry',      'system', 'system'),
    ('canteen.delete', 'Delete a canteen (meal) entry',    'system', 'system');

insert into roles (code, name, description, system, created_by, updated_by) values
    ('CANTEEN',
     'Canteen',
     'Maintains the canteen meal register: records meals for active employees, ' ||
     'edits and deletes entries, views daily and date-range listings.',
     true, 'system', 'system');

-- CANTEEN and ADMIN get the full set. HR / MANAGER / EMPLOYEE
-- deliberately get nothing — CANTEEN is assigned as an additive role.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code like 'canteen.%'
where r.code in ('CANTEEN', 'ADMIN');
