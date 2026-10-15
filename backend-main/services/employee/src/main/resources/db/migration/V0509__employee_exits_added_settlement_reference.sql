-- ---------------------------------------------------------------------
-- reference column for settlement reference.
-- This is used to store the reference of employee in legacy system.
-- ---------------------------------------------------------------------

ALTER TABLE employee_exits

    ADD COLUMN settlement_id BIGINT;
