ALTER TABLE off_cycle_payments
    ALTER COLUMN reason DROP NOT NULL;

ALTER TABLE off_cycle_payments
    ALTER COLUMN payment_mode DROP NOT NULL;

ALTER TABLE off_cycle_payments
    DROP CONSTRAINT chk_ocp_category;
