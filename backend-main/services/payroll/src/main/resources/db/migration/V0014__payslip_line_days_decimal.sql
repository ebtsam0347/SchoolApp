-- §7.payroll review B4 — `days_present` was rounded UP for odd half-day
-- counts (3 half-days resolved to 2 instead of 1.5), over-paying any
-- PER_DAY allowance for employees with an odd number of half-day
-- attendance rows. Switching daysPresent to a BigDecimal means
-- `payslip_lines.days` (the per-day-component "days" indicator the FE
-- displays + uses for the rate × days breakdown line) needs to hold the
-- fractional value too. No legacy data risk: the column was non-zero
-- only on PER_DAY lines, which always carried whole-day counts pre-fix.
alter table payslip_lines alter column days type numeric(5, 2)
    using (case when days is null then null else days::numeric(5, 2) end);
