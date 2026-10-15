-- Travel Request approval hierarchy — TRANSPORT -> DD/HOD -> HR.
--
-- The employee-service TRAVEL_REQUEST approval chain (employee-service
-- V0613) now has two PERMISSION-stage pools:
--
--   travel.transport.verify   stage 1 — Transport verifies company-vehicle
--                             availability / number and per-leg distance,
--                             and may set the actual (validated) distance.
--   travel.hr.approve         stage 3 — HR gives final approval.
--
-- The DD/HOD stage (stage 2) is a MANAGER-resolver stage against the
-- requester's manager and needs no permission.
--
-- Seeded on the same `permissions` / `role_permissions` tables the JWT
-- `permissions` claim is built from (same pattern as V0032__travel_perm).
-- The pre-existing travel.approve permission is left as-is — it still gates
-- travel-expense review and is intentionally NOT reused for the HR stage.

insert into permissions (code, name, created_by, updated_by) values
    ('travel.transport.verify', 'Verify travel-request transport: company vehicle + per-leg distance (stage 1)', 'system', 'system'),
    ('travel.hr.approve',       'HR final approval of a travel request (stage 3)', 'system', 'system');

-- Transport pool — a dedicated additive role plus ADMIN.
insert into roles (code, name, description, system, created_by, updated_by) values
    ('TRANSPORT',
     'Transport',
     'Verifies the transport stage of travel requests: confirms company-vehicle ' ||
     'availability and number and validates the distance of each travel leg.',
     true, 'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'travel.transport.verify'
where r.code in ('TRANSPORT', 'ADMIN');

-- HR final-approval pool — HR and ADMIN (parity with travel.approve in V0032).
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'travel.hr.approve'
where r.code in ('HR', 'ADMIN');
