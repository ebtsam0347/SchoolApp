-- ============================================================
-- Travel Request approval hierarchy — drop the HR stage
--
-- Business change (HR Head, confirmed): HR is no longer a stage in the
-- TRAVEL_REQUEST approval chain. The chain becomes TRANSPORT -> DD/
-- Requester's Head, and DD/Head approval is now the FINAL approval.
--
--   Stage 1  PERMISSION   travel.transport.verify   'Transport Verification'
--   Stage 2  MANAGER       (requester's manager)     'DD/HOD Approval' (final)
--
-- travel.hr.approve (auth V0033) is untouched and stays a valid permission —
-- it is simply no longer referenced by this chain. TRAVEL_ADVANCE and
-- TRAVEL_SETTLEMENT chains (V0611/V0612) are left as-is; only TRAVEL_REQUEST
-- is re-pointed here.
--
-- Same deactivate-and-reseed technique as V0613: approval_workflow_stage
-- rows are DEACTIVATED (not deleted) for audit; in-flight requests already
-- hold their approval_steps snapshot from submit time and are unaffected.
--
-- HR_PENDING / HR_APPROVED / HR_REJECTED are dropped from the status check
-- constraint: the Travel module has not shipped to production yet (still on
-- its feature branch), so no live row can hold them.
-- ============================================================

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
                          'APPROVED',
                          'REJECTED',
                          'CANCELLED',
                          'IN_PROGRESS',
                          'COMPLETED',
                          'SETTLEMENT_PENDING',
                          'SETTLED'
            ));

update approval_workflow_stage
    set active = false, updated_by = 'system'
    where request_type = 'TRAVEL_REQUEST' and active;

insert into approval_workflow_stage
    (request_type, stage_no, resolver_kind, approve_permission, label, created_by, updated_by)
values
    ('TRAVEL_REQUEST', 1, 'PERMISSION', 'travel.transport.verify', 'Transport Verification', 'system', 'system'),
    ('TRAVEL_REQUEST', 2, 'MANAGER',    null,                      'DD/HOD Approval',        'system', 'system');
