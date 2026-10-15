// Drawer that opens when the user clicks a row in their leaves table.
//
// Shows the request facts + approval timeline + per-day breakdown +
// activity feed. Cancel button at the bottom is gated on the server's
// `actions[]` (CANCEL when still cancellable) — and only on my own list
// (`allowCancel`); the team / department lists open it read-only, with the
// employee it's for at the top.
//
// Opened from a table row, the drawer gets that row as `initial` and skips
// `GET /leaves/{id}` — the list already carries the full request. Opened
// from a link (`?detail=<id>`), it fetches.

import {
  App as AntdApp,
  Button,
  Card,
  Empty,
  Popconfirm,
  Table,
  Tag,
  Typography,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { XCircle } from 'lucide-react';
import {
  LEAVE_DAY_STATUS_LABEL,
  LEAVE_DAY_STATUS_TONE,
  LEAVE_STATUS_LABEL,
  LEAVE_STATUS_TONE,
  RequestTimeline,
  formatDateShort,
  formatDateMonthDay,
  leavesApi,
  prettyLeaveCode,
  type LeaveRequestDto,
} from '@/attendance';
import { DetailDrawer, KvList, PersonCell, Section, StatusPill } from '@/components';
import { getErrorMessage } from '@/lib';
import { ApprovalStepsTimeline } from './ApprovalStepsTimeline';

const { Text } = Typography;

export type LeaveDetailDrawerProps = {
  leaveId: number | null;
  onClose: () => void;
  /** The request, when the caller already has it (a table row) — skips the fetch. */
  initial?: LeaveRequestDto | null;
  /** Offer Cancel when the server allows it. Off for someone else's leave. */
  allowCancel?: boolean;
  /** Who the leave is for — shown at the top (team / department lists). */
  employee?: {
    fullName: string;
    employeeCode?: string | null;
    profilePictureUrl?: string | null;
  } | null;
};

export function LeaveDetailDrawer({
  leaveId,
  onClose,
  initial,
  allowCancel = true,
  employee,
}: LeaveDetailDrawerProps) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const open = leaveId != null;
  const seeded = initial != null && initial.id === leaveId ? initial : null;

  const requestQuery = useQuery({
    queryKey: ['leaves', 'detail', leaveId],
    queryFn: ({ signal }) => leavesApi.get(leaveId!, signal),
    enabled: open && !seeded,
  });
  const daysQuery = useQuery({
    queryKey: ['leaves', 'days', leaveId],
    queryFn: ({ signal }) => leavesApi.days(leaveId!, signal),
    enabled: open,
  });

  const cancelMutation = useMutation({
    mutationFn: () => leavesApi.cancel(leaveId!),
    onSuccess: () => {
      message.success('Leave cancelled');
      queryClient.invalidateQueries({ queryKey: ['leaves'] });
      onClose();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const req = seeded ?? requestQuery.data;
  const days = daysQuery.data ?? [];

  // T16: actions[] is computed server-side against the caller and includes
  // CANCEL while the request is cancellable. No client-side rules needed.
  const cancellable = allowCancel && !!req && req.actions.includes('CANCEL');

  return (
    <DetailDrawer
      title={req ? `Leave · ${req.requestCode}` : 'Leave'}
      open={open}
      onClose={onClose}
      loading={!seeded && requestQuery.isLoading}
      error={!seeded && requestQuery.isError ? requestQuery.error : undefined}
      empty={!req && !requestQuery.isLoading}
      emptyTitle="Leave not found"
      emptyDescription="It may have been deleted, or the link is wrong."
      errorTitle="Couldn't load leave"
      footer={
        cancellable ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Popconfirm
              title="Cancel this leave?"
              description="The whole request will be cancelled and your balance released."
              okText="Yes, cancel"
              okButtonProps={{ danger: true }}
              onConfirm={() => cancelMutation.mutate()}
            >
              <Button danger icon={<XCircle size={14} />} loading={cancelMutation.isPending}>
                Cancel leave
              </Button>
            </Popconfirm>
          </div>
        ) : undefined
      }
    >
      {req ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          {employee ? (
            <PersonCell
              person={{
                fullName: employee.fullName,
                employeeCode: employee.employeeCode ?? null,
                profilePictureUrl: employee.profilePictureUrl ?? undefined,
              }}
            />
          ) : null}
          <Card size="small" styles={{ body: { padding: 14 } }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: 'var(--app-space-sm)',
              }}
            >
              <strong style={{ fontSize: 14 }}>{prettyLeaveCode(req.leaveTypeCode)}</strong>
              <StatusPill tone={LEAVE_STATUS_TONE[req.status]} size="md">
                {LEAVE_STATUS_LABEL[req.status]}
              </StatusPill>
            </div>
            <KvList
              items={[
                { label: 'From', value: formatDateShort(req.fromDate) },
                { label: 'To', value: formatDateShort(req.toDate) },
                {
                  label: 'Days',
                  value: `${req.days}${req.halfDay ? ` (½ ${req.halfDayPart ?? ''})` : ''}`,
                },
                { label: 'Submitted', value: formatDateShort(req.createdAt) },
              ]}
              minColumnWidth={120}
              gap={10}
              size="sm"
            />
            {req.reason ? (
              <div style={{ marginTop: 'var(--app-space-sm)', fontSize: 13 }}>
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
                <div>{req.reason}</div>
              </div>
            ) : null}
          </Card>

          <Section title="Approval" level={5}>
            <ApprovalStepsTimeline steps={req.steps} />
          </Section>

          <Section title="Activity" level={5}>
            <RequestTimeline kind="leave" requestId={req.id} />
          </Section>

          <Section title="Per-day breakdown" level={5}>
            {days.length === 0 ? (
              <Empty description="No day rows" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <Table
                rowKey="id"
                dataSource={days}
                size="small"
                pagination={false}
                // Scroll wide columns inside the table, not the drawer.
                scroll={{ x: 'max-content' }}
                columns={[
                  {
                    title: 'Date',
                    dataIndex: 'workDate',
                    key: 'workDate',
                    render: (d: string) => formatDateMonthDay(d),
                  },
                  {
                    title: 'Status',
                    dataIndex: 'status',
                    key: 'status',
                    width: 110,
                    render: (s: keyof typeof LEAVE_DAY_STATUS_LABEL) => (
                      <StatusPill tone={LEAVE_DAY_STATUS_TONE[s]}>
                        {LEAVE_DAY_STATUS_LABEL[s]}
                      </StatusPill>
                    ),
                  },
                  {
                    title: 'Decided at',
                    dataIndex: 'decidedAtStage',
                    key: 'decidedAtStage',
                    width: 140,
                    render: (st: number | null) =>
                      st != null ? (
                        <Tag>Stage {st}</Tag>
                      ) : (
                        <Text type="secondary" style={{ fontSize: 11 }}>
                          —
                        </Text>
                      ),
                  },
                  {
                    title: 'Note',
                    dataIndex: 'rejectionReason',
                    key: 'rejectionReason',
                    render: (r: string | null) =>
                      r ? <span style={{ fontSize: 12 }}>{r}</span> : '—',
                  },
                ]}
              />
            )}
          </Section>

        </div>
      ) : null}
    </DetailDrawer>
  );
}
