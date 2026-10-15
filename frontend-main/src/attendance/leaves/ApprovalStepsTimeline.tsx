// Vertical, time-style stepper used in the leave detail drawer.
//
// Different shape from the inbox ApprovalSteps (which is a card grid).
// This one shows the chain in chronological order with status icon +
// connecting line + acted-by metadata. Used for trust / audit affordance
// rather than for action.

import { theme as antdTheme, Typography } from 'antd';
import { CheckCircle2, Clock, XCircle } from 'lucide-react';
import {
  APPROVAL_STEP_STATUS_LABEL,
  APPROVAL_STEP_STATUS_TONE,
  formatDateMonthDay,
  type LeaveRequestDto,
} from '@/attendance';
import { StatusPill } from '@/components';

const { Text } = Typography;
const { useToken } = antdTheme;

export type ApprovalStepsTimelineProps = {
  steps: LeaveRequestDto['steps'];
  /** Stage number to highlight as "you are here" — pass `currentStage?.stageNo`. */
  currentStageNo?: number | null;
};

export function ApprovalStepsTimeline({ steps, currentStageNo }: ApprovalStepsTimelineProps) {
  const { token } = useToken();
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      {steps.map((s, i) => {
        const isLast = i === steps.length - 1;
        const isCurrent = s.stageNo === currentStageNo;
        const tone = APPROVAL_STEP_STATUS_TONE[s.status];
        // Role-only stages always read as "{role} role" — consistent
        // whether or not the stage has been decided yet — since there's no
        // reliable way to resolve the specific staff member's name from
        // the frontend without the backend sending it.
        const assignedName = s.assignedToName ?? `${s.stageRole} role`;
        const actedByName = s.decidedBy !== s.assignedTo ? s.decidedByName : null;
        const toneColor =
          tone === 'success'
            ? token.colorSuccess
            : tone === 'error'
              ? token.colorError
              : tone === 'warning'
                ? token.colorWarning
                : token.colorTextTertiary;
        return (
          <div
            key={s.stageNo}
            style={{
              display: 'grid',
              gridTemplateColumns: '24px 1fr',
              gap: 12,
              padding: isCurrent ? '8px 10px' : '8px 0',
              margin: isCurrent ? '0 -10px' : undefined,
              borderRadius: isCurrent ? token.borderRadius : undefined,
              background: isCurrent
                ? `color-mix(in srgb, ${token.colorPrimary} 8%, transparent)`
                : undefined,
              position: 'relative',
            }}
          >
            <div
              style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}
            >
              <span
                aria-hidden
                style={{
                  width: 18,
                  height: 18,
                  borderRadius: 'var(--app-radius-full)',
                  background: `color-mix(in srgb, ${toneColor} 20%, transparent)`,
                  border: `2px solid ${toneColor}`,
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: toneColor,
                }}
              >
                {s.status === 'APPROVED' ? <CheckCircle2 size={11} strokeWidth={2.5} /> : null}
                {s.status === 'REJECTED' ? <XCircle size={11} strokeWidth={2.5} /> : null}
                {s.status === 'PENDING' ? <Clock size={11} strokeWidth={2.5} /> : null}
              </span>
              {!isLast ? (
                <span
                  aria-hidden
                  style={{
                    flex: 1,
                    width: 2,
                    background: token.colorBorderSecondary,
                    minHeight: 16,
                  }}
                />
              ) : null}
            </div>
            <div style={{ paddingBottom: isLast ? 0 : 4 }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <strong style={{ fontSize: 13 }}>
                  Stage {s.stageNo} · {s.stageRole}
                </strong>
                <StatusPill tone={tone}>{APPROVAL_STEP_STATUS_LABEL[s.status]}</StatusPill>
              </div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {assignedName}
                {actedByName ? ` · acted by ${actedByName}` : ''}
                {s.decidedAt ? ` · ${formatDateMonthDay(s.decidedAt)}` : ''}
              </Text>
              {s.note ? (
                <Text style={{ fontSize: 12, display: 'block', marginTop: 4 }}>{s.note}</Text>
              ) : null}
            </div>
          </div>
        );
      })}
    </div>
  );
}
