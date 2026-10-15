-- ---------------------------------------------------------------------
-- reference column for legacy fields added in employees table.
-- This is used to store the reference of employee in legacy system.
-- ---------------------------------------------------------------------

ALTER TABLE employees
    RENAME COLUMN referenceBy TO reference_by;
