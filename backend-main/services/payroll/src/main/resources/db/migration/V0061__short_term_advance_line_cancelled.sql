-- A short-term advance line gets its own CANCELLED status when its batch is
-- cancelled. Previously cancel wrote REJECTED ("batch cancelled"), which
-- conflated "an approver said no" with "the requester withdrew".

alter table short_term_advances drop constraint chk_sta_status;
alter table short_term_advances add constraint chk_sta_status check (status in (
    'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));

update short_term_advances l
   set status = 'CANCELLED'
  from short_term_advance_batches b
 where b.id = l.batch_id
   and b.status = 'CANCELLED'
   and l.status = 'REJECTED';
