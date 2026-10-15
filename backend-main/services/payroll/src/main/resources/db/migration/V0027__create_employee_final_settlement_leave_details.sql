CREATE TABLE employee_final_settlement_leave_details
(
    id                BIGSERIAL PRIMARY KEY,

    settlement_id     BIGINT         NOT NULL,

    --------------------------------------------------------------------
    -- Original Leave Type Reference
    --------------------------------------------------------------------
    leave_type_id     BIGINT,

    --------------------------------------------------------------------
    -- Snapshot
    --------------------------------------------------------------------
    leave_code        VARCHAR(30)    NOT NULL,

    leave_name        VARCHAR(100)   NOT NULL,

    cycle_start       DATE,

    cycle_end         DATE,

    allocated         NUMERIC(10, 2) NOT NULL DEFAULT 0,

    accrued           NUMERIC(10, 2) NOT NULL DEFAULT 0,

    used              NUMERIC(10, 2) NOT NULL DEFAULT 0,

    pending           NUMERIC(10, 2) NOT NULL DEFAULT 0,

    payable_days      NUMERIC(10, 2) NOT NULL DEFAULT 0,

    recoverable_days  NUMERIC(10, 2) NOT NULL DEFAULT 0,

    encashment_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,

    recovery_amount   NUMERIC(18, 2) NOT NULL DEFAULT 0,

    --------------------------------------------------------------------
    -- Audit
    --------------------------------------------------------------------
    created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    --------------------------------------------------------------------
    -- FK
    --------------------------------------------------------------------
    CONSTRAINT fk_employee_final_settlement_leave
        FOREIGN KEY (settlement_id)
            REFERENCES employee_final_settlements (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_employee_final_settlement_leave_settlement
    ON employee_final_settlement_leave_details (settlement_id);

CREATE INDEX idx_employee_final_settlement_leave_type
    ON employee_final_settlement_leave_details (leave_type_id);