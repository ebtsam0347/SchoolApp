// Compact summary block at the top of an inbox decision drawer:
// employee header, key facts (type / range / days), reason, approval
// chain, then activity timeline. Reused by every drawer (leaves now,
// overtime + regularization once they ship per-row drawers).

import { Card, Typography } from 'antd';
import {
  RequestTimeline,
  formatDateShort,
  type EmployeeSummary,
  type LeaveRequestDto,
  type RequestTimelineKind,
} from '@/attendance';
import { KvList, Section } from '@/components';
import { EmployeeCell } from './EmployeeCell';
import { ApprovalSteps } from './ApprovalSteps';

const { Text } = Typography;

export type RowSummaryProps = {
  employee: EmployeeSummary;
  title: string;
  rangeFrom: string;
  rangeTo: string;
  days?: number;
  reason: string | null;
  steps: LeaveRequestDto['steps'];
  /** Omit to hide the Activity section — e.g. short leave, which isn't
   *  wired into the `/bff/timeline/{kind}/{id}` composite yet. */
  timelineKind?: RequestTimelineKind;
  requestId: number;
};

export function RowSummary({
  employee,
  title,
  rangeFrom,
  rangeTo,
  days,
  reason,
  steps,
  timelineKind,
  requestId,
}: RowSummaryProps) {
  const factItems = [
    { label: 'Type', value: title },
    ...(days != null ? [{ label: 'Days', value: `${days}` }] : []),
    { label: 'From', value: formatDateShort(rangeFrom) },
    { label: 'To', value: formatDateShort(rangeTo) },
  ];

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 14,
        marginBottom: 'var(--app-space-md)',
      }}
    >
      <Card size="small" styles={{ body: { padding: 14 } }}>
        <EmployeeCell employee={employee} size="default" />
        <div style={{ marginTop: 12 }}>
          <KvList items={factItems} layout="grid" minColumnWidth={120} gap={10} size="sm" />
        </div>
        {/* Always rendered so an empty reason reads as "none given" rather
            than as a missing field. */}
        <div style={{ marginTop: 'var(--app-space-sm)' }}>
          <Text
            type="secondary"
            style={{
              fontSize: 11,
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
            }}
          >
            Reason
          </Text>
          <div style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>
            {reason?.trim() ? reason : <Text type="secondary">No reason given</Text>}
          </div>
        </div>
      </Card>
      <Section title="Approval chain" level={5}>
        <ApprovalSteps steps={steps} requesterId={employee.id} />
      </Section>
      {timelineKind ? (
        <Section title="Activity" level={5}>
          <RequestTimeline kind={timelineKind} requestId={requestId} />
        </Section>
      ) : null}
    </div>
  );
}
