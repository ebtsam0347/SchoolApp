CREATE TABLE employee_final_settlement_overtime
(
    id BIGSERIAL PRIMARY KEY,

    -------------------------------------------------------------------
    -- Parent Settlement
    -------------------------------------------------------------------
    settlement_id BIGINT NOT NULL
        REFERENCES employee_final_settlements(id)
            ON DELETE CASCADE,

    -------------------------------------------------------------------
    -- Employee Reference (snapshot only, not the join key)
    -------------------------------------------------------------------
    employee_id BIGINT NOT NULL,

    -------------------------------------------------------------------
    -- Overtime Classification (Normal Overtime only)
    -------------------------------------------------------------------
    overtime_type VARCHAR(30) NOT NULL DEFAULT 'NORMAL',

    -------------------------------------------------------------------
    -- Normal OT Snapshot
    -------------------------------------------------------------------
    approved_minutes INTEGER NOT NULL DEFAULT 0,

    approved_amount NUMERIC(18,2) NOT NULL DEFAULT 0,

    pending_minutes INTEGER NOT NULL DEFAULT 0,

    pending_amount NUMERIC(18,2) NOT NULL DEFAULT 0,

    unapplied_minutes INTEGER NOT NULL DEFAULT 0,

    unapplied_amount NUMERIC(18,2) NOT NULL DEFAULT 0,

    total_minutes INTEGER NOT NULL DEFAULT 0,

    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0,

    -------------------------------------------------------------------
    -- Calculation Input Snapshot (auditability only)
    -------------------------------------------------------------------
    monthly_basic_salary NUMERIC(18,2) NOT NULL DEFAULT 0,

    per_day_salary NUMERIC(18,2) NOT NULL DEFAULT 0,

    per_hour_salary NUMERIC(18,2) NOT NULL DEFAULT 0,

    overtime_multiplier NUMERIC(5,2) NOT NULL DEFAULT 2.00,

    -------------------------------------------------------------------
    -- Audit
    -------------------------------------------------------------------
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_final_settlement_overtime_settlement
    ON employee_final_settlement_overtime(settlement_id);

CREATE INDEX idx_final_settlement_overtime_employee
    ON employee_final_settlement_overtime(employee_id);
