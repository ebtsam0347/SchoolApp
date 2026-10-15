CREATE TABLE employee_final_settlement_advance_details
(
    id BIGSERIAL PRIMARY KEY,

    -------------------------------------------------------------------
    -- Parent Settlement
    -------------------------------------------------------------------
    settlement_id BIGINT NOT NULL
        REFERENCES employee_final_settlements(id)
            ON DELETE CASCADE,

    -------------------------------------------------------------------
    -- Permanent Advance Snapshot
    -------------------------------------------------------------------
    advance_id BIGINT NOT NULL,

    request_code VARCHAR(30) NOT NULL,

    receiving_date DATE,

    advance_status VARCHAR(30) NOT NULL,

    description TEXT,

    -------------------------------------------------------------------
    -- Financial Snapshot
    -------------------------------------------------------------------
    original_amount NUMERIC(18,2) NOT NULL DEFAULT 0,

    recovered_amount NUMERIC(18,2) NOT NULL DEFAULT 0,

    outstanding_amount NUMERIC(18,2) NOT NULL DEFAULT 0,

    monthly_deduction NUMERIC(18,2) NOT NULL DEFAULT 0,

    bonus_offset NUMERIC(18,2) NOT NULL DEFAULT 0,

    annual_leave_offset NUMERIC(18,2) NOT NULL DEFAULT 0,

    -------------------------------------------------------------------
    -- Recovery Through Final Settlement
    -------------------------------------------------------------------
    bonus_recovered NUMERIC(18,2) NOT NULL DEFAULT 0,

    leave_recovered NUMERIC(18,2) NOT NULL DEFAULT 0,

    settlement_recovered NUMERIC(18,2) NOT NULL DEFAULT 0,

    -------------------------------------------------------------------
    -- Remarks
    -------------------------------------------------------------------
    remarks TEXT,

    -------------------------------------------------------------------
    -- Audit
    -------------------------------------------------------------------
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    recovered_at TIMESTAMP,
    recovered_by VARCHAR(100)
);

CREATE INDEX idx_final_settlement_advance_settlement
    ON employee_final_settlement_advance_details(settlement_id);

CREATE INDEX idx_final_settlement_advance_advance
    ON employee_final_settlement_advance_details(advance_id);

CREATE INDEX idx_final_settlement_advance_request_code
    ON employee_final_settlement_advance_details(request_code);

CREATE INDEX idx_final_settlement_advance_status
    ON employee_final_settlement_advance_details(advance_status);