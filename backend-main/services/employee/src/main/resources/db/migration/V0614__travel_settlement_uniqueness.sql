-- ============================================================
-- Travel Module — settlement uniqueness hardening
--
-- Backs two invariants that were previously only enforced in
-- service code (TravelSettlementService) and therefore raceable:
--
-- 1) ONE active FINAL settlement per (travel_request, participant).
--
--    "Active" mirrors TravelSettlementService.hasActiveFinalSettlement():
--    a FINAL settlement counts unless it was CANCELLED or REJECTED, so a
--    fresh FINAL settlement can still be prepared after a prior attempt
--    was canceled/rejected. INTERIM / ADJUSTMENT settlements are not
--    constrained — a participant may legitimately have several of those.
--
--    is_finalized is deliberately NOT used as the predicate: it only
--    flips true at settle() time, which would still allow two DRAFT /
--    SUBMITTED / APPROVED FINAL settlements to coexist. The status
--    predicate matches the real business rule ("one settlement in
--    flight per participant") and the existing service check.
--
-- 2) A source EXPENSE / ADVANCE record can appear at most once inside
--    the same settlement.
--
--    ADJUSTMENT items are excluded: their source_record_id is a free
--    reference (no FK), and several adjustment lines on one settlement
--    may legitimately carry the same value.
-- ============================================================


CREATE UNIQUE INDEX uq_travel_settlements_one_active_final
    ON travel_request_settlements (travel_request_id, travel_request_participant_id)
    WHERE settlement_type_code = 'FINAL'
      AND status NOT IN ('CANCELLED', 'REJECTED');


CREATE UNIQUE INDEX uq_travel_settlement_items_source
    ON travel_settlement_items (travel_settlement_id, source_type_code, source_record_id)
    WHERE source_type_code <> 'ADJUSTMENT';


COMMENT ON INDEX uq_travel_settlements_one_active_final IS
    'At most one non-CANCELLED/REJECTED FINAL settlement per travel request participant. Hard backstop for TravelSettlementService.hasActiveFinalSettlement / prepareFinalSettlement under concurrency.';

COMMENT ON INDEX uq_travel_settlement_items_source IS
    'A given EXPENSE / ADVANCE source record may be included at most once per settlement. ADJUSTMENT rows are exempt (source_record_id is a free reference, not a FK).';
