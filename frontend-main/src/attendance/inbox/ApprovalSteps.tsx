// Approval-stage chips arranged in a responsive grid. Used inside the
// inbox decision drawer to show the current shape of the approval chain.
// Note: the timeline-style stepper used on the /time/leaves detail drawer
// is a different component (see attendance/leaves/ApprovalStepsTimeline).

import { Card, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import {
  APPROVAL_STEP_STATUS_LABEL,
  APPROVAL_STEP_STATUS_TONE,
  type LeaveRequestDto,
} from '@/attendance';
import { StatusPill } from '@/components';
import { peopleBff } from '@/people';

const { Text } = Typography;

export type ApprovalStepsProps = {
  steps: LeaveRequestDto['steps'];
  /**
   * The requester's employee id. Used only as a fallback when a MANAGER /
   * DEPT_HEAD step's `assignedToName` comes back null from the backend
   * (it should normally be populated) — we resolve the requester's
   * *current* manager off the org chart instead of showing a bare auth
   * user id. Best-effort: if the manager has changed since the request was
   * submitted, this may not match who actually decided that step.
   */
  requesterId?: number;
};

export function ApprovalSteps({ steps, requesterId }: ApprovalStepsProps) {
  const needsManagerFallback = steps.some(
    (s) => !s.assignedToName && (s.stageRole === 'MANAGER' || s.stageRole === 'DEPT_HEAD'),
  );
  const managerQuery = useQuery({
    queryKey: ['people', 'detail', requesterId],
    queryFn: ({ signal }) => peopleBff.detail(requesterId!, signal),
    enabled: needsManagerFallback && !!requesterId,
    staleTime: 5 * 60_000,
  });
  const manager = managerQuery.data?.resolved?.manager;

  // Role-only stages (HR, Accounts, …) always read as "{role} role" —
  // consistent whether or not that stage has been decided yet — since we
  // have no reliable way to tie the specific staff member back to a name
  // from the frontend. Only MANAGER/DEPT_HEAD get a real person's name,
  // via the requester's org-chart manager above.
  const nameFor = (s: LeaveRequestDto['steps'][number]) => {
    if (s.assignedToName) return s.assignedToName;
    if ((s.stageRole === 'MANAGER' || s.stageRole === 'DEPT_HEAD') && manager?.fullName) {
      return manager.employeeCode ? `${manager.fullName} · ${manager.employeeCode}` : manager.fullName;
    }
    return `${s.stageRole} role`;
  };

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 'var(--app-space-sm)',
      }}
    >
      {steps.map((s) => (
        <Card key={s.stageNo} size="small" styles={{ body: { padding: 12 } }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <strong style={{ fontSize: 12 }}>
              {s.stageNo}. {s.stageRole}
            </strong>
            <StatusPill tone={APPROVAL_STEP_STATUS_TONE[s.status]}>
              {APPROVAL_STEP_STATUS_LABEL[s.status]}
            </StatusPill>
          </div>
          <Text
            type="secondary"
            style={{ fontSize: 11, display: 'block', marginTop: 4 }}
          >
            {nameFor(s)}
          </Text>
        </Card>
      ))}
    </div>
  );
}
