// Inbox table for pending leave requests. One row per request, with the
// per-row Approve / Reject (+ Per-day) actions. The decision drawer
// handles deeper review and final-stage per-day submission.

import { useState } from 'react';
import { useOpenRequestFromLink } from './deep-link';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarRange, CheckCircle2, Plane, XCircle } from 'lucide-react';
import { formatDateMonthDay, formatDateShort } from '@/lib';
import {
  leavesApi,
  prettyLeaveCode,
  type ApprovalInboxLeaveRow,
  LEAVE_STATUS_LABEL,
  LEAVE_STATUS_TONE,
  type ApprovalsInboxResponse,
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
import { LeaveDecisionDrawer } from './LeaveDecisionDrawer';
import { useInboxSearch } from './use-inbox-search';
import { requestIdColumns } from './request-id-columns';
import { employeeSubtitle } from './employee-columns';

const { Text } = Typography;

const INBOX_KEY = ['bff', 'approvals', 'inbox'] as const;

export type LeavesInboxProps = {
  rows: ApprovalInboxLeaveRow[];
  loading: boolean;
  error: unknown;
  /** Deep link (`/inbox?open=<id>`): open this request's drawer once loaded. */
  openId?: number;
  /** Called after `openId` has been opened. */
  onOpened?: () => void;
};

export function LeavesInbox({ rows, loading, error, openId, onOpened }: LeavesInboxProps) {
  const [openRow, setOpenRow] = useState<ApprovalInboxLeaveRow | null>(null);
  useOpenRequestFromLink(rows, openId, setOpenRow, onOpened);
  const [search, setSearch] = useState('');
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [batchRejectOpen, setBatchRejectOpen] = useState(false);
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  // Server-side search (`leaveQ` + `leaveTypeId`) — see useInboxSearch.
  const [leaveTypeId, setLeaveTypeId] = useState<number | undefined>(undefined);
  const searchResult = useInboxSearch(
    search,
    (leaveQ) => ({ leaveQ, leaveTypeId }),
    leaveTypeId != null,
    (d) => d.leaves,
  );

  // Active leave types for the Type filter. Labelled the same way as the
  // table's Type column so the dropdown and the rows read identically.
  const leaveTypesQuery = useQuery({
    queryKey: ['leaves', 'types', 'active'],
    queryFn: ({ signal }) => leavesApi.types({ signal }),
    select: (types) =>
      types
        .filter((t) => t.active)
        .map((t) => ({ value: t.id, label: prettyLeaveCode(t.code) }))
        .sort((a, b) => a.label.localeCompare(b.label)),
    staleTime: 5 * 60_000,
  });
  const filteredRows = searchResult.active ? searchResult.rows : rows;

  const selected = filteredRows.filter((r) => selectedKeys.includes(r.request.id));

  const optimisticRemove = async (ids: number[]) => {
    await queryClient.cancelQueries({ queryKey: INBOX_KEY });
    const prev = queryClient.getQueryData<ApprovalsInboxResponse>(INBOX_KEY);
    if (prev) {
      const idSet = new Set(ids);
      const removed = prev.leaves.filter((r) => idSet.has(r.request.id)).length;
      const next: ApprovalsInboxResponse = {
        ...prev,
        leaves: prev.leaves.filter((r) => !idSet.has(r.request.id)),
        counts: {
          ...prev.counts,
          leaves: Math.max(0, prev.counts.leaves - removed),
          total: Math.max(0, prev.counts.total - removed),
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
    void queryClient.invalidateQueries({ queryKey: ['leaves'] });
  };

  const approveBatchMutation = useMutation({
    mutationFn: async () => {
      await Promise.all(selected.map((r) => leavesApi.approve(r.request.id)));
    },
    onMutate: () => optimisticRemove(selected.map((r) => r.request.id)),
    onSuccess: () => message.success(`Approved ${selected.length}`),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: () => {
      setSelectedKeys([]);
      settle();
    },
  });

  const rejectBatchMutation = useMutation({
    mutationFn: async (reason: string) => {
      await Promise.all(selected.map((r) => leavesApi.reject(r.request.id, { reason })));
    },
    onMutate: () => optimisticRemove(selected.map((r) => r.request.id)),
    onSuccess: () => message.success(`Rejected ${selected.length}`),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: () => {
      setSelectedKeys([]);
      settle();
    },
  });

  return (
    <RequestState
      loading={loading && rows.length === 0}
      error={error}
      empty={!loading && rows.length === 0}
      emptyIcon={Plane}
      emptyTitle="No leaves to review"
      emptyDescription="You're all caught up."
      errorTitle="Couldn't load pending leaves"
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
        filters={
          <Select<number>
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="All leave types"
            style={{ width: 180 }}
            value={leaveTypeId}
            onChange={(v) => setLeaveTypeId(v ?? undefined)}
            options={leaveTypesQuery.data ?? []}
            loading={leaveTypesQuery.isLoading}
          />
        }
        onReset={
          search || leaveTypeId != null
            ? () => {
                setSearch('');
                setLeaveTypeId(undefined);
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
          ...requestIdColumns<ApprovalInboxLeaveRow>({ labelled: true }),
          {
            ...personColumn<ApprovalInboxLeaveRow>({
              title: 'Employee',
              dataIndex: (row) => ({
                fullName: row.employee.fullName,
                employeeCode: row.employee.employeeCode,
                profilePictureUrl: row.employee.profilePictureUrl,
                subtitle: employeeSubtitle(row.employee),
              }),
              to: (row) => `/people/${row.employee.id}`,
            }),
            sorter: (a: ApprovalInboxLeaveRow, b: ApprovalInboxLeaveRow) =>
              a.employee.fullName.localeCompare(b.employee.fullName),
          },
          {
            title: 'Status',
            key: 'status',
            render: (_v, row) => (
              <StatusPill tone={LEAVE_STATUS_TONE[row.request.status]}>
                {LEAVE_STATUS_LABEL[row.request.status]}
              </StatusPill>
            ),
            sorter: (a, b) =>
              LEAVE_STATUS_LABEL[a.request.status].localeCompare(
                LEAVE_STATUS_LABEL[b.request.status],
              ),
          },
          {
            title: 'Type',
            key: 'leaveTypeCode',
            width: 130,
            render: (_v, row) => prettyLeaveCode(row.request.leaveTypeCode),
            sorter: (a, b) =>
              prettyLeaveCode(a.request.leaveTypeCode).localeCompare(
                prettyLeaveCode(b.request.leaveTypeCode),
              ),
          },
          {
            title: 'Range',
            key: 'range',
            width: 250,
            render: (_v, row) => (
              <span style={{ fontSize: 12 }}>
                {formatDateShort(row.request.fromDate)}
                {row.request.fromDate !== row.request.toDate
                  ? ` → ${formatDateShort(row.request.toDate)}`
                  : ''}
                <Text type="secondary" style={{ marginLeft: 8 }}>
                  · {row.request.days}d
                </Text>
              </span>
            ),
            sorter: (a, b) => a.request.fromDate.localeCompare(b.request.fromDate),
          },
          {
            title: 'Reason',
            key: 'reason',
            render: (_v, row) => (
              <span style={{ fontSize: 12 }}>{row.request.reason || '—'}</span>
            ),
          },
          {
            title: 'Submitted',
            key: 'createdAt',
            width: 110,
            render: (_v, row) => (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {formatDateMonthDay(row.request.createdAt)}
              </Text>
            ),
            sorter: (a, b) => a.request.createdAt.localeCompare(b.request.createdAt),
            defaultSortOrder: 'descend',
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
            width: 320,
            align: 'right',
            render: (_v, row) => (
              <LeaveRowActions row={row} onOpen={() => setOpenRow(row)} />
            ),
          },
        ]}
      />
      <LeaveDecisionDrawer row={openRow} onClose={() => setOpenRow(null)} />
      <RejectReasonModal
        open={batchRejectOpen}
        title={`Reject ${selected.length} leave(s)?`}
        onCancel={() => setBatchRejectOpen(false)}
        onConfirm={async (reason) => {
          await rejectBatchMutation.mutateAsync(reason);
          setBatchRejectOpen(false);
        }}
      />
    </RequestState>
  );
}

function LeaveRowActions({
  row,
  onOpen,
}: {
  row: ApprovalInboxLeaveRow;
  onOpen: () => void;
}) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const req = row.request;
  const canPerDay = req.actions.includes('DECIDE_PER_DAY');

  const onMutateRow = async () => {
    await queryClient.cancelQueries({ queryKey: INBOX_KEY });
    const prev = queryClient.getQueryData<ApprovalsInboxResponse>(INBOX_KEY);
    if (prev) {
      const next: ApprovalsInboxResponse = {
        ...prev,
        leaves: prev.leaves.filter((r) => r.request.id !== req.id),
        counts: {
          ...prev.counts,
          leaves: Math.max(0, prev.counts.leaves - 1),
          total: Math.max(0, prev.counts.total - 1),
        },
      };
      queryClient.setQueryData(INBOX_KEY, next);
    }
    return { prev };
  };

  const rollback = (ctx: { prev?: ApprovalsInboxResponse } | undefined) => {
    if (ctx?.prev) queryClient.setQueryData(INBOX_KEY, ctx.prev);
  };

  const approveMutation = useMutation({
    mutationFn: () => leavesApi.approve(req.id),
    onMutate: onMutateRow,
    onSuccess: () => message.success('Approved at your stage'),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: INBOX_KEY });
      void queryClient.invalidateQueries({ queryKey: ['leaves'] });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => leavesApi.reject(req.id, { reason }),
    onMutate: onMutateRow,
    onSuccess: () => message.success('Rejected'),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: INBOX_KEY });
      void queryClient.invalidateQueries({ queryKey: ['leaves'] });
    },
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
      rejectTitle="Reject this leave?"
      rejectDescription="The whole request will be rejected at your stage. The submitter is notified."
      leading={
        <>
          <Button size="small" onClick={onOpen}>
            Details
          </Button>
          {canPerDay ? (
            <Tooltip title="Decide each day separately (final stage only)">
              <Button size="small" icon={<CalendarRange size={12} />} onClick={onOpen}>
                Per-day
              </Button>
            </Tooltip>
          ) : null}
        </>
      }
    />
  );
}
