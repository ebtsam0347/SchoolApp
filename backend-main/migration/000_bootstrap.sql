-- §-1 bootstrap: the scratch schema every later step depends on.
--
-- MAPPING.md §0.3 describes `migration.id_map` but nothing created it —
-- it was made by hand on the dev box, so a fresh environment (UAT, prod)
-- fails on the first `INSERT INTO migration.id_map` in 01. Run this once,
-- before 00_reset.sql.
--
-- The schema is scratch: it holds the legacy-id → FSHRMS-id map, the
-- initial ESS passwords, and the leave-cycle helpers 08 defines. It is
-- dropped once the migration is signed off (MAPPING.md §8).
--
-- Idempotent. Run as the DB owner — on a server host that is the env role
-- (`uat` / `prod`), which needs CREATE on the DATABASE, not just on its
-- schemas (same grant the pg_trgm extension needs; see CLAUDE.md).

CREATE SCHEMA IF NOT EXISTS migration;

CREATE TABLE IF NOT EXISTS migration.id_map (
    source_table varchar(64) not null,
    source_id    text        not null,   -- legacy ids may carry leading zeros
    target_table varchar(64) not null,
    target_id    bigint      not null,
    primary key (source_table, source_id, target_table)
);

-- Reverse lookups (target → source) are used by the verification queries.
CREATE INDEX IF NOT EXISTS idx_id_map_target
    ON migration.id_map (target_table, target_id);

-- Initial ESS passwords, filled by 07 and handed to HR. Created here (not
-- in 07) because 00_reset truncates it, and reset runs first. Holds live
-- plaintext credentials between 07 and the hand-off — 07 §6.3 has the
-- export-and-drop procedure.
CREATE TABLE IF NOT EXISTS migration.ess_initial_passwords (
    employee_code varchar(32)  primary key,
    full_name     varchar(128) not null,
    password      text         not null,
    generated_at  timestamp    not null default now()
);

SELECT 'migration.id_map' AS bootstrapped, count(*) AS existing_rows FROM migration.id_map;
