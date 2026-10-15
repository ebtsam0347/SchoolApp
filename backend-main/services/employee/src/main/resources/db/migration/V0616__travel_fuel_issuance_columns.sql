-- ============================================================
-- Travel Module
-- Fuel/petrol issuance columns for travel_requests and
-- travel_request_legs.
--
-- Petrol issuance is calculated by Transport during verification
-- of the TRAVEL_REQUEST approval chain, from the leg's Transport-
-- approved travel_request_legs.actual_distance_km (never the
-- planned distance) and the resolved travel_fuel_policies row.
--
-- travel_fuel_policy_id is nullable on both tables: a leg may
-- legitimately carry no fuel issuance (e.g. air/train/public
-- transport legs Transport never assigns a fuel policy to).
-- ============================================================


ALTER TABLE travel_request_legs
    ADD COLUMN travel_fuel_policy_id BIGINT,
    ADD COLUMN petrol_issued         NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE travel_requests
    ADD COLUMN travel_fuel_policy_id BIGINT,
    ADD COLUMN petrol_issued         NUMERIC(12, 2) NOT NULL DEFAULT 0;


-- ============================================================
-- Constraints
-- ============================================================

ALTER TABLE travel_request_legs
    ADD CONSTRAINT fk_travel_request_legs_fuel_policy
        FOREIGN KEY (travel_fuel_policy_id)
            REFERENCES travel_fuel_policies (id),
    ADD CONSTRAINT ck_travel_request_legs_petrol_issued
        CHECK (petrol_issued >= 0);

ALTER TABLE travel_requests
    ADD CONSTRAINT fk_travel_requests_fuel_policy
        FOREIGN KEY (travel_fuel_policy_id)
            REFERENCES travel_fuel_policies (id),
    ADD CONSTRAINT ck_travel_requests_petrol_issued
        CHECK (petrol_issued >= 0);


-- ============================================================
-- Indexes
-- ============================================================

CREATE INDEX idx_travel_request_legs_fuel_policy
    ON travel_request_legs (travel_fuel_policy_id);

CREATE INDEX idx_travel_requests_fuel_policy
    ON travel_requests (travel_fuel_policy_id);


-- ============================================================
-- Documentation
-- ============================================================

COMMENT
ON COLUMN travel_request_legs.travel_fuel_policy_id IS
    'Fuel policy Transport applied to this leg at verification time. NULL when the leg has no fuel issuance (e.g. air/train/public transport).';

COMMENT
ON COLUMN travel_request_legs.petrol_issued IS
    'Petrol quantity issued for this leg, calculated at Transport verification as actual_distance_km / fuel_average of travel_fuel_policy_id. Zero when the policy''s fuel_average is zero or no policy applies.';

COMMENT
ON COLUMN travel_requests.travel_fuel_policy_id IS
    'Fuel policy applicable to the overall request: the single policy shared by every leg that has one, or NULL when legs carry different fuel policies. See travel_request_legs.travel_fuel_policy_id for the per-leg source of truth.';

COMMENT
ON COLUMN travel_requests.petrol_issued IS
    'Sum of travel_request_legs.petrol_issued across every leg of this request, maintained at Transport verification.';
