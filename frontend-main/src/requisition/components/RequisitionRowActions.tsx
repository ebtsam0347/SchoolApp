// Action buttons for a single requisition row.
//
// Entirely driven by `row.actions` — the backend already computes exactly
// which of Submit/Approve/Reject/Cancel/Fulfill the calling user may
// perform on this specific row (ownership, employee.* permission, and
// current-stage assignment are all checked server-side). We only render
// whichever buttons are present; there's no client-side status/role
// re-derivation here.

import { App as AntdApp, Button, Space } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Ban, CheckCircle2, Pencil, Send } from 'lucide-react';

import { ApproveRejectActions } from '@/components';
import { getErrorMessage } from '@/lib';
import { requisitionApi } from '../api';
import type { RequisitionDto } from '../types';

export type RequisitionRowActionsProps = {
  row: RequisitionDto;
  onEdit: (row: RequisitionDto) => void;
  /** Hide the Cancel action — e.g. in an approvals queue, which is an
   * approver's view, not the requester's own management view. */
  hideCancel?: boolean;
};

export function RequisitionRowActions({ row, onEdit, hideCancel }: RequisitionRowActionsProps) {
  const { message, modal } = AntdApp.useApp();
  const qc = useQueryClient();

  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ['requisitions'] });
    // Also used inside the Approvals Inbox, which now reads pending
    // requisitions from the combined composite — refresh that too.
    void qc.invalidateQueries({ queryKey: ['bff', 'approvals', 'inbox'] });
  };

  const submitMutation = useMutation({
    mutationFn: () => requisitionApi.submit(row.id),
    onSuccess: () => {
      message.success('Submitted for approval');
      invalidate();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const approveMutation = useMutation({
    mutationFn: () => requisitionApi.approve(row.id),
    onSuccess: () => {
      message.success('Requisition approved');
      invalidate();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => requisitionApi.reject(row.id, { reason }),
    onSuccess: () => {
      message.success('Requisition rejected');
      invalidate();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const fulfillMutation = useMutation({
    mutationFn: () => requisitionApi.fulfill(row.id),
    onSuccess: () => {
      message.success('Requisition marked fulfilled');
      invalidate();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const cancelMutation = useMutation({
    mutationFn: () => requisitionApi.cancel(row.id),
    onSuccess: () => {
      message.success('Requisition cancelled');
      invalidate();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  // Editing only makes sense while the row is still in DRAFT, which is
  // exactly when SUBMIT is offered — same ownership/permission gate.
  const canSubmit = row.actions.includes('SUBMIT');
  const canFulfill = row.actions.includes('FULFILL');
  const canCancel = !hideCancel && row.actions.includes('CANCEL');

  return (
    <Space size={4} wrap={false}>
      {canSubmit ? (
        <>
          <Button size="small" icon={<Pencil size={12} />} onClick={() => onEdit(row)}>
            Edit
          </Button>
          <Button
            size="small"
            type="primary"
            icon={<Send size={12} />}
            loading={submitMutation.isPending}
            onClick={() => submitMutation.mutate()}
          >
            Submit
          </Button>
        </>
      ) : null}

      <ApproveRejectActions
        actions={row.actions}
        onApprove={async () => {
          await approveMutation.mutateAsync();
        }}
        onReject={async (reason) => {
          await rejectMutation.mutateAsync(reason);
        }}
        rejectTitle="Reject this requisition?"
        hideReadOnlyHint
      />

      {canFulfill ? (
        <Button
          size="small"
          type="primary"
          icon={<CheckCircle2 size={12} />}
          loading={fulfillMutation.isPending}
          onClick={() => fulfillMutation.mutate()}
        >
          Mark fulfilled
        </Button>
      ) : null}

      {canCancel ? (
        <Button
          size="small"
          danger
          icon={<Ban size={12} />}
          loading={cancelMutation.isPending}
          onClick={() =>
            modal.confirm({
              title: 'Cancel this requisition?',
              content: 'This cannot be undone — you can raise a new one later if needed.',
              okText: 'Cancel requisition',
              okButtonProps: { danger: true },
              cancelText: 'Keep',
              onOk: () => cancelMutation.mutateAsync(),
            })
          }
        >
          Cancel
        </Button>
      ) : null}
    </Space>
  );
}
