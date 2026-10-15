// Drawer that opens when a manager clicks "Details" on a pending leave row.
//
// Two modes:
//   - read-only summary (any approver) — RowSummary + approval chain
//   - per-day picker (final-stage only) — pick APPROVE / REJECT for every
//     day in the request, submit as a batch via leavesApi.decideDays
//
// The per-day mode is gated by the `DECIDE_PER_DAY` action in the row's
// actions[] (computed server-side).

import { useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Drawer,
  Empty,
  Segmented,
  Space,
  Typography,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  formatDateShort,
  generateDateRange,
  leavesApi,
  prettyLeaveCode,
  type ApprovalInboxLeaveRow,
} from '@/attendance';
import { Section } from '@/components';
import { getErrorMessage, useIsDesktop } from '@/lib';
import { RowSummary } from './RowSummary';

const { Text } = Typography;

export type LeaveDecisionDrawerProps = {
  row: ApprovalInboxLeaveRow | null;
  onClose: () => void;
};

export function LeaveDecisionDrawer({ row, onClose }: LeaveDecisionDrawerProps) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const open = !!row;

  const decideMutation = useMutation({
    mutationFn: (
      decisions: { workDate: string; action: 'APPROVE' | 'REJECT'; reason?: string }[],
    ) => leavesApi.decideDays(row?.request.id ?? 0, { decisions }),
    onSuccess: () => {
      message.success('Per-day decisions saved');
      queryClient.invalidateQueries({ queryKey: ['bff', 'approvals', 'inbox'] });
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      onClose();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const [picks, setPicks] = useState<Record<string, 'APPROVE' | 'REJECT' | undefined>>({});
  // 560px is a desktop width; a phone needs the full viewport. Declared
  // before the `if (!row)` early return so the hook order stays stable.
  const isDesktop = useIsDesktop();

  if (!row) {
    return (
      <Drawer open={open} onClose={onClose} title="Leave" size={520} destroyOnHidden>
        <Empty description="No selection" />
      </Drawer>
    );
  }

  const req = row.request;
  const days = generateDateRange(req.fromDate, req.toDate);
  const allPicked = days.every((d) => picks[d]);

  return (
    <Drawer
      title={`Leave #${req.id} · ${prettyLeaveCode(req.leaveTypeCode)}`}
      open={open}
      onClose={onClose}
      size={isDesktop ? 560 : '100%'}
      destroyOnHidden
      footer={
        req.actions.includes('DECIDE_PER_DAY') ? (
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: 'var(--app-space-sm)',
            }}
          >
            <Text type="secondary" style={{ fontSize: 12 }}>
              {Object.values(picks).filter(Boolean).length}/{days.length} day(s) decided
            </Text>
            <Space>
              <Button onClick={onClose}>Cancel</Button>
              <Button
                type="primary"
                disabled={!allPicked}
                loading={decideMutation.isPending}
                onClick={() =>
                  decideMutation.mutate(days.map((d) => ({ workDate: d, action: picks[d]! })))
                }
              >
                Submit decisions
              </Button>
            </Space>
          </div>
        ) : null
      }
    >
      <RowSummary
        employee={row.employee}
        title={prettyLeaveCode(req.leaveTypeCode)}
        rangeFrom={req.fromDate}
        rangeTo={req.toDate}
        days={req.days}
        reason={req.reason}
        steps={req.steps}
        timelineKind="leave"
        requestId={req.id}
      />
      {req.actions.includes('DECIDE_PER_DAY') ? (
        <Section title="Decide per day" level={5}>
          <Alert
            type="info"
            showIcon
            title="Final-stage only"
            description="Earlier stages can only approve or reject the whole request. Pick approve / reject for every day to submit."
            style={{ marginBottom: 12 }}
          />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--app-space-sm)' }}>
            {days.map((d) => (
              <PerDayRow
                key={d}
                workDate={d}
                value={picks[d]}
                onChange={(v) => setPicks((prev) => ({ ...prev, [d]: v }))}
              />
            ))}
          </div>
        </Section>
      ) : null}
    </Drawer>
  );
}

function PerDayRow({
  workDate,
  value,
  onChange,
}: {
  workDate: string;
  value: 'APPROVE' | 'REJECT' | undefined;
  onChange: (v: 'APPROVE' | 'REJECT') => void;
}) {
  return (
    <Card size="small" styles={{ body: { padding: 12 } }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
        }}
      >
        <Text style={{ fontWeight: 500 }}>{formatDateShort(workDate)}</Text>
        <Segmented
          value={value ?? ''}
          onChange={(v) => onChange(v as 'APPROVE' | 'REJECT')}
          options={[
            { value: 'APPROVE', label: 'Approve' },
            { value: 'REJECT', label: 'Reject' },
          ]}
        />
      </div>
    </Card>
  );
}
