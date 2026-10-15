-- Split device administration off attendance.admin, and stop HR from
-- carrying floor-clerk perms by default.
--
-- Before this migration:
--   * attendance.admin gates BOTH HR's lookup catalogs (shifts, holidays,
--     leave-types, period locks) AND device CRUD on
--     AttendanceDeviceController. That conflates HR ownership with IT ops.
--   * HR carries attendance.operator.{punch,punch.amend,shift-override},
--     so an HR user's sidebar shows floor-operator menus they don't
--     actually run day-to-day.
--
-- After:
--   * New attendance.devices.admin permission gates device CRUD.
--   * New IT_OPS role holds attendance.devices.admin (read-only for
--     attendance otherwise — they don't approve, don't punch, don't
--     touch the catalog).
--   * ADMIN gets attendance.devices.admin too (superset).
--   * HR loses the three attendance.operator.* perms. An HR user who
--     also needs to operate punches gets ATTENDANCE_OPERATOR assigned
--     as a second role.
--
-- Controllers re-gated in the same change:
--   * AttendanceDeviceController: every endpoint previously on
--     attendance.admin OR attendance.read moves to attendance.devices.admin.
--     attendance.poller stays as-is (poller-targets is the service account).

insert into permissions (code, name, created_by, updated_by) values
    ('attendance.devices.admin',
     'Manage biometric / kiosk devices (CRUD, comm password, sync tasks, snapshots, HWM)',
     'system', 'system');

insert into roles (code, name, description, system, created_by, updated_by) values
    ('IT_OPS',
     'IT Operations',
     'Owns biometric / kiosk device configuration. Adds / edits / deactivates devices, ' ||
     'manages ZK comm passwords, retries sync tasks, requests snapshots. Does not approve ' ||
     'requests, run payroll, or manage HR catalogs.',
     true, 'system', 'system');

-- Grant attendance.devices.admin to IT_OPS and ADMIN.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'attendance.devices.admin'
where r.code in ('IT_OPS', 'ADMIN');

-- IT_OPS also needs attendance.read so the device list / get endpoints
-- (which read shared attendance state on the way) work, and so future
-- read-only diagnostic views over punches stay accessible without
-- another grant.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'attendance.read'
where r.code = 'IT_OPS';

-- Revoke attendance.operator.* from HR. Existing operator users keep
-- access by being granted ATTENDANCE_OPERATOR explicitly (additive role).
delete from role_permissions
where role_id = (select id from roles where code = 'HR')
  and permission_id in (
      select id from permissions
      where code in (
          'attendance.operator.punch',
          'attendance.operator.punch.amend',
          'attendance.operator.shift-override'));
