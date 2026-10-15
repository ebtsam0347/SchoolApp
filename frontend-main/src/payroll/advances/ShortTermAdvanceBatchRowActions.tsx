// Approve / reject actions for a single short-term advance batch row — used
// in the Approvals Inbox. Three-stage flow: PENDING (Manager) ->
// manager-approve -> MANAGER_APPROVED (HR) -> hr-approve -> HR_APPROVED
// (Accounts) -> accounts-approve -> APPROVED. Which endpoint "Approve"
// calls is picked from `resolveAdvanceApprovalStage(row)` — the same
// resolver the Stage badge uses — rather than duplicating the
// status/stage mapping here.

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp } from 'antd';

import { ApproveRejectActions } from '@/components';
import { getErrorMessage } from '@/lib';
import { shortTermAdvancesApi } from '../api';
import { ADVANCE_APPROVAL_STAGE_LABEL, resolveAdvanceApprovalStage } from '../format';
import type { ShortTermAdvanceBatchDto } from '../types';

export type ShortTermAdvanceBatchRowActionsProps = {
  row: ShortTermAdvanceBatchDto;
};

export function ShortTermAdvanceBatchRowActions({ row }: ShortTermAdvanceBatchRowActionsProps) {
  const { message } = AntdApp.useApp();
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ['payroll', 'short-term-advances'] });
    // Also used inside the Approvals Inbox, which now reads pending
    // advance batches from the combined composite — refresh that too.
    void qc.invalidateQueries({ queryKey: ['bff', 'approvals', 'inbox'] });
  };

  const approvalStage = resolveAdvanceApprovalStage(row);

  const approveMutation = useMutation({
    mutationFn: () => {
      switch (approvalStage) {
        case 'MANAGER_APPROVAL':
          return shortTermAdvancesApi.managerApprove(row.id);
        case 'HR_APPROVAL':
          return shortTermAdvancesApi.hrApprove(row.id);
        case 'ACCOUNTS_APPROVAL':
          return shortTermAdvancesApi.accountsApprove(row.id);
        default:
          // Stage badge already hides Approve in this case — guards against
          // calling the wrong endpoint if this ever fires anyway.
          return Promise.reject(new Error('Unknown approval stage'));
      }
    },
    onSuccess: () => {
      message.success(
        approvalStage ? `${ADVANCE_APPROVAL_STAGE_LABEL[approvalStage]}-approved` : 'Approved',
      );
      invalidate();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => shortTermAdvancesApi.reject(row.id, { reason }),
    onSuccess: () => {
      message.success('Batch rejected');
      invalidate();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  if (
    row.status !== 'PENDING' &&
    row.status !== 'MANAGER_APPROVED' &&
    row.status !== 'HR_APPROVED'
  )
    return null;

  return (
    <ApproveRejectActions
      actions={approvalStage ? ['APPROVE', 'REJECT'] : ['REJECT']}
      onApprove={async () => {
        await approveMutation.mutateAsync();
      }}
      onReject={async (reason) => {
        await rejectMutation.mutateAsync(reason);
      }}
      rejectTitle="Reject this advance batch?"
      hideReadOnlyHint
    />
  );
}