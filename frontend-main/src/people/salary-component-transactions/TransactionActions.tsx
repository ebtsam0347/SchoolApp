// State-transition buttons for the salary component transaction header.
// Mirrors RunActions from payroll/runs.

import { Button, Popconfirm, Space, Tooltip } from 'antd';
import {
  Ban,
  CheckCircle2,
  Play,
  RotateCcw,
} from 'lucide-react';
import type { SalaryComponentTransactionStatus } from './types';

type Mutation = { isPending: boolean; mutateAsync: () => Promise<unknown> };

export type TransactionActionsProps = {
  status: SalaryComponentTransactionStatus;
  canWrite: boolean;
  compute: Mutation;
  approve: Mutation;
  cancel: Mutation;
  reset: Mutation;
};

export function TransactionActions({
  status,
  canWrite,
  compute,
  approve,
  cancel,
  reset,
}: TransactionActionsProps) {
  if (!canWrite) return null;

  const buttons: React.ReactNode[] = [];
  switch (status) {
    case 'DRAFT':
    case 'LOCKED':
      buttons.push(
        <Button
          key="compute"
          type="primary"
          icon={<Play size={14} strokeWidth={1.75} />}
          loading={compute.isPending}
          onClick={() => compute.mutateAsync()}
        >
          Compute
        </Button>,
        <Popconfirm
          key="cancel"
          title="Cancel this transaction?"
          okText="Cancel transaction"
          cancelText="Keep"
          okButtonProps={{ danger: true }}
          onConfirm={() => cancel.mutateAsync()}
        >
          <Button
            danger
            icon={<Ban size={14} strokeWidth={1.75} />}
            loading={cancel.isPending}
          >
            Cancel
          </Button>
        </Popconfirm>,
      );
      break;
    case 'COMPUTING':
      buttons.push(
        <Tooltip
          key="busy"
          title="Compute is running. Page will refresh automatically."
        >
          <Button loading>Computing</Button>
        </Tooltip>,
      );
      break;
    case 'COMPUTED':
      buttons.push(
        <Button
          key="approve"
          type="primary"
          icon={<CheckCircle2 size={14} strokeWidth={1.75} />}
          loading={approve.isPending}
          onClick={() => approve.mutateAsync()}
        >
          Approve
        </Button>,
        <Popconfirm
          key="reset"
          title="Reset back to Locked?"
          description={
            <span style={{ display: 'block', maxWidth: 320 }}>
              Drops every line on this transaction and clears the computed
              timestamp. Use when employee data needs fixing. The attendance
              period stays locked — re-run Compute once corrections are done.
            </span>
          }
          okText="Reset transaction"
          okButtonProps={{ danger: true }}
          onConfirm={() => reset.mutateAsync()}
        >
          <Button
            danger
            icon={<RotateCcw size={14} strokeWidth={1.75} />}
            loading={reset.isPending}
          >
            Reset
          </Button>
        </Popconfirm>,
      );
      break;
    case 'APPROVED':
    case 'POSTING':
    case 'POSTED':
    case 'CANCELLED':
      break;
    case 'FAILED':
      buttons.push(
        <Button
          key="retry"
          icon={<RotateCcw size={14} strokeWidth={1.75} />}
          onClick={() => compute.mutateAsync()}
          loading={compute.isPending}
        >
          Retry compute
        </Button>,
      );
      break;
  }

  return <Space wrap>{buttons}</Space>;
}
