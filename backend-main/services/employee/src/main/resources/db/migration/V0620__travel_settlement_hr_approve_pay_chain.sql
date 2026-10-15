-- ============================================================
-- Travel Settlement — 2-stage HR approval / Accounts payment chain
--
-- Business change: a Travel Settlement now goes through two distinct
-- actors instead of one shared "Finance/HR" pool:
--
--   DRAFT -> submit -> SUBMITTED
--     -> HR approve (travel.settlement.approve)      -> HR_APPROVED
--     -> Accounts verify + confirm payment            -> PAID
--          (travel.settlement.paid)
--   (HR reject at SUBMITTED -> REJECTED, terminal)
--
-- HR_APPROVED is a new status (widened into the check constraint below).
-- PAID already existed in the status model and is reused as-is for the
-- Accounts payment confirmation, per the historical meaning of that value.
-- The pre-existing APPROVED / PARTIALLY_APPROVED / PAYMENT_PENDING /
-- RECOVERY_PENDING / RECOVERED / SETTLED / UNDER_REVIEW values are left in
-- the model (unused by this flow going forward) since nothing has shipped
-- to production yet and no live row can reference them.
--
-- 1. widens ck_travel_settlements_status to add HR_APPROVED;
-- 2. re-points the TRAVEL_SETTLEMENT chain to two PERMISSION stages.
--    TRAVEL_REQUEST and TRAVEL_ADVANCE chains are left untouched.
--
-- Same deactivate-and-reseed technique as V0612/V0613/V0618: old
-- approval_workflow_stage rows are DEACTIVATED (not deleted) for audit;
-- the partial unique index on active rows means the deactivated rows
-- don't collide with the new ones. Editing a chain never disturbs
-- in-flight requests — approval_steps are snapshot at submit time.
-- ============================================================

alter table travel_request_settlements
    drop constraint if exists ck_travel_settlements_status;

alter table travel_request_settlements
    add constraint ck_travel_settlements_status
        check (
            status in (
                       'DRAFT',
                       'SUBMITTED',
                       'HR_APPROVED',
                       'UNDER_REVIEW',
                       'APPROVED',
                       'PARTIALLY_APPROVED',
                       'REJECTED',
                       'PAYMENT_PENDING',
                       'RECOVERY_PENDING',
                       'PAID',
                       'RECOVERED',
                       'SETTLED',
                       'CANCELLED'
                )
            );

update approval_workflow_stage
    set active = false, updated_by = 'system'
    where request_type = 'TRAVEL_SETTLEMENT' and active;

insert into approval_workflow_stage
    (request_type, stage_no, resolver_kind, approve_permission, label, created_by, updated_by)
values
    ('TRAVEL_SETTLEMENT', 1, 'PERMISSION', 'travel.settlement.approve', 'HR Settlement Verification',    'system', 'system'),
    ('TRAVEL_SETTLEMENT', 2, 'PERMISSION', 'travel.settlement.paid',    'Accounts Payment Confirmation', 'system', 'system');
