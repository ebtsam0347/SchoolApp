// Read-only detail drawer that opens when a manager clicks "Details" on a
// pending regularization row. Approve/Reject stay on the row itself — same
// split as LeaveDecisionDrawer / OvertimeDecisionDrawer.

import { Drawer, Empty } from 'antd';
import { formatClock, type ApprovalInboxRegularizationRow } from '@/attendance';
import { useIsDesktop } from '@/lib';
import { RowSummary } from './RowSummary';

export type RegularizationDecisionDrawerProps = {
  row: ApprovalInboxRegularizationRow | null;
  onClose: () => void;
};

export function RegularizationDecisionDrawer({
  row,
  onClose,
}: RegularizationDecisionDrawerProps) {
  const open = !!row;
  // 560px is a desktop width; a phone needs the full viewport.
  const isDesktop = useIsDesktop();

  if (!row) {
    return (
      <Drawer open={open} onClose={onClose} title="Regularization" size={520} destroyOnHidden>
        <Empty description="No selection" />
      </Drawer>
    );
  }

  const req = row.request;
  const title = [
    req.requestedCheckIn ? `IN ${formatClock(req.requestedCheckIn)}` : null,
    req.requestedCheckOut ? `OUT ${formatClock(req.requestedCheckOut)}` : null,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <Drawer
      title={`Regularization #${req.id}`}
      open={open}
      onClose={onClose}
      size={isDesktop ? 560 : '100%'}
      destroyOnHidden
    >
      <RowSummary
        employee={row.employee}
        title={title || 'Regularization'}
        rangeFrom={req.workDate}
        rangeTo={req.workDate}
        reason={req.reason}
        steps={req.steps}
        timelineKind="regularization"
        requestId={req.id}
      />
    </Drawer>
  );
}
