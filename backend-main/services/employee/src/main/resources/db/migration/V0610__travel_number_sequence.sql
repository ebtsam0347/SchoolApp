-- ============================================================
-- Travel Module
-- Table: travel_number_sequence
--
-- Per-year, per-scope allocator for human-readable Travel
-- identifiers:
--
--   REQUEST     -> TR-{YYYY}-{NNNNNN}   e.g. TR-2026-000125
--   ADVANCE     -> ADV-{YYYY}-{NNNNNN}  e.g. ADV-2026-000125
--   SETTLEMENT  -> SET-{YYYY}-{NNNNNN}  e.g. SET-2026-000125
--
-- One row per (scope, year). A single upsert
--   INSERT ... ON CONFLICT (scope, year) DO UPDATE ... RETURNING
-- atomically inserts or increments the row inside the caller's
-- business transaction; Postgres' row-level lock serializes
-- concurrent allocations. Same pattern as employee_code_sequence
-- (V0002) / EmployeeCodeService.
--
-- System/allocator table: no audit columns (matches
-- employee_code_sequence).
-- ============================================================


CREATE TABLE travel_number_sequence
(
    scope      VARCHAR(20) NOT NULL,
    year       INT         NOT NULL,
    last_value BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_travel_number_sequence
        PRIMARY KEY (scope, year),

    CONSTRAINT ck_travel_number_sequence_scope
        CHECK (scope IN ('REQUEST', 'ADVANCE', 'SETTLEMENT')),

    CONSTRAINT ck_travel_number_sequence_last_value
        CHECK (last_value >= 0)
);


COMMENT ON TABLE travel_number_sequence IS
    'Per-year, per-scope allocator for human-readable Travel identifiers (TR-/ADV-/SET-YYYY-NNNNNN). One row per (scope, year); incremented by an upsert inside the caller transaction.';

COMMENT ON COLUMN travel_number_sequence.scope IS
    'Identifier family being allocated: REQUEST, ADVANCE or SETTLEMENT.';

COMMENT ON COLUMN travel_number_sequence.year IS
    'Calendar year the sequence belongs to. Numbering restarts each year.';

COMMENT ON COLUMN travel_number_sequence.last_value IS
    'Last value handed out for this (scope, year). Next allocation returns last_value + 1.';
