-- Canteen meal price (rupees per meal). Per-day rate for the dynamic
-- canteen deduction:
--   non-management : canteen_days × rate
--   management     : weeks_with_canteen × (rate × 7)
-- Stored on the same effective-dated statutory_config row as EOBI/PESSI
-- so a single insert closes prior history and opens new rates atomically.
-- Defaults to 0 so existing rows stay neutral until HR sets the price.

alter table statutory_config
    add column canteen_per_day_rate numeric(15, 2) not null default 0;
