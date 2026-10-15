-- RFD advance is keyed by transaction_date alone:
--   * a payroll run deducts every row dated in its period month, then rounds
--     a cash net up to the next 500 note and inserts the top-up dated the
--     payslip's created date ("From Salary <Month Year>");
--   * run reset deletes the "From Salary" rows dated each payslip's created
--     date;
--   * final settlement deducts the rows dated in the last-working-day month;
--   * bonus RFD is entered by hand (bonus_transaction.rfd_advance_added plus
--     an rfd_advance row).
-- So the recovered / final_settlement_id bookkeeping from V0055 goes.

-- Existing rows are not needed (business decision, 2026-10-06).
delete from rfd_advance;

alter table rfd_advance
    drop column recovered,
    drop column final_settlement_id;

create index idx_rfd_advance_employee_date on rfd_advance (employee_id, transaction_date);
