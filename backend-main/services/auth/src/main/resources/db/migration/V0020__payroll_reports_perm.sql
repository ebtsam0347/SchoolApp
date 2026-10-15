-- payroll.reports.run permission for payroll-service report endpoints
-- (payslip PDF, pay register, EOBI/PESSI returns, tax deducted, advances
-- outstanding, etc. — same permission gates every standard payroll
-- report). Granted to ADMIN and HR. Per V0008__payroll_perms.sql, only
-- HR / ADMIN ever get payroll.* permissions; payroll reports follow the
-- same scope.

insert into permissions (code, name, created_by, updated_by) values
    ('payroll.reports.run', 'Run payroll-service standard reports', 'system', 'system');

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
join permissions p on p.code = 'payroll.reports.run'
where r.code in ('ADMIN', 'HR');
