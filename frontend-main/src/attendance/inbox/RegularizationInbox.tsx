// Inbox table for pending regularization requests (correcting punch-in /
// punch-out times). One row per request; per-row Approve / Reject only —
// no batch operations because regularizations are individual corrections.

import { useState } from 'react';
import { useOpenRequestFromLink } from './deep-link';
import { useInboxSearch } from './use-inbox-search';
import { requestIdColumns } from './request-id-columns';
import { employeeSubtitle } from './employee-columns';
import { Alert, App as AntdApp, Button, Card, Space, Table, Tag, Typography } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Stamp, XCircle } from 'lucide-react';
import {
  REGULARIZATION_STATUS_LABEL,
  REGULARIZATION_STATUS_TONE,
  formatClock,
  formatDateShort,
  regularizationsApi,
  type ApprovalInboxRegularizationRow,
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
import { RegularizationDecisionDrawer } from './RegularizationDecisionDrawer';

const { Text } = Typography;

const INBOX_KEY = ['bff', 'approvals', 'inbox'] as const;

export type RegularizationInboxProps = {
  rows: ApprovalInboxRegularizationRow[];
  loading: boolean;
  error: unknown;
  /** Deep link (`/inbox?open=<id>`): open this request's drawer once loaded. */
  openId?: number;
  /** Called after `openId` has been opened. */
  onOpened?: () => void;
};

export function RegularizationInbox({ rows, loading, error, openId, onOpened }: RegularizationInboxProps) {
  const [search, setSearch] = useState('');
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [batchRejectOpen, setBatchRejectOpen] = useState(false);
  const [openRow, setOpenRow] = useState<ApprovalInboxRegularizationRow | null>(null);
  useOpenRequestFromLink(rows, openId, setOpenRow, onOpened);
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  // Server-side search (`regularizationQ`) — see useInboxSearch.
  const searchResult = useInboxSearch(
    search,
    (regularizationQ) => ({ regularizationQ }),
    false,
    (d) => d.regularization,
  );
  const filteredRows = searchResult.active ? searchResult.rows : rows;

  const selected = filteredRows.filter((r) => selectedKeys.includes(r.request.id));

  const optimisticRemove = async (ids: number[]) => {
    await queryClient.cancelQueries({ queryKey: INBOX_KEY });
    const prev = queryClient.getQueryData<ApprovalsInboxResponse>(INBOX_KEY);
    if (prev) {
      const idSet = new Set(ids);
      const removed = prev.regularization.filter((r) => idSet.has(r.request.id)).length;
      const next: ApprovalsInboxResponse = {
        ...prev,
        regularization: prev.regularization.filter((r) => !idSet.has(r.request.id)),
        counts: {
          ...prev.counts,
          regularization: Math.max(0, prev.counts.regularization - removed),
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
    void queryClient.invalidateQueries({ queryKey: ['regularizations'] });
  };

  const approveBatchMutation = useMutation({
    mutationFn: async () => {
      await Promise.all(selected.map((r) => regularizationsApi.approve(r.request.id)));
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
      await Promise.all(
        selected.map((r) => regularizationsApi.reject(r.request.id, { reason })),
      );
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
      emptyIcon={Stamp}
      emptyTitle="No regularizations to review"
      emptyDescription="You're all caught up."
      errorTitle="Couldn't load regularizations"
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
          ...requestIdColumns<ApprovalInboxRegularizationRow>({ labelled: true }),
          {
            ...personColumn<ApprovalInboxRegularizationRow>({
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
            sorter: (a: ApprovalInboxRegularizationRow, b: ApprovalInboxRegularizationRow) =>
              a.employee.fullName.localeCompare(b.employee.fullName),
          },
          {
            title: 'Status',
            key: 'status',
            width: 110,
            render: (_v, row) => (
              <StatusPill tone={REGULARIZATION_STATUS_TONE[row.request.status]}>
                {REGULARIZATION_STATUS_LABEL[row.request.status]}
              </StatusPill>
            ),
            sorter: (a, b) =>
              REGULARIZATION_STATUS_LABEL[a.request.status].localeCompare(
                REGULARIZATION_STATUS_LABEL[b.request.status],
              ),
          },
          {
            title: 'Date',
            key: 'workDate',
            width: 120,
            render: (_v, row) => formatDateShort(row.request.workDate),
            sorter: (a, b) => a.request.workDate.localeCompare(b.request.workDate),
            defaultSortOrder: 'descend',
          },
          {
            title: 'Times',
            key: 'times',
            width: 200,
            render: (_v, row) => (
              <span style={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}>
                {row.request.requestedCheckIn
                  ? `IN ${formatClock(row.request.requestedCheckIn)}`
                  : ''}
                {row.request.requestedCheckIn && row.request.requestedCheckOut ? ' · ' : ''}
                {row.request.requestedCheckOut
                  ? `OUT ${formatClock(row.request.requestedCheckOut)}`
                  : ''}
              </span>
            ),
          },
          {
            title: 'Reason',
            key: 'reason',
            ellipsis: true,
            render: (_v, row) => row.request.reason,
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
              <RegularizationRowActions row={row} onOpen={() => setOpenRow(row)} />
            ),
          },
        ]}
      />
      <RejectReasonModal
        open={batchRejectOpen}
        title={`Reject ${selected.length} regularization(s)?`}
        onCancel={() => setBatchRejectOpen(false)}
        onConfirm={async (reason) => {
          await rejectBatchMutation.mutateAsync(reason);
          setBatchRejectOpen(false);
        }}
      />
      <RegularizationDecisionDrawer row={openRow} onClose={() => setOpenRow(null)} />
    </RequestState>
  );
}

function RegularizationRowActions({
  row,
  onOpen,
}: {
  row: ApprovalInboxRegularizationRow;
  onOpen: () => void;
}) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const req = row.request;

  const onMutateRow = async () => {
    await queryClient.cancelQueries({ queryKey: INBOX_KEY });
    const prev = queryClient.getQueryData<ApprovalsInboxResponse>(INBOX_KEY);
    if (prev) {
      const next: ApprovalsInboxResponse = {
        ...prev,
        regularization: prev.regularization.filter((r) => r.request.id !== req.id),
        counts: {
          ...prev.counts,
          regularization: Math.max(0, prev.counts.regularization - 1),
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
    mutationFn: () => regularizationsApi.approve(req.id),
    onMutate: onMutateRow,
    onSuccess: () => message.success('Approved'),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: INBOX_KEY });
      void queryClient.invalidateQueries({ queryKey: ['regularizations'] });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => regularizationsApi.reject(req.id, { reason }),
    onMutate: onMutateRow,
    onSuccess: () => message.success('Rejected'),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: INBOX_KEY });
      void queryClient.invalidateQueries({ queryKey: ['regularizations'] });
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
      leading={
        <Button size="small" onClick={onOpen}>
          Details
        </Button>
      }
    />
  );
}
