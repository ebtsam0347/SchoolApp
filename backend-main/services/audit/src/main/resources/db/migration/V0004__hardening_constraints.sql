-- §7.audit round 2 — schema hardening on audit_events.
--
-- Closes audit.md #5/#26/#28/#29/#30. The partition migration (V0003)
-- left these as follow-ups; landing them now while the table is still
-- small enough for the CHECK adds to be cheap.

-- #5 — relax correlation_id to nullable. The ingest service was
-- synthesizing eventId-as-correlationId on missing input, which made
-- "find every event in this request" queries return single-row noise.
-- Producers SHOULD set correlationId; missing is a producer bug we'd
-- rather see as NULL (and warn in logs) than paper over.
alter table audit_events alter column correlation_id drop not null;

-- #28 — defend against producer clock skew + buggy emitters that send
-- a timestamp from the future. We allow a 60-second tolerance because
-- node clocks drift; rows past that get rejected at write time so a
-- compromised producer can't backdate or post-date arbitrarily.
alter table audit_events
    add constraint chk_audit_events_occurred_not_future
    check (occurred_at <= ingested_at + interval '60 seconds');

-- #29 — cap message length. text was unbounded; a misbehaving emitter
-- could ship a 10MB string. 4000 chars is generous enough for a
-- human-readable message line.
alter table audit_events
    add constraint chk_audit_events_message_length
    check (message is null or length(message) <= 4000);

-- #30 — cap the combined JSONB payload size at 1 MB. Producers that
-- need to ship bigger payloads should use a separate side-table; we
-- don't want one rogue audit row to bloat a partition.
alter table audit_events
    add constraint chk_audit_events_payload_size
    check (
        coalesce(octet_length(before_json::text), 0) +
        coalesce(octet_length(after_json::text), 0) +
        coalesce(octet_length(changes::text), 0) +
        coalesce(octet_length(metadata::text), 0)
        < 1048576
    );

-- #26 — facet-style "last 24h of payroll events" used to fall back to
-- a full scan on (occurred_at). Add a per-source index. Declared on
-- the partitioned parent so PG ≥ 11 propagates it to every partition.
create index idx_audit_events_source_occurred
    on audit_events (source_service, occurred_at desc);
