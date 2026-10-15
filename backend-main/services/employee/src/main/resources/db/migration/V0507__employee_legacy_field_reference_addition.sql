

-- ---------------------------------------------------------------------
-- reference column for legacy fields added in employees table.
-- This is used to store the reference of employee in legacy system.
-- ---------------------------------------------------------------------

alter table employees
    add column referenceBy varchar(100);
