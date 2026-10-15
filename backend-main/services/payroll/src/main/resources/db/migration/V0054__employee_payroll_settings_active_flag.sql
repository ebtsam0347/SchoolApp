ALTER TABLE employee_payroll_settings
    ADD COLUMN active boolean NOT NULL DEFAULT true;

-- Backfill: an employee whose salary row is already closed out
-- (closeOnExit ran on a past termination) is already terminated today,
-- not just from here on — exclude them from regular-run selection too,
-- not only employees terminated after this migration.
UPDATE employee_payroll_settings s
SET active = false
WHERE EXISTS (
    SELECT 1 FROM employee_salaries es
    WHERE es.employee_id = s.employee_id
      AND es.effective_to IS NOT NULL
      AND es.effective_to < CURRENT_DATE
);
