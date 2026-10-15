-- Off-Cycle Payments — Accounts payment permission.
--
-- The off-cycle lifecycle splits into two authorities (payroll V0056):
--
--   HR       creates (= authorizes), edits and cancels while HR_APPROVED
--            -> already covered by `payroll.write` (HR, ADMIN)
--   ACCOUNTS executes the payment and flips HR_APPROVED -> PAID
--            -> `payroll.offcycle.accounts`, seeded here
--
-- `payroll.write` deliberately does NOT gate the pay action: ACCOUNTS does
-- not hold it (V0026 keeps the persona off payroll's write surface), and
-- gating /pay on it would hand HR both halves of the separation of duty.
-- Same split as `payroll.advance.accounts` (V0027) and
-- `travel.settlement.paid` (V0035).
--
-- ADMIN is granted explicitly: its payroll grants are built per-migration
-- rather than from a `like 'payroll.%'` wildcard, so a new payroll
-- permission is invisible to ADMIN until a migration inserts the row.

insert into permissions (code, name, created_by, updated_by) values
    ('payroll.offcycle.accounts',
     'Pay an HR-approved off-cycle payment (Accounts)',
     'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'payroll.offcycle.accounts'
where r.code in ('ACCOUNTS', 'ADMIN');
