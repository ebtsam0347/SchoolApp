-- attendance-service service-account role. The nightly-close job inside
-- attendance-service calls employee-service for the active-employee roster;
-- without an SA identity the call lands as an unauthenticated request and
-- HeaderIdentityFilter rejects it. Tight scope: just employee.read, mirrors
-- the attendance-poller pattern from V0016.
--
-- Bootstrap (UAT / Prod, one-time):
--   1. POST /admin/users  { username:"attendance-svc", fullName:"Attendance Service Account",
--                           roleCodes:["ATTENDANCE_SVC"], status:"ACTIVE", employeeLinked:false,
--                           password:"<random>" }
--   2. POST /admin/users/{id}/service-tokens  { ttlDays: 365 }
--   3. Seal the returned token into the attendance Secret as
--      HRMS_ATTENDANCE_SERVICE_TOKEN.

insert into roles (code, name, system, created_by, updated_by) values
    ('ATTENDANCE_SVC', 'attendance-service service account', true, 'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
cross join permissions p
where r.code = 'ATTENDANCE_SVC' and p.code = 'employee.read';
