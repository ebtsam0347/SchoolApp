-- ---------------------------------------------------------------------
-- Factory code on overtime requests. The applicant picks which factory /
-- unit the overtime is worked for when filing (self, on-behalf or bulk).
-- Stored as the literal code. Left nullable: requests filed before this
-- column existed have no factory, and there's nothing to backfill from.
-- New requests always carry one (the API requires it).
-- ---------------------------------------------------------------------

alter table overtime_requests add column factory_code varchar(16);

alter table overtime_requests
    add constraint ck_overtime_requests_factory_code
    check (factory_code in ('B34002', 'B34003', 'B34004', 'B34005', 'B34006', 'B34007', 'Administrator'));
