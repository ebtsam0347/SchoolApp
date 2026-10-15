-- Mark service-account roles (POLLER, ATTENDANCE_SVC) so the role
-- catalog used by the user-assignment dropdown can hide them. They
-- exist only to back machine identities (attendance-poller sidecar,
-- attendance-service nightly-close); HR should never see them as an
-- option when granting roles to a person. The role admin screen still
-- pulls them via /admin/roles?includeServiceAccounts=true.

alter table roles
    add column service_account boolean not null default false;

update roles set service_account = true where code in ('POLLER', 'ATTENDANCE_SVC');
