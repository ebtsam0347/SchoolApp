-- ============================================================
-- Travel Module
-- Column: travel_requests.total_distance_km
--
-- Purpose:
-- Request-level summary of the finalized Transport-approved leg
-- distances. Pure summary column:
--
--   travel_requests.total_distance_km
--       = SUM(travel_request_legs.actual_distance_km)
--
-- travel_request_legs.actual_distance_km remains the sole
-- authoritative Transport-approved distance for a leg. This
-- column never independently recalculates or overrides it — it
-- is derived from the persisted legs at Transport verification.
-- ============================================================


ALTER TABLE travel_requests
    ADD COLUMN total_distance_km NUMERIC(12, 2) NOT NULL DEFAULT 0;


-- ============================================================
-- Constraints
-- ============================================================

ALTER TABLE travel_requests
    ADD CONSTRAINT ck_travel_requests_total_distance_km
        CHECK (total_distance_km >= 0);


-- ============================================================
-- Documentation
-- ============================================================

COMMENT
ON COLUMN travel_requests.total_distance_km IS
    'Sum of travel_request_legs.actual_distance_km across every leg of this request, maintained at Transport verification. Pure summary column — travel_request_legs.actual_distance_km remains the sole authoritative Transport-approved distance.';
