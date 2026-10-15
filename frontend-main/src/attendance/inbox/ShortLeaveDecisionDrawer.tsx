// Read-only detail drawer that opens when a manager clicks "Details" on a
// pending short-leave row. Approve/Reject stay on the row itself — same
// split as LeaveDecisionDrawer. Activity comes from
// `/bff/timeline/short-leave/{id}`.

import { Drawer, Empty } from 'antd';
import { formatMinutes } from '@/attendance';
import { useIsDesktop } from '@/lib';
import { RowSummary } from './RowSummary';
import type { ShortLeaveInboxRow } from './ShortLeaveInbox';

export type ShortLeaveDecisionDrawerProps = {
  row: ShortLeaveInboxRow | null;
  onClose: () => void;
};

export function ShortLeaveDecisionDrawer({ row, onClose }: ShortLeaveDecisionDrawerProps) {
  const open = !!row;
  // 560px is a desktop width; a phone needs the full viewport.
  const isDesktop = useIsDesktop();

  if (!row) {
    return (
      <Drawer open={open} onClose={onClose} title="Short leave" size={520} destroyOnHidden>
        <Empty description="No selection" />
      </Drawer>
    );
  }

  const req = row.request;
  const title = `${req.startTime} – ${req.endTime} · ${formatMinutes(req.totalMinutes)}`;

  return (
    <Drawer
      title={`Short leave #${req.id}`}
      open={open}
      onClose={onClose}
      size={isDesktop ? 560 : '100%'}
      destroyOnHidden
    >
      <RowSummary
        employee={row.employee}
        title={title}
        rangeFrom={req.leaveDate}
        rangeTo={req.leaveDate}
        reason={req.reason}
        steps={req.steps}
        timelineKind="short-leave"
        requestId={req.id}
      />
    </Drawer>
  );
}
