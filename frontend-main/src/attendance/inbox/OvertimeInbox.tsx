// Inbox table for pending overtime requests.
//
// Multi-select is supported because OT often comes in batches: one
// manager files 30 rows for the team. When every selected row shares a
// `bulkBatchId`, we use the fast-path batch endpoint; otherwise we fall
// back to N per-row calls so the user still sees their action through.
//
// Approve/reject paths use TanStack Query optimistic updates: the row
// (or batch of rows) is removed from the inbox cache + counts decremented
// on mutate, rolled back on error, settled by invalidation. Mirrors the
// Leaves and Regularization inboxes.

import { useMemo, useState } from 'react';
import { useOpenRequestFromLink } from './deep-link';
import { useInboxSearch } from './use-inbox-search';
import { requestIdColumns } from './request-id-columns';
import { employeeSubtitle } from './employee-columns';
import { Alert, App as AntdApp, Card, Select, Space, Table, Tag, Typography } from 'antd';
import { useMutation, useQueries, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Sparkles, XCircle } from 'lucide-react';
import {
  OVERTIME_STATUS_LABEL,
  OVERTIME_STATUS_TONE,
  OVERTIME_TYPE_COLOR,
  OVERTIME_TYPE_LABEL,
  type OvertimeType,
  formatDateShort,
  attendanceApi,
  formatMinutes,
  overtimeApi,
  type ApprovalInboxOvertimeRow,
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
import { Button } from 'antd';
import { getErrorMessage } from '@/lib';
import { OvertimeDecisionDrawer } from './OvertimeDecisionDrawer';
import { projectedOtMinutes } from '../overtime/projected-minutes';

const { Text } = Typography;

const INBOX_KEY = ['bff', 'approvals', 'inbox'] as const;

const minutesKey = (employeeId: number, date: string) => `${employeeId}|${date}`;

/**
 * Projected OT minutes per `employeeId|workDate` for rows the API sent
 * without `minutes.projectedOtMinutes` (builds that predate it): one
 * `/attendance/employees` call per work date (≤200 ids each). A row with
 * no record that day — no punches — projects 0. `undefined` while loading,
 * `null` when the lookup failed.
 */
function useFallbackOtMinutes(rows: ApprovalInboxOvertimeRow[]) {
  const batches = useMemo(() => {
    const byDate = new Map<string, Set<number>>();
    for (const r of rows) {
      if (r.minutes?.projectedOtMinutes != null) continue;
      const ids = byDate.get(r.request.workDate) ?? new Set<number>();
      ids.add(r.request.employeeId);
      byDate.set(r.request.workDate, ids);
    }
    return [...byDate].flatMap(([date, ids]) => {
      const all = [...ids].sort((a, b) => a - b);
      const chunks: { date: string; ids: number[] }[] = [];
      for (let i = 0; i < all.length; i += 200) chunks.push({ date, ids: all.slice(i, i + 200) });
      return chunks;
    });
  }, [rows]);

  return useQueries({
    queries: batches.map(({ date, ids }) => ({
      queryKey: ['attendance', 'employees-on-date', date, ids],
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        attendanceApi.employeesOnDate(ids, date, signal),
      staleTime: 60_000,
    })),
    combine: (results) => {
      const out = new Map<string, number | null>();
      results.forEach((res, i) => {
        const { date, ids } = batches[i]!;
        if (res.isError) for (const id of ids) out.set(minutesKey(id, date), null);
        if (!res.data) return;
        for (const id of ids) out.set(minutesKey(id, date), 0);
        for (const rec of res.data) out.set(minutesKey(rec.employeeId, date), projectedOtMinutes(rec));
      });
      return out;
    },
  });
}

function getOvertimeType(row: ApprovalInboxOvertimeRow) {
  const request = row.request as ApprovalInboxOvertimeRow['request'] & {
    overtimeType?: ApprovalInboxOvertimeRow['request']['type'];
  };
  return request.overtimeType ?? request.type;
}

export type OvertimeInboxProps = {
  rows: ApprovalInboxOvertimeRow[];
  loading: boolean;
  error: unknown;
  /** Deep link (`/inbox?open=<id>`): open this request's drawer once loaded. */
  openId?: number;
  /** Called after `openId` has been opened. */
  onOpened?: () => void;
};

export function OvertimeInbox({ rows, loading, error, openId, onOpened }: OvertimeInboxProps) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [batchRejectOpen, setBatchRejectOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [openRow, setOpenRow] = useState<ApprovalInboxOvertimeRow | null>(null);
  useOpenRequestFromLink(rows, openId, setOpenRow, onOpened);

  // Server-side search (`overtimeQ` + `overtimeType`) — see useInboxSearch.
  const [overtimeType, setOvertimeType] = useState<OvertimeType | undefined>(undefined);
  const searchResult = useInboxSearch(
    search,
    (overtimeQ) => ({ overtimeQ, overtimeType }),
    overtimeType != null,
    (d) => d.overtime,
  );
  const filteredRows = searchResult.active ? searchResult.rows : rows;

  const fallbackMinutes = useFallbackOtMinutes(filteredRows);
  const otMinutesOf = (row: ApprovalInboxOvertimeRow) =>
    row.minutes?.projectedOtMinutes ??
    fallbackMinutes.get(minutesKey(row.request.employeeId, row.request.workDate));

  const selected = filteredRows.filter((r) => selectedKeys.includes(r.request.id));
  const sharedBatchId =
    selected.length > 0 &&
    selected.every((r) => r.request.bulkBatchId === selected[0]!.request.bulkBatchId)
      ? selected[0]!.request.bulkBatchId
      : null;

  const optimisticRemove = async (ids: number[]) => {
    await queryClient.cancelQueries({ queryKey: INBOX_KEY });
    const prev = queryClient.getQueryData<ApprovalsInboxResponse>(INBOX_KEY);
    if (prev) {
      const idSet = new Set(ids);
      const removed = prev.overtime.filter((r) => idSet.has(r.request.id)).length;
      const next: ApprovalsInboxResponse = {
        ...prev,
        overtime: prev.overtime.filter((r) => !idSet.has(r.request.id)),
        counts: {
          ...prev.counts,
          overtime: Math.max(0, prev.counts.overtime - removed),
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
    void queryClient.invalidateQueries({ queryKey: ['overtime'] });
  };

  const approveRowMutation = useMutation({
    mutationFn: (id: number) => overtimeApi.approve(id),
    onMutate: (id) => optimisticRemove([id]),
    onSuccess: () => message.success('Approved'),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: settle,
  });

  const rejectRowMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      overtimeApi.reject(id, { reason }),
    onMutate: ({ id }) => optimisticRemove([id]),
    onSuccess: () => message.success('Rejected'),
    onError: (err, _v, ctx) => {
      rollback(ctx);
      message.error(getErrorMessage(err));
    },
    onSettled: settle,
  });

  const approveBatchMutation = useMutation({
    mutationFn: async () => {
      if (sharedBatchId) {
        await overtimeApi.approveBatch(sharedBatchId);
      } else {
        await Promise.all(selected.map((r) => overtimeApi.approve(r.request.id)));
      }
    },
    onMutate: () => optimisticRemove(selected.map((r) => r.request.id)),
    onSuccess: () =>
      message.success(
        sharedBatchId ? 'Approved batch' : `Approved ${selected.length}`,
      ),
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
      if (sharedBatchId) {
        await overtimeApi.rejectBatch(sharedBatchId, { reason });
      } else {
        await Promise.all(
          selected.map((r) => overtimeApi.reject(r.request.id, { reason })),
        );
      }
    },
    onMutate: () => optimisticRemove(selected.map((r) => r.request.id)),
    onSuccess: () =>
      message.success(
        sharedBatchId ? 'Rejected batch' : `Rejected ${selected.length}`,
      ),
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
      emptyIcon={Sparkles}
      emptyTitle="No OT to review"
      emptyDescription="You're all caught up."
      errorTitle="Couldn't load pending OT"
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
            <Text style={{ fontSize: 13 }}>
              {selected.length} selected
              {sharedBatchId ? (
                <Tag style={{ marginLeft: 8 }}>same batch — fast path</Tag>
              ) : null}
            </Text>
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
          <Select<OvertimeType>
            allowClear
            placeholder="All overtime types"
            style={{ width: 170 }}
            value={overtimeType}
            onChange={(v) => setOvertimeType(v ?? undefined)}
            options={(Object.keys(OVERTIME_TYPE_LABEL) as OvertimeType[]).map((t) => ({
              value: t,
              label: OVERTIME_TYPE_LABEL[t],
            }))}
          />
        }
        onReset={
          search || overtimeType != null
            ? () => {
                setSearch('');
                setOvertimeType(undefined);
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
          ...requestIdColumns<ApprovalInboxOvertimeRow>({ labelled: true }),
          {
            ...personColumn<ApprovalInboxOvertimeRow>({
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
            sorter: (a: ApprovalInboxOvertimeRow, b: ApprovalInboxOvertimeRow) =>
              a.employee.fullName.localeCompare(b.employee.fullName),
          },
          {
            title: 'Status',
            key: 'status',
            width: 110,
            render: (_v, row) => (
              <StatusPill tone={OVERTIME_STATUS_TONE[row.request.status]}>
                {OVERTIME_STATUS_LABEL[row.request.status]}
              </StatusPill>
            ),
            sorter: (a, b) =>
              OVERTIME_STATUS_LABEL[a.request.status].localeCompare(
                OVERTIME_STATUS_LABEL[b.request.status],
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
            title: 'Type',
            key: 'type',
            width: 100,
            render: (_v, row) => (
              <Tag color={OVERTIME_TYPE_COLOR[getOvertimeType(row)]}>
                {OVERTIME_TYPE_LABEL[getOvertimeType(row)]}
              </Tag>
            ),
            sorter: (a, b) =>
              OVERTIME_TYPE_LABEL[getOvertimeType(a)].localeCompare(
                OVERTIME_TYPE_LABEL[getOvertimeType(b)],
              ),
          },
          {
            // Pending OT has no paid minutes yet — show what approving it
            // would credit, from the work date's punches.
            title: 'OT minutes',
            key: 'otMinutes',
            width: 100,
            render: (_v, row) => {
              const mins = otMinutesOf(row);
              if (mins === undefined) return <Text type="secondary">…</Text>;
              if (mins === null) return <Text type="secondary">—</Text>;
              return (
                <span style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 500 }}>
                  {formatMinutes(mins)}
                </span>
              );
            },
            sorter: (a, b) => (otMinutesOf(a) ?? -1) - (otMinutesOf(b) ?? -1),
          },
          {
            title: 'Factory',
            key: 'factoryCode',
            width: 100,
            render: (_v, row) => row.request.factoryCode ?? '—',
            sorter: (a, b) =>
              (a.request.factoryCode ?? '').localeCompare(b.request.factoryCode ?? ''),
          },
          {
            title: 'Reason',
            key: 'reason',
            ellipsis: true,
            render: (_v, row) => row.request.reason ?? '—',
          },
          {
            title: 'Stage',
            key: 'stage',
            width: 130,
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
            title: 'Batch',
            key: 'batch',
            width: 90,
            render: (_v, row) =>
              row.request.bulkBatchId ? (
                <Tag style={{ fontFamily: 'ui-monospace, monospace' }}>
                  {row.request.bulkBatchId.slice(0, 6)}
                </Tag>
              ) : (
                <Text type="secondary" style={{ fontSize: 11 }}>
                  self
                </Text>
              ),
          },
          {
            title: '',
            key: 'actions',
            width: 280,
            align: 'right',
            render: (_v, row) => (
              <ApproveRejectActions
                actions={row.request.actions}
                onApprove={async () => {
                  await approveRowMutation.mutateAsync(row.request.id);
                }}
                onReject={async (reason) => {
                  await rejectRowMutation.mutateAsync({
                    id: row.request.id,
                    reason,
                  });
                }}
                leading={
                  <Button size="small" onClick={() => setOpenRow(row)}>
                    Details
                  </Button>
                }
              />
            ),
          },
        ]}
      />

      <RejectReasonModal
        open={batchRejectOpen}
        title={`Reject ${selected.length} OT row(s)?`}
        onCancel={() => setBatchRejectOpen(false)}
        onConfirm={async (reason) => {
          await rejectBatchMutation.mutateAsync(reason);
          setBatchRejectOpen(false);
        }}
      />
      <OvertimeDecisionDrawer row={openRow} onClose={() => setOpenRow(null)} />
    </RequestState>
  );
}
