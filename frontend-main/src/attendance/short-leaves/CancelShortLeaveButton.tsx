import { App as AntdApp, Button } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { XCircle } from 'lucide-react';

import { getErrorMessage } from '@/lib';
import { shortLeavesApi } from '../api';
import type { ShortLeaveRequestDto } from '../types';

export function CancelShortLeaveButton({ row }: { row: ShortLeaveRequestDto }) {
  const queryClient = useQueryClient();
  const { message, modal } = AntdApp.useApp();
  const canCancel = row.actions.includes('CANCEL');

  const cancelMutation = useMutation({
    mutationFn: () => shortLeavesApi.cancel(row.id),
    onSuccess: () => {
      message.success('Short leave cancelled');
      queryClient.invalidateQueries({ queryKey: ['short-leaves'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  if (!canCancel) return null;

  return (
    <Button
      size="small"
      danger
      icon={<XCircle size={12} />}
      loading={cancelMutation.isPending}
      onClick={() =>
        modal.confirm({
          title: 'Cancel this short leave?',
          content:
            row.status === 'APPROVED'
              ? 'This was already approved — cancelling releases it before the leave date arrives.'
              : 'You can resubmit later if needed.',
          okText: 'Cancel short leave',
          okButtonProps: { danger: true },
          cancelText: 'Keep',
          onOk: () => cancelMutation.mutateAsync(),
        })
      }
    >
      Cancel
    </Button>
  );
}