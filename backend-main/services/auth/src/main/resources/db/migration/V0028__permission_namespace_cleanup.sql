-- Permission namespace cleanup. Three drifts in the permission vocabulary:
--
-- 1) leaves.* is a phantom namespace. Leaves live inside attendance-service
--    (no `leaves` service exists), yet leaves.reports.run / leaves.analytics.read
--    invented a top-level namespace. Re-home under attendance.leaves.* so every
--    code is <real-service>.<resource...>.<action>.
--
-- 2) chat.admin.read / chat.admin.delete use "admin" as a fake resource segment;
--    everywhere else the last segment is the action (audit.admin, attendance.admin).
--    Rename to chat.conversations.read / chat.messages.delete to match
--    chat.broadcast.write's resource.action shape.
--
-- 3) Operator punch permissions were split three ways (operator.punch,
--    operator.punch.amend, operator.shift-override) though no role ever held a
--    strict subset — HR, ADMIN and ATTENDANCE_OPERATOR each hold all three.
--    Collapse to a single attendance.operator. Behaviour-preserving at the role
--    level.
--
-- role_permissions reference permissions by id, so the UPDATEs below carry the
-- existing grants forward automatically; the JWT's `permissions` claim picks up
-- the new codes on the next token issuance.

-- (1) leaves.* -> attendance.leaves.*
update permissions set code = 'attendance.leaves.reports.run', updated_at = now(), updated_by = 'system'
 where code = 'leaves.reports.run';
update permissions set code = 'attendance.leaves.analytics.read', updated_at = now(), updated_by = 'system'
 where code = 'leaves.analytics.read';

-- (2) chat.admin.* -> resource.action shape
update permissions set code = 'chat.conversations.read', name = 'Read any conversation (admin / HR review)',
       updated_at = now(), updated_by = 'system'
 where code = 'chat.admin.read';
update permissions set code = 'chat.messages.delete', name = 'Hard-delete any chat message',
       updated_at = now(), updated_by = 'system'
 where code = 'chat.admin.delete';

-- (3) collapse the operator trio into attendance.operator.
insert into permissions (code, name, created_by, updated_by) values
    ('attendance.operator', 'Operator punch + shift corrections for others (add / amend / shift-override)',
     'system', 'system');

-- Grant attendance.operator to every role that currently holds operator.punch
-- (HR, ADMIN, ATTENDANCE_OPERATOR), without duplicating an existing grant.
insert into role_permissions (role_id, permission_id)
select distinct rp.role_id, np.id
from role_permissions rp
join permissions op on op.id = rp.permission_id and op.code = 'attendance.operator.punch'
cross join permissions np
where np.code = 'attendance.operator'
  and not exists (
      select 1 from role_permissions x
       where x.role_id = rp.role_id and x.permission_id = np.id);

-- Drop the three old codes. role_permissions rows cascade (FK on delete cascade).
delete from permissions
 where code in (
    'attendance.operator.punch',
    'attendance.operator.punch.amend',
    'attendance.operator.shift-override');
