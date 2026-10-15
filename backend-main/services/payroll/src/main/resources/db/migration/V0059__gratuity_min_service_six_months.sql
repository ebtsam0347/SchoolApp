-- Gratuity minimum service: 12 → 6 months, matching the legacy HRMS, which
-- pays one year's gratuity once service passes six months. Applied to every
-- row, historical ones included — legacy never used 12, so a back-dated
-- asOf must not either. The column default moves with it so a config row
-- created without the field doesn't silently revert to 12.
update payroll_config set gratuity_min_service_months = 6
 where gratuity_min_service_months = 12;

alter table payroll_config alter column gratuity_min_service_months set default 6;
