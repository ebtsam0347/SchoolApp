-- Travel Settlement — split the shared "Finance/HR" settlement pool into
-- two distinct actors (employee-service V0619 re-points the
-- TRAVEL_SETTLEMENT chain to a 2-stage HR -> Accounts hierarchy):
--
--   travel.settlement.approve   HR Settlement Verification (stage 1) -> HR, ADMIN
--   travel.settlement.paid      Accounts Payment Confirmation (stage 2) -> ACCOUNTS, ADMIN
--
-- V0032 granted travel.settlement.approve to both HR and ACCOUNTS (a single
-- shared stage at the time). ACCOUNTS no longer verifies the settlement —
-- it now only confirms payment once HR has — so the ACCOUNTS grant of
-- travel.settlement.approve is revoked here and replaced with the new
-- travel.settlement.paid permission. HR's travel.settlement.approve grant
-- is untouched.

insert into permissions (code, name, created_by, updated_by) values
    ('travel.settlement.paid', 'Verify and confirm payment of an HR-approved travel settlement (Accounts)', 'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'travel.settlement.paid'
where r.code in ('ACCOUNTS', 'ADMIN');

delete from role_permissions
where permission_id = (select id from permissions where code = 'travel.settlement.approve')
  and role_id = (select id from roles where code = 'ACCOUNTS');
