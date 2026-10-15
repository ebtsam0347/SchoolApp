CREATE TABLE employee_final_settlement_deductions
(
    id             BIGSERIAL PRIMARY KEY,

    settlement_id  BIGINT         NOT NULL,

    --------------------------------------------------------------------
    -- Deduction Snapshot
    --------------------------------------------------------------------
    deduction_code VARCHAR(50)    NOT NULL,

    deduction_name VARCHAR(200),

    amount         NUMERIC(18, 2) NOT NULL DEFAULT 0,

    --------------------------------------------------------------------
    -- Audit
    --------------------------------------------------------------------
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    --------------------------------------------------------------------
    -- Foreign Key
    --------------------------------------------------------------------
    CONSTRAINT fk_employee_final_settlement_deduction
        FOREIGN KEY (settlement_id)
            REFERENCES employee_final_settlements (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_employee_final_settlement_deduction_settlement
    ON employee_final_settlement_deductions (settlement_id);

CREATE INDEX idx_employee_final_settlement_deduction_code
    ON employee_final_settlement_deductions (deduction_code);