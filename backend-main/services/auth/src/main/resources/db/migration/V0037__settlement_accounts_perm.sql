-- Employee Final Settlement — Accounts payment permission.
--
-- The settlement lifecycle splits into the same two authorities as
-- off-cycle payments (V0036):
--
--   HR       creates (= terminates + closes leave/bonus/advances),
--            approves, rejects and cancels
--            -> already covered by `payroll.write` (HR, ADMIN)
--   ACCOUNTS confirms the payment and flips HR_APPROVED -> PAID
--            -> `payroll.settlement.accounts`, seeded here
--
-- Before this, POST /settlement/employees/{id}/pay was gated on
-- `payroll.write` — the same permission as hr-approval-confirmation. That
-- gave HR both halves of the approval/pay split and 403'd ACCOUNTS, the one
-- role meant to use it (V0026 keeps the persona off payroll's write
-- surface). Same split as `payroll.advance.accounts` (V0027),
-- `payroll.offcycle.accounts` (V0036) and `travel.settlement.paid` (V0035).
--
-- ADMIN is granted explicitly: its payroll grants are built per-migration
-- rather than from a `like 'payroll.%'` wildcard, so a new payroll
-- permission is invisible to ADMIN until a migration inserts the row.

insert into permissions (code, name, created_by, updated_by) values
    ('payroll.settlement.accounts',
     'Pay an HR-approved employee final settlement (Accounts)',
     'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'payroll.settlement.accounts'
where r.code in ('ACCOUNTS', 'ADMIN');
