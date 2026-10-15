-- Two canteen rates: management pays a higher per-meal price than the
-- supervisor/labour rate. Compute uses the right one per employee based
-- on their grade type. The single `canteen_per_day_rate` added in V0019
-- is retired; existing values (only ever 0 in practice — V0019 default)
-- are copied into the new MANAGEMENT column so any pre-set rate is kept.

alter table payroll_config
    add column canteen_per_day_rate_management numeric(15, 2) not null default 0,
    add column canteen_per_day_rate_other      numeric(15, 2) not null default 0;

update payroll_config
   set canteen_per_day_rate_management = canteen_per_day_rate,
       canteen_per_day_rate_other      = canteen_per_day_rate;

alter table payroll_config
    drop column canteen_per_day_rate;
