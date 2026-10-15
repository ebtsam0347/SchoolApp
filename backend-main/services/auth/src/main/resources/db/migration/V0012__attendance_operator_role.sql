-- Attendance-operator persona. Distinct from HR — a gate / shift-floor
-- clerk who fixes attendance for others but does not run HR ops or
-- approve requests. New permissions:
--   * attendance.operator.punch.amend     — edit / delete an existing punch
--   * attendance.operator.shift-override  — bulk shift overrides
-- The single-punch-add permission (attendance.operator.punch) was seeded
-- in V0007 and stays unchanged.
--
-- Force-finalise of leave / OT / regularization stays HR-only via
-- attendance.approval.override — the operator never touches that.

insert into permissions (code, name, created_by, updated_by) values
    ('attendance.operator.punch.amend',    'Edit or delete an existing punch',           'system', 'system'),
    ('attendance.operator.shift-override', 'Bulk shift overrides for selected employees','system', 'system');

insert into roles (code, name, description, system, created_by, updated_by) values
    ('ATTENDANCE_OPERATOR',
     'Attendance Operator',
     'Floor / gate clerk who records and corrects attendance for others. Adds / amends / deletes punches and applies bulk shift overrides; cannot approve requests.',
     true, 'system', 'system');

-- ATTENDANCE_OPERATOR's permission set: read everything, plus the three
-- operator powers (add / amend / shift-override). Deliberately NO
-- attendance.write (that is self-serve), NO attendance.admin (HR catalog
-- ownership), NO attendance.approval.override.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in (
    'attendance.read',
    'attendance.operator.punch',
    'attendance.operator.punch.amend',
    'attendance.operator.shift-override')
where r.code = 'ATTENDANCE_OPERATOR';

-- HR + ADMIN already had the previous attendance.* set; grant them the
-- two new permissions too so HR keeps amend / shift-override capability.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in (
    'attendance.operator.punch.amend',
    'attendance.operator.shift-override')
where r.code in ('HR', 'ADMIN');
