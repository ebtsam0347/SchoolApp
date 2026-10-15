-- Human-readable codes for approval requests (leave / overtime /
-- regularization). Format `<PREFIX>-<YYYY>-<NNNN>` (e.g. `LR-2026-0042`),
-- allocated via the per-year sequence pattern documented in CLAUDE.md.
-- The same allocator services every request type by keying on
-- (request_type, year); future request types in this schema reuse the
-- table without a migration.

create table request_code_sequence (
    request_type varchar(24) not null,
    year         int         not null,
    last_value   int         not null default 0,
    primary key (request_type, year)
);

alter table leave_requests          add column request_code varchar(20);
alter table overtime_requests       add column request_code varchar(20);
alter table regularization_requests add column request_code varchar(20);

-- Backfill any rows that already exist in dev. Order by id so codes are
-- monotonic; year is the current calendar year (Asia/Karachi). The
-- allocator below picks up from the high-water mark.
update leave_requests          set request_code = 'LR-'  || extract(year from now() at time zone 'Asia/Karachi')::int || '-' || lpad(rn::text, 4, '0')
    from (select id, row_number() over (order by id) as rn from leave_requests) s
    where leave_requests.id = s.id;

update overtime_requests       set request_code = 'OT-'  || extract(year from now() at time zone 'Asia/Karachi')::int || '-' || lpad(rn::text, 4, '0')
    from (select id, row_number() over (order by id) as rn from overtime_requests) s
    where overtime_requests.id = s.id;

update regularization_requests set request_code = 'REG-' || extract(year from now() at time zone 'Asia/Karachi')::int || '-' || lpad(rn::text, 4, '0')
    from (select id, row_number() over (order by id) as rn from regularization_requests) s
    where regularization_requests.id = s.id;

-- Seed the sequence with the high-water mark per type so the next
-- allocation continues, instead of colliding with backfilled codes.
insert into request_code_sequence (request_type, year, last_value)
select 'LR',  extract(year from now() at time zone 'Asia/Karachi')::int, count(*) from leave_requests          having count(*) > 0
union all
select 'OT',  extract(year from now() at time zone 'Asia/Karachi')::int, count(*) from overtime_requests       having count(*) > 0
union all
select 'REG', extract(year from now() at time zone 'Asia/Karachi')::int, count(*) from regularization_requests having count(*) > 0;

alter table leave_requests          alter column request_code set not null;
alter table overtime_requests       alter column request_code set not null;
alter table regularization_requests alter column request_code set not null;

alter table leave_requests          add constraint leave_requests_code_uk          unique (request_code);
alter table overtime_requests       add constraint overtime_requests_code_uk       unique (request_code);
alter table regularization_requests add constraint regularization_requests_code_uk unique (request_code);
