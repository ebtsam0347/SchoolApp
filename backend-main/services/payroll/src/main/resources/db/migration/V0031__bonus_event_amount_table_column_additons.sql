ALTER TABLE bonus_event_amounts
    ADD COLUMN paid_amount NUMERIC(15,2) NOT NULL DEFAULT 0;

ALTER TABLE bonus_event_amounts
    ADD COLUMN payment_status VARCHAR(20)
        NOT NULL DEFAULT 'PENDING';

ALTER TABLE bonus_event_amounts
    ADD COLUMN paid_at TIMESTAMP;