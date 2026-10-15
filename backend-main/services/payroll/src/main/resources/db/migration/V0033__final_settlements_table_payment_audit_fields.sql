-- ============================================================
-- Add payment audit columns to employee_final_settlements
-- ============================================================

ALTER TABLE employee_final_settlements
    ADD COLUMN paid_by BIGINT;

ALTER TABLE employee_final_settlements
    ADD COLUMN paid_at TIMESTAMP;