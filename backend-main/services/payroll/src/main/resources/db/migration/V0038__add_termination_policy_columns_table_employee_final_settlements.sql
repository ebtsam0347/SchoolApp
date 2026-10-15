-- add_termination_policy_columns.sql

ALTER TABLE employee_final_settlements
    ADD COLUMN adjustment_type VARCHAR(50) NOT NULL DEFAULT 'NONE',
    ADD COLUMN notice_pay_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN notice_pay_percentage VARCHAR(50),
    ADD COLUMN forced_deduction_percentage VARCHAR(50),
    ADD COLUMN forced_deduction_amount NUMERIC(18,2) NOT NULL DEFAULT 0;