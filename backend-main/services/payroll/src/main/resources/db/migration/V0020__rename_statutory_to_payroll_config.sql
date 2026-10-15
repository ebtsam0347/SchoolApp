-- Rename: the table holds more than statutory rates now (canteen meal
-- price, gratuity thresholds, etc.). Calling it `statutory_config` is
-- misleading; everything HR maintains here is payroll configuration.

alter table statutory_config rename to payroll_config;
alter index  idx_statutory_effective rename to idx_payroll_config_effective;
alter table  payroll_config rename constraint chk_stat_dates to chk_payroll_config_dates;
