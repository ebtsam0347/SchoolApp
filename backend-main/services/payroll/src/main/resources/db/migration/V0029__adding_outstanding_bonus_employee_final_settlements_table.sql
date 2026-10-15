ALTER TABLE employee_final_settlements
    ADD COLUMN outstanding_bonus numeric(18,2) NOT NULL DEFAULT 0;