// Approve / Reject button pair, gated on `actions[]` from the upstream.
//
// The action grammar matches what `/approvals/inbox` returns on each row:
// strings like 'APPROVE', 'REJECT', 'DECIDE_PER_DAY'. We only render buttons
// for actions present in the array, so a row with only 'APPROVE' shows just
// approve, and a stage that's read-only shows the "Read-only" hint.
//
// Replaces SimpleApproveReject in inbox.tsx and the bespoke pairs in
// time/attendance, time/overtime, account/delegations.

import { Button, Space, Typography } from 'antd';
import { useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import { CheckCircle2, XCircle } from 'lucide-react';
import { RejectReasonModal } from './RejectReasonModal';

const { Text } = Typography;

export type ApproveRejectActionsProps = {
  /** Action codes the BFF says the caller can perform (e.g. ['APPROVE','REJECT']). */
  actions: readonly string[];
  /** Awaited; resolved → success toast handled by caller via mutation. */
  onApprove: () => Promise<void>;
  /** Awaited with the reason text. */
  onReject: (reason: string) => Promise<void>;
  /** Slot for extra leading buttons (e.g. "Per-day", "Details"). */
  leading?: ReactNode;
  /** Modal copy override. */
  rejectTitle?: ReactNode;
  rejectDescription?: ReactNode;
  /** Button size. Defaults to 'small' for table rows. */
  size?: 'small' | 'middle' | 'large';
  /** Hide the "Read-only" placeholder when no actions are available. */
  hideReadOnlyHint?: boolean;
  className?: string;
  style?: CSSProperties;
};

export function ApproveRejectActions({
  actions,
  onApprove,
  onReject,
  leading,
  rejectTitle = 'Reject?',
  rejectDescription,
  size = 'small',
  hideReadOnlyHint,
  className,
  style,
}: ApproveRejectActionsProps) {
  const [rejecting, setRejecting] = useState(false);
  const [approving, setApproving] = useState(false);

  const canApprove = actions.includes('APPROVE');
  const canReject = actions.includes('REJECT');
  const iconSize = size === 'large' ? 14 : 12;

  const handleApprove = async () => {
    setApproving(true);
    try {
      await onApprove();
    } finally {
      setApproving(false);
    }
  };

  return (
    <>
      <Space size={4} className={className} style={style}>
        {leading}
        {canReject ? (
          <Button
            size={size}
            danger
            icon={<XCircle size={iconSize} />}
            onClick={() => setRejecting(true)}
          >
            Reject
          </Button>
        ) : null}
        {canApprove ? (
          <Button
            size={size}
            type="primary"
            icon={<CheckCircle2 size={iconSize} />}
            loading={approving}
            onClick={handleApprove}
          >
            Approve
          </Button>
        ) : null}
        {!canApprove && !canReject && !leading && !hideReadOnlyHint ? (
          <Text type="secondary" style={{ fontSize: 11 }}>
            Read-only
          </Text>
        ) : null}
      </Space>
      <RejectReasonModal
        open={rejecting}
        title={rejectTitle}
        description={rejectDescription}
        onCancel={() => setRejecting(false)}
        onConfirm={async (reason) => {
          await onReject(reason);
          setRejecting(false);
        }}
      />
    </>
  );
}
