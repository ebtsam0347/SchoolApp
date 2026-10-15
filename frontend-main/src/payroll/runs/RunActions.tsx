// State-transition buttons for the payroll run header. Visibility is
// gated on the current run state — only the transitions that make sense
// from the current state render. Mutations are passed in by the parent so
// this stays purely presentational; the parent owns invalidate + toast.

import { Button, Popconfirm, Space, Tooltip } from 'antd';
import {
  Ban,
  CheckCircle2,
  Lock,
  Play,
  RotateCcw,
  Send,
} from 'lucide-react';
import type { PayrollRunStatus } from '@/payroll';

type Mutation = { isPending: boolean; mutateAsync: () => Promise<unknown> };

export type RunActionsProps = {
  status: PayrollRunStatus;
  canWrite: boolean;
  lockAttendance: Mutation;
  compute: Mutation;
  approve: Mutation;
  post: Mutation;
  cancel: Mutation;
  reset: Mutation;
};

export function RunActions({
  status,
  canWrite,
  lockAttendance,
  compute,
  approve,
  post,
  cancel,
  reset,
}: RunActionsProps) {
  if (!canWrite) return null;

  const buttons: React.ReactNode[] = [];
  switch (status) {
    case 'DRAFT':
      buttons.push(
        <Button
          key="lock"
          type="primary"
          icon={<Lock size={14} strokeWidth={1.75} />}
          loading={lockAttendance.isPending}
          onClick={() => lockAttendance.mutateAsync()}
        >
          Lock attendance
        </Button>,
        <Popconfirm
          key="cancel"
          title="Cancel this draft run?"
          okText="Cancel"
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
          title="Cancel this locked run? Attendance will need re-locking."
          okText="Cancel run"
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
              Drops every payslip on this run and clears the computed
              timestamp. Use when employee data needs fixing. The attendance
              period stays locked — re-run Compute once corrections are done.
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
    case 'APPROVED':
      buttons.push(
        <Popconfirm
          key="post"
          title="Post this run? This is irreversible — PDFs are generated and per-employee events fire."
          okText="Post"
          okButtonProps={{ type: 'primary' }}
          onConfirm={() => post.mutateAsync()}
        >
          <Button
            type="primary"
            icon={<Send size={14} strokeWidth={1.75} />}
            loading={post.isPending}
          >
            Post
          </Button>
        </Popconfirm>,
      );
      break;
    case 'POSTING':
      buttons.push(
        <Tooltip
          key="busy"
          title="Posting is running. Page will refresh automatically."
        >
          <Button loading>Posting</Button>
        </Tooltip>,
      );
      break;
    case 'POSTED':
    case 'CANCELLED':
      // terminal — nothing to do
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
