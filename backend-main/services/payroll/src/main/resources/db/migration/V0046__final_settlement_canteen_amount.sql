-- Grade-conditional canteen deduction for the final-settlement period,
-- computed the same way the regular payroll run computes it. Previously
-- final settlements had no canteen line at all — a terminating
-- employee's canteen usage in their partial final month went unbilled.
ALTER TABLE employee_final_settlements
    ADD COLUMN canteen_amount NUMERIC(18,2) NOT NULL DEFAULT 0;
