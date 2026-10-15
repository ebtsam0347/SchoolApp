// State-transition buttons for the bonus run header. Mirrors RunActions:
// only the transitions valid from the current state render, and the parent
// owns the mutations (invalidate + toast) so this stays presentational.

import type { ReactNode } from 'react';
import { Button, Popconfirm, Space, Tooltip } from 'antd';
import {
  CheckCircle2,
  Percent,
  Play,
  RotateCcw,
  Trash2,
} from 'lucide-react';
import type { BonusRunStatus } from '@/payroll';

type Mutation = { isPending: boolean; mutateAsync: () => Promise<unknown> };

export type BonusRunActionsProps = {
  status: BonusRunStatus;
  canWrite: boolean;
  compute: Mutation;
  approve: Mutation;
  reset: Mutation;
  remove: Mutation;
  /** Opens the percentage editor. DRAFT only. */
  onEditPercentage: () => void;
};

export function BonusRunActions({
  status,
  canWrite,
  compute,
  approve,
  reset,
  remove,
  onEditPercentage,
}: BonusRunActionsProps) {
  if (!canWrite) return null;

  const buttons: ReactNode[] = [];
  switch (status) {
    case 'DRAFT':
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
        <Button
          key="edit"
          icon={<Percent size={14} strokeWidth={1.75} />}
          onClick={onEditPercentage}
        >
          Edit percentage
        </Button>,
        <Popconfirm
          key="delete"
          title="Delete this bonus run?"
          description="Removes the settings row. You can create the same bonus again afterwards."
          okText="Delete"
          okButtonProps={{ danger: true }}
          onConfirm={() => remove.mutateAsync()}
        >
          <Button
            danger
            icon={<Trash2 size={14} strokeWidth={1.75} />}
            loading={remove.isPending}
          >
            Delete
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
        <Popconfirm
          key="approve"
          title="Approve this bonus run?"
          description="The computed bonus amounts become final."
          okText="Approve"
          okButtonProps={{ type: 'primary' }}
          onConfirm={() => approve.mutateAsync()}
        >
          <Button
            type="primary"
            icon={<CheckCircle2 size={14} strokeWidth={1.75} />}
            loading={approve.isPending}
          >
            Approve
          </Button>
        </Popconfirm>,
        <Popconfirm
          key="reset"
          title="Reset back to Draft?"
          description={
            <span style={{ display: 'block', maxWidth: 320 }}>
              Drops every computed bonus transaction. Use when the percentage
              or employee data needs fixing, then run Compute again.
            </span>
          }
          okText="Reset run"
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
    default:
      // APPROVED is terminal; the remaining states aren't reachable for a
      // bonus run — nothing to do.
      break;
  }

  return <Space wrap>{buttons}</Space>;
}
