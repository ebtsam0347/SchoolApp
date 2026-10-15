CREATE TABLE employee_final_settlement_bonus_details
(
    id                 BIGSERIAL PRIMARY KEY,

    settlement_id      BIGINT         NOT NULL
        REFERENCES employee_final_settlements (id)
            ON DELETE CASCADE,

    bonus_event_id     BIGINT         NOT NULL,

    event_name         VARCHAR(200)   NOT NULL,

    event_type         VARCHAR(50)    NOT NULL,

    reference_date     DATE,

    eligible_amount    NUMERIC(18, 2) NOT NULL DEFAULT 0,

    paid_amount        NUMERIC(18, 2) NOT NULL DEFAULT 0,

    outstanding_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,

    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_final_settlement_bonus_settlement
    ON employee_final_settlement_bonus_details (settlement_id);

CREATE INDEX idx_final_settlement_bonus_event
    ON employee_final_settlement_bonus_details (bonus_event_id);