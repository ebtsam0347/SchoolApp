--------------------------------------------------------------------
-- "Excessive leave" rework: attendance-service now returns exactly
-- two leave buckets for a termination settlement — Annual (kept
-- separate, the only encashable type) and Medical(M1) + Casual
-- (combined). employee_final_settlement_leave_details stores one row
-- per bucket (leave_code = 'ANNUAL' or 'MEDICAL_CASUAL'); its
-- recoverable_days/recovery_amount columns are renamed to the
-- explicit excessive_days/excessive_amount naming HR actually uses.
--
-- The settlement header gets the split rollup (annual vs combined
-- medical+casual) so the API doesn't need to re-derive it from the
-- child rows on every read.
--------------------------------------------------------------------

ALTER TABLE employee_final_settlement_leave_details
    RENAME COLUMN recoverable_days TO excessive_days;

ALTER TABLE employee_final_settlement_leave_details
    RENAME COLUMN recovery_amount TO excessive_amount;

ALTER TABLE employee_final_settlements
    ADD COLUMN annual_excessive_leave_days           NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN annual_excessive_leave_amount         NUMERIC(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN medical_casual_excessive_leave_days   NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN medical_casual_excessive_leave_amount NUMERIC(18, 2) NOT NULL DEFAULT 0;
