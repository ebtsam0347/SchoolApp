// Inbox table for pending short-leave requests (sub-day leave). One row
// per request; per-row Approve / Reject only — no batch operations.
//
// Sourced from the same `/bff/approvals/inbox` composite as leaves/
// overtime/regularization (see inbox.index.tsx) — the standalone
// `/short-leaves/pending-approvals` endpoint 404s (never shipped).

import { useState } from 'react';
import { useOpenRequestFromLink } from './deep-link';
import { useInboxSearch } from './use-inbox-search';
import { requestIdColumns } from './request-id-columns';
import { employeeSubtitle } from './employee-columns';
import { Alert, App as AntdApp, Button, Card, Space, Table, Tag, Typography } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Timer, XCircle } from 'lucide-react';
import {
  SHORT_LEAVE_STATUS_LABEL,
  SHORT_LEAVE_STATUS_TONE,
  formatDateShort,
  formatMinutes,
  shortLeavesApi,
  type ApprovalsInboxResponse,
  type EmployeeSummary,
  type ShortLeaveRequestDto,
} from '@/attendance';
import {
  ApproveRejectActions,
  FilterBar,
  RejectReasonModal,
  RequestState,
  StatusPill,
  personColumn,
} from '@/components';
import { getErrorMessage } from '@/lib';
import { ShortLeaveDecisionDrawer } from './ShortLeaveDecisionDrawer';

const { Text } = Typography;

export type ShortLeaveInboxRow = {
  request: ShortLeaveRequestDto;
  employee: EmployeeSummary;
};

export type ShortLeaveInboxProps = {
  rows: ShortLeaveInboxRow[];
  loading: boolean;
  error: unknown;
  /** Deep link (`/inbox?open=<id>`): open this request's drawer once loaded. */
  openId?: number;
  /** Called after `openId` has been opened. */
  onOpened?: () => void;
};

const INBOX_KEY = ['bff', 'approvals', 'inbox'] as const;

export function ShortLeaveInbox({ rows, loading, error, openId, onOpened }: ShortLeaveInboxProps) {
  const [search, setSearch] = useState('');
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [batchRejectOpen, setBatchRejectOpen] = useState(false);
  const [openRow, setOpenRow] = useState<ShortLeaveInboxRow | null>(null);
  useOpenRequestFromLink(rows, openId, setOpenRow, onOpened);
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  // Server-side search (`shortLeaveQ`) — see useInboxSearch.
  const searchResult = useInboxSearch(
    search,
    (shortLeaveQ) => ({ shortLeaveQ }),
    false,
    (d) => d.shortLeave,
  );
  const filteredRows = searchResult.active ? searchResult.rows : rows;

  const selected = filteredRows.filter((r) => selectedKeys.includes(r.request.id));

  const optimisticRemove = async (ids: number[]) => {
    await queryClient.cancelQueries({ queryKey: INBOX_KEY });
    const prev = queryClient.getQueryData<ApprovalsInboxResponse>(INBOX_KEY);
    if (prev) {
      const idSet = new Set(ids);
      const prevShortLeaves = prev.shortLeave ?? [];
      const removed = prevShortLeaves.filter((r) => idSet.has(r.request.id)).length;
      const next: ApprovalsInboxResponse = {
        ...prev,
        shortLeave: prevShortLeaves.filter((r) => !idSet.has(r.request.id)),
        counts: {
          ...prev.counts,
          shortLeave: Math.max(0, (prev.counts.shortLeave ?? prevShortLeaves.length) - removed),
        },
      };
      queryClient.setQueryData(INBOX_KEY, next);
    }
    return { prev };
  };

  const rollback = (ctx: { prev?: ApprovalsInboxResponse } | undefined) => {
    if (ctx?.prev) queryClient.setQueryData(INBOX_KEY, ctx.prev);
  };

  const settleBatch = () => {
    void queryClient.invalidateQueries({ queryKey: INBOX_KEY });
  };

  const approveBatchMutation = useMutation({
    mutationFn: async () => {
      await Promise.all(selected.map((r) => shortLeavesApi.approve(r.request.id)));
    },
    onMutate: () => optimisticRemove(selected.map((r) => r.request.id)),
    onSuccess: () => message.success(`Approved ${selected.length}`),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: () => {
      setSelectedKeys([]);
      settleBatch();
    },
  });

  const rejectBatchMutation = useMutation({
    mutationFn: async (reason: string) => {
      await Promise.all(selected.map((r) => shortLeavesApi.reject(r.request.id, { reason })));
    },
    onMutate: () => optimisticRemove(selected.map((r) => r.request.id)),
    onSuccess: () => message.success(`Rejected ${selected.length}`),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: () => {
      setSelectedKeys([]);
      settleBatch();
    },
  });

  return (
    <RequestState
      loading={loading && rows.length === 0}
      error={error}
      empty={!loading && rows.length === 0}
      emptyIcon={Timer}
      emptyTitle="No short leaves to review"
      emptyDescription="You're all caught up."
      errorTitle="Couldn't load pending short leaves"
    >
      {selected.length > 0 ? (
        <Card
          size="small"
          styles={{ body: { padding: '10px 14px' } }}
          style={{ marginBottom: 12 }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <Text style={{ fontSize: 13 }}>{selected.length} selected</Text>
            <Space>
              <Button onClick={() => setSelectedKeys([])}>Clear</Button>
              <Button
                danger
                icon={<XCircle size={12} />}
                onClick={() => setBatchRejectOpen(true)}
                loading={rejectBatchMutation.isPending}
              >
                Reject
              </Button>
              <Button
                type="primary"
                icon={<CheckCircle2 size={12} />}
                onClick={() => approveBatchMutation.mutate()}
                loading={approveBatchMutation.isPending}
              >
                Approve
              </Button>
            </Space>
          </div>
        </Card>
      ) : null}

      <FilterBar
        search={{
          value: search,
          onChange: setSearch,
          placeholder: 'Search request ID/code, employee name or code…',
        }}
        onReset={
          search
            ? () => {
                setSearch('');
              }
            : undefined
        }
        style={{ marginBottom: 12 }}
      />
      {searchResult.error ? (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          title="Search failed"
          description={getErrorMessage(searchResult.error)}
        />
      ) : null}
      <Table
        rowKey={(r) => r.request.id}
        loading={loading || searchResult.fetching}
        dataSource={filteredRows}
        size="middle"
        // Below this width the table scrolls inside itself (phones) rather
        // than dragging the page sideways; above it, columns wrap to fit.
        scroll={{ x: 1200 }}
        pagination={{
          defaultPageSize: 20,
          pageSizeOptions: [10, 20, 50, 100],
          showSizeChanger: true,
          showTotal: (total, [from, to]) => `${from}–${to} of ${total}`,
        }}
        rowSelection={{
          selectedRowKeys: selectedKeys,
          onChange: setSelectedKeys,
        }}
        columns={[
          ...requestIdColumns<ShortLeaveInboxRow>({ labelled: true }),
          {
            ...personColumn<ShortLeaveInboxRow>({
              title: 'Employee',
              // Fixed width: the ellipsis Reason column puts the table in
              // fixed layout, where unwidthed columns get squeezed.
              width: 240,
              dataIndex: (row) => ({
                fullName: row.employee.fullName,
                employeeCode: row.employee.employeeCode,
                profilePictureUrl: row.employee.profilePictureUrl,
                subtitle: employeeSubtitle(row.employee),
              }),
              to: (row) => `/people/${row.employee.id}`,
            }),
            sorter: (a: ShortLeaveInboxRow, b: ShortLeaveInboxRow) =>
              a.employee.fullName.localeCompare(b.employee.fullName),
          },
          {
            title: 'Status',
            key: 'status',
            width: 110,
            render: (_v, row) => (
              <StatusPill tone={SHORT_LEAVE_STATUS_TONE[row.request.status]}>
                {SHORT_LEAVE_STATUS_LABEL[row.request.status]}
              </StatusPill>
            ),
            sorter: (a, b) =>
              SHORT_LEAVE_STATUS_LABEL[a.request.status].localeCompare(
                SHORT_LEAVE_STATUS_LABEL[b.request.status],
              ),
          },
          {
            title: 'Date / Time away',
            key: 'leaveDate',
            width: 150,
            render: (_v, row) => (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                <span style={{ fontSize: 12 }}>{formatDateShort(row.request.leaveDate)}</span>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {row.request.startTime} – {row.request.endTime} ·{' '}
                  {formatMinutes(row.request.totalMinutes)}
                </Text>
              </div>
            ),
            sorter: (a, b) => a.request.leaveDate.localeCompare(b.request.leaveDate),
            defaultSortOrder: 'descend',
          },
          {
            title: 'Reason',
            key: 'reason',
            render: (_v, row) => (
              <span style={{ fontSize: 12 }}>{row.request.reason || '—'}</span>
            ),
          },
          {
            title: 'Stage',
            key: 'stage',
            width: 150,
            render: (_v, row) =>
              row.request.currentStage ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <Tag style={{ marginRight: 0, width: 'fit-content' }}>
                    {row.request.currentStage.stageRole}
                  </Tag>
                  {row.request.currentStage.assignedToName ? (
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {row.request.currentStage.assignedToName}
                    </Text>
                  ) : null}
                </div>
              ) : (
                <Text type="secondary" style={{ fontSize: 11 }}>
                  —
                </Text>
              ),
          },
          {
            title: '',
            key: 'actions',
            width: 280,
            align: 'right',
            render: (_v, row) => (
              <ShortLeaveRowActions row={row} onOpen={() => setOpenRow(row)} />
            ),
          },
        ]}
      />
      <RejectReasonModal
        open={batchRejectOpen}
        title={`Reject ${selected.length} short leave(s)?`}
        onCancel={() => setBatchRejectOpen(false)}
        onConfirm={async (reason) => {
          await rejectBatchMutation.mutateAsync(reason);
          setBatchRejectOpen(false);
        }}
      />
      <ShortLeaveDecisionDrawer row={openRow} onClose={() => setOpenRow(null)} />
    </RequestState>
  );
}

function ShortLeaveRowActions({
  row,
  onOpen,
}: {
  row: ShortLeaveInboxRow;
  onOpen: () => void;
}) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const req = row.request;

  const onMutateRow = async () => {
    await queryClient.cancelQueries({ queryKey: INBOX_KEY });
    const prev = queryClient.getQueryData<ApprovalsInboxResponse>(INBOX_KEY);
    if (prev) {
      const prevShortLeaves = prev.shortLeave ?? [];
      const next: ApprovalsInboxResponse = {
        ...prev,
        shortLeave: prevShortLeaves.filter((r) => r.request.id !== req.id),
        counts: {
          ...prev.counts,
          shortLeave: Math.max(0, (prev.counts.shortLeave ?? prevShortLeaves.length) - 1),
        },
      };
      queryClient.setQueryData(INBOX_KEY, next);
    }
    return { prev };
  };

  const rollback = (ctx: { prev?: ApprovalsInboxResponse } | undefined) => {
    if (ctx?.prev) queryClient.setQueryData(INBOX_KEY, ctx.prev);
  };

  const settle = () => {
    void queryClient.invalidateQueries({ queryKey: INBOX_KEY });
  };

  const approveMutation = useMutation({
    mutationFn: () => shortLeavesApi.approve(req.id),
    onMutate: onMutateRow,
    onSuccess: () => message.success('Approved'),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: settle,
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => shortLeavesApi.reject(req.id, { reason }),
    onMutate: onMutateRow,
    onSuccess: () => message.success('Rejected'),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: settle,
  });

  return (
    <ApproveRejectActions
      actions={req.actions}
      onApprove={async () => {
        await approveMutation.mutateAsync();
      }}
      onReject={async (reason) => {
        await rejectMutation.mutateAsync(reason);
      }}
      rejectTitle="Reject this short leave?"
      rejectDescription="The request will be rejected at your stage. The submitter is notified."
      leading={
        <Button size="small" onClick={onOpen}>
          Details
        </Button>
      }
    />
  );
}
