import { useState } from 'react';
import { App as AntdApp, Button, Popconfirm, Space } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { getErrorMessage } from '@/lib';
import { belongingIssuanceApi } from '../api';
import { ClearanceUpdateModal } from './ClearanceUpdateModal';
import type { BelongingIssuanceDto } from '../types';

export type BelongingIssuanceRowActionsProps = {
  row: BelongingIssuanceDto;
  onEdit: (row: BelongingIssuanceDto) => void;
};

export function BelongingIssuanceRowActions({ row, onEdit }: BelongingIssuanceRowActionsProps) {
  const [clearanceOpen, setClearanceOpen] = useState(false);
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const isActive = row.activationStatus !== 'INACTIVE';

  const activationMutation = useMutation({
    mutationFn: () =>
      belongingIssuanceApi.updateActivation(row.id!, isActive ? 'INACTIVE' : 'ACTIVE'),
    onSuccess: () => {
      message.success(isActive ? 'Marked inactive' : 'Reactivated');
      queryClient.invalidateQueries({ queryKey: ['belonging-issuance'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  return (
    <>
      <Space size="small">
        <Button size="small" onClick={() => onEdit(row)}>
          Edit
        </Button>
        {!isActive && (
          <Popconfirm
            title="Reactivate this issuance?"
            onConfirm={() => activationMutation.mutate()}
          >
            <Button size="small" loading={activationMutation.isPending}>
              Reactivate
            </Button>
          </Popconfirm>
        )}
        <Button size="small" onClick={() => setClearanceOpen(true)}>
          Clearance
        </Button>
      </Space>
      <ClearanceUpdateModal
        open={clearanceOpen}
        onClose={() => setClearanceOpen(false)}
        issuance={row}
      />
    </>
  );
}
