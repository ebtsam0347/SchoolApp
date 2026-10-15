// Read-only detail drawer that opens when a manager clicks "Details" on a
// pending overtime row. Approve/Reject stay on the row itself — same split
// as LeaveDecisionDrawer, minus the per-day picker (overtime has no
// per-day concept).

import { Drawer, Empty } from 'antd';
import { OVERTIME_TYPE_LABEL, type ApprovalInboxOvertimeRow } from '@/attendance';
import { useIsDesktop } from '@/lib';
import { RowSummary } from './RowSummary';

function getOvertimeType(row: ApprovalInboxOvertimeRow) {
  const request = row.request as ApprovalInboxOvertimeRow['request'] & {
    overtimeType?: ApprovalInboxOvertimeRow['request']['type'];
  };
  return request.overtimeType ?? request.type;
}

export type OvertimeDecisionDrawerProps = {
  row: ApprovalInboxOvertimeRow | null;
  onClose: () => void;
};

export function OvertimeDecisionDrawer({ row, onClose }: OvertimeDecisionDrawerProps) {
  const open = !!row;
  // 560px is a desktop width; a phone needs the full viewport.
  const isDesktop = useIsDesktop();

  if (!row) {
    return (
      <Drawer open={open} onClose={onClose} title="Overtime" size={520} destroyOnHidden>
        <Empty description="No selection" />
      </Drawer>
    );
  }

  const req = row.request;
  const overtimeType = getOvertimeType(row);

  return (
    <Drawer
      title={`Overtime #${req.id} · ${OVERTIME_TYPE_LABEL[overtimeType]}`}
      open={open}
      onClose={onClose}
      size={isDesktop ? 560 : '100%'}
      destroyOnHidden
    >
      <RowSummary
        employee={row.employee}
        title={
          req.factoryCode
            ? `${OVERTIME_TYPE_LABEL[overtimeType]} · ${req.factoryCode}`
            : OVERTIME_TYPE_LABEL[overtimeType]
        }
        rangeFrom={req.workDate}
        rangeTo={req.workDate}
        reason={req.reason}
        steps={req.steps}
        timelineKind="overtime"
        requestId={req.id}
      />
    </Drawer>
  );
}
