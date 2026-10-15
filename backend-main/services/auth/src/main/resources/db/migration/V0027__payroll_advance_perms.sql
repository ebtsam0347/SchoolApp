-- Payroll advance approval permissions.
--
-- DRIFT FIX: payroll.advance.hr and payroll.advance.accounts were enforced
-- on the permanent / short-term advance approval endpoints (@RequirePermission
-- in PermanentAdvanceController + ShortTermAdvanceController) but were never
-- seeded into the permissions table. No role — not even ADMIN, whose grants
-- are built per-migration via `like 'payroll.%'` rather than a wildcard —
-- could ever hold them, so every advance HR-approve / accounts-approve /
-- reject / disburse / deduction endpoint returned 403 for everyone.
--
-- Two-stage separation of duty: HR approves PENDING -> HR_APPROVED, Accounts
-- approves HR_APPROVED -> APPROVED (and disburses / records deductions).
-- Either reviewer may reject. Granting:
--   * payroll.advance.hr        -> HR, ADMIN
--   * payroll.advance.accounts  -> ACCOUNTS, ADMIN
-- (ACCOUNTS gains its first write-side action here; its persona note in
-- V0026 calling it strictly read-only is now superseded for advances.)

insert into permissions (code, name, created_by, updated_by) values
    ('payroll.advance.hr',       'HR-approve / reject employee advances',                'system', 'system'),
    ('payroll.advance.accounts', 'Accounts-approve / disburse / reject employee advances','system', 'system');

-- ADMIN gets both.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code in ('payroll.advance.hr', 'payroll.advance.accounts')
where r.code = 'ADMIN';

-- HR holds the HR-approval stage.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'payroll.advance.hr'
where r.code = 'HR';

-- ACCOUNTS holds the accounts-approval / disbursement stage.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'payroll.advance.accounts'
where r.code = 'ACCOUNTS';
