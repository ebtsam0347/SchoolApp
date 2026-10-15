ALTER TABLE annual_leave_transaction
    ALTER COLUMN payment_status DROP DEFAULT;

ALTER TABLE annual_leave_transaction
    ALTER COLUMN lock_status DROP DEFAULT;