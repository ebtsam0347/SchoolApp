-- Travel module permissions.
--
-- The employee-service travel controllers gate every endpoint with
-- @RequirePermission; the approval chains seeded in employee-service
-- V0611 additionally reference travel.approve / travel.advance.approve /
-- travel.settlement.approve as PERMISSION-stage pools. All six are seeded
-- here so the JWT carries them on the same `permissions` claim the
-- employee-service aspect checks (same pattern as V0029__factory_tour_perm).
--
--   travel.read                 read requests / expenses / advances / settlements / policies
--   travel.write                raise + edit + submit + cancel; start/complete a trip
--   travel.approve              HR-approve a travel request; review expenses          -> HR, ADMIN
--   travel.advance.approve      Finance: approve / pay / reconcile advances           -> ACCOUNTS, ADMIN
--   travel.settlement.approve   Finance/HR: approve + pay/recover + settle settlements -> ACCOUNTS, HR, ADMIN
--   travel.admin                travel policy version + grade-limit administration    -> HR, ADMIN

insert into permissions (code, name, created_by, updated_by) values
    ('travel.read',               'Read travel requests, expenses, advances, settlements and policies', 'system', 'system'),
    ('travel.write',              'Raise and manage own travel requests, expenses, advances, settlements', 'system', 'system'),
    ('travel.approve',            'HR-approve travel requests and review travel expenses', 'system', 'system'),
    ('travel.advance.approve',    'Approve, pay and reconcile travel advances (Finance)', 'system', 'system'),
    ('travel.settlement.approve', 'Approve, pay/recover and finalize travel settlements (Finance/HR)', 'system', 'system'),
    ('travel.admin',              'Administer versioned travel policies and grade limits', 'system', 'system');

-- ADMIN gets all six.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in (
    'travel.read', 'travel.write', 'travel.approve',
    'travel.advance.approve', 'travel.settlement.approve', 'travel.admin'
)
where r.code = 'ADMIN';

-- Every employee-linked user can raise and track their own travel.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('travel.read', 'travel.write')
where r.code in ('EMPLOYEE', 'MANAGER', 'HR');

-- HR owns request approval, expense review and policy administration,
-- and shares settlement approval with Finance.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('travel.approve', 'travel.settlement.approve', 'travel.admin')
where r.code = 'HR';

-- ACCOUNTS (Finance persona) owns advance payment and settlement finalization.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('travel.read', 'travel.advance.approve', 'travel.settlement.approve')
where r.code = 'ACCOUNTS';
