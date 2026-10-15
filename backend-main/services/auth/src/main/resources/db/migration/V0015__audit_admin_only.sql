-- Doctrine update: `audit.read` is ADMIN-only.
--
-- Original V0004 granted `audit.read` to HR on the assumption HR would use
-- the audit timeline as the canonical "what happened to this employee"
-- view. That view actually lives in employee-service: HR reads
-- `/employees/{id}/history` and `/employees/{id}/contracts` (both gated on
-- `employee.read`, which HR already has). The audit log is the operator
-- surface — "who changed what, when, with which correlation id" — and
-- carries cross-tenant detail HR has no business with.
--
-- Effect on existing flows:
--   - HR's user-detail screen no longer renders login history (the auth
--     `/admin/users/{id}/login-history` endpoint also tightens to
--     `audit.read` in the auth controller). ADMIN keeps it.
--   - HR's employee-detail screen continues to render the timeline via
--     employee-service's history endpoints — no change.

delete from role_permissions
 where (role_id, permission_id) in (
    select r.id, p.id
      from roles r
      join permissions p on p.code = 'audit.read'
     where r.code = 'HR'
);
