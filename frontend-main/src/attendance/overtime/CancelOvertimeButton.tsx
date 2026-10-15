import { App as AntdApp, Button } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { XCircle } from 'lucide-react';

import { getErrorMessage } from '@/lib';
import { overtimeApi } from '../api';
import type { OvertimeRequestDto } from '../types';

export function CancelOvertimeButton({ row }: { row: OvertimeRequestDto }) {
  const queryClient = useQueryClient();
  const { message, modal } = AntdApp.useApp();
  const canCancel = row.actions.includes('CANCEL');

  const cancelMutation = useMutation({
    mutationFn: () => overtimeApi.cancel(row.id),
    onSuccess: () => {
      message.success('OT cancelled');
      queryClient.invalidateQueries({ queryKey: ['bff', 'overtime'] });
      queryClient.invalidateQueries({ queryKey: ['overtime'] });
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
          title: 'Cancel this OT request?',
          content:
            row.status === 'APPROVED'
              ? "This was approved — the day's OT minutes will be recalculated."
              : 'You can resubmit later if needed.',
          okText: 'Cancel OT',
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
