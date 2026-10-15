-- Sick Leave's code is renamed from the legacy 'M1' to 'MEDICAL_M1' to
-- disambiguate from other "M" short-leave codes. V0007 already executed
-- in every environment (it introduced 'M1'), so we rename forward here
-- instead of editing that migration.
update leave_types set code = 'MEDICAL_M1'
 where code = 'M1';
