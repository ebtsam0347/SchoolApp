-- ============================================================
-- Travel Request approval hierarchy — TRANSPORT -> DD/HOD -> HR
--
-- Business change: the TRAVEL_REQUEST approval chain becomes a
-- three-stage hierarchy.
--
--   Stage 1  PERMISSION   travel.transport.verify   'Transport Verification'
--            Transport verifies company-vehicle availability / number and
--            the distance of every leg, and may set actual_distance_km.
--   Stage 2  MANAGER       (requester's manager)     'DD/HOD Approval'
--            The requester's Departmental Head / HOD, resolved by the
--            existing ManagerResolver against employees.manager_id.
--   Stage 3  PERMISSION   travel.hr.approve          'HR Approval'
--
-- A rejection at any stage is terminal (the generic approval engine skips
-- the remaining stages) and maps to TRANSPORT_REJECTED / DD_REJECTED /
-- HR_REJECTED. Only the final HR approval yields APPROVED.
--
-- This migration:
--   1. widens ck_travel_requests_status to the stage-specific statuses;
--   2. re-points ONLY the TRAVEL_REQUEST workflow chain. TRAVEL_ADVANCE and
--      TRAVEL_SETTLEMENT chains (V0611/V0612) are left untouched.
--
-- Editing a chain never disturbs in-flight requests: approval_steps are
-- snapshot at submit time. Old approval_workflow_stage rows are DEACTIVATED
-- (not deleted) for audit; the partial unique index on active rows means the
-- deactivated rows do not collide with the new ones. Same technique as
-- V0612__travel_approval_chains.sql / payroll V0042.
-- ============================================================

-- ── 1. status check constraint ─────────────────────────────────────────────
-- travel_requests.status stays VARCHAR(30) (longest new value is 18 chars).

alter table travel_requests
    drop constraint if exists ck_travel_requests_status;

alter table travel_requests
    add constraint ck_travel_requests_status
        check (status in (
                          'DRAFT',
                          'SUBMITTED',
                          'TRANSPORT_PENDING',
                          'TRANSPORT_APPROVED',
                          'TRANSPORT_REJECTED',
                          'DD_PENDING',
                          'DD_APPROVED',
                          'DD_REJECTED',
                          'HR_PENDING',
                          'HR_APPROVED',
                          'HR_REJECTED',
                          'APPROVED',
                          'REJECTED',
                          'CANCELLED',
                          'IN_PROGRESS',
                          'COMPLETED',
                          'SETTLEMENT_PENDING',
                          'SETTLED'
            ));

-- ── 2. TRAVEL_REQUEST chain: TRANSPORT -> DD/HOD -> HR ─────────────────────

update approval_workflow_stage
    set active = false, updated_by = 'system'
    where request_type = 'TRAVEL_REQUEST' and active;

insert into approval_workflow_stage
    (request_type, stage_no, resolver_kind, approve_permission, label, created_by, updated_by)
values
    ('TRAVEL_REQUEST', 1, 'PERMISSION', 'travel.transport.verify', 'Transport Verification', 'system', 'system'),
    ('TRAVEL_REQUEST', 2, 'MANAGER',    null,                      'DD/HOD Approval',        'system', 'system'),
    ('TRAVEL_REQUEST', 3, 'PERMISSION', 'travel.hr.approve',       'HR Approval',            'system', 'system');
