// /payroll/queue — disbursement queue for the payroll team.
//
// Lists payslips parked in HELD or RELEASE_PENDING across runs. The page
// answers "what's left to settle today" — held nets pending release on the
// next run, plus releases queued from prior periods. Inline release/unhold
// happens on the run-detail page; this is the holding pen.
//
// Backed by GET /payslips/queue?status=… (P15). The payroll service resolves
// employee names + run period in one shot so the table is meaningful without
// fan-out.

import { createFileRoute, Link } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Empty,
  Input,
  Modal,
  Segmented,
  Table,
  Tooltip,
  type TableColumnsType,
} from 'antd';
import { Hand, RefreshCcw, Send } from 'lucide-react';

import { Can } from '@/auth';
import {
  EmptyState,
  KpiGrid,
  MoneyCell,
  PageHeader,
  Section,
  SelectionToolbar,
  StatCard,
  StatusPill,
  employeeColumn,
} from '@/components';
import { getErrorMessage } from '@/lib';
import {
  PAYSLIP_STATUS_LABEL,
  PAYSLIP_STATUS_TONE,
  PayslipDetailDrawer,
  formatPeriod,
  payrollComposites,
  payslipsApi,
  type PayslipQueueRow,
  type PayslipStatus,
} from '@/payroll';

export const Route = createFileRoute('/_app/payroll/queue')({
  component: PayslipQueuePage,
});

type QueueStatus = Extract<PayslipStatus, 'HELD' | 'RELEASE_PENDING'>;

function PayslipQueuePage() {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const [status, setStatus] = useState<QueueStatus>('HELD');
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [bulkAction, setBulkAction] = useState<'release' | 'unhold' | null>(null);
  const [reason, setReason] = useState('');
  const [openId, setOpenId] = useState<number | null>(null);

  const queueQuery = useQuery({
    queryKey: ['payroll', 'queue', status],
    queryFn: ({ signal }) => payrollComposites.payslipQueue(status, signal),
  });

  const rows = queueQuery.data?.page.content ?? [];
  const total = queueQuery.data?.page.totalElements ?? 0;
  const heldNet = rows.reduce((s, r) => s + r.net, 0);

  const bulkMutation = useMutation({
    mutationFn: async ({ ids, action, reason }: {
      ids: number[];
      action: 'release' | 'unhold';
      reason: string;
    }) => {
      const fn = action === 'release' ? payslipsApi.release : payslipsApi.unhold;
      const results = await Promise.allSettled(
        ids.map((id) => fn(id, { reason })),
      );
      return {
        succeeded: results.filter((r) => r.status === 'fulfilled').length,
        failed: results
          .map((r, i) => ({ result: r, id: ids[i]! }))
          .filter((x) => x.result.status === 'rejected'),
        action,
      };
    },
    onSuccess: ({ succeeded, failed, action }) => {
      const verb = action === 'release' ? 'Released' : 'Un-held';
      if (failed.length === 0) {
        message.success(`${verb} ${succeeded} ${succeeded === 1 ? 'payslip' : 'payslips'}.`);
      } else if (succeeded === 0) {
        message.error(`Couldn't ${action} any. ${failed.length} failed.`);
      } else {
        message.warning(`${verb} ${succeeded}. ${failed.length} failed.`);
      }
      setSelectedIds([]);
      setBulkAction(null);
      setReason('');
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'queue'] });
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'runs'] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const columns: TableColumnsType<PayslipQueueRow> = [
    {
      title: 'Period',
      key: 'period',
      render: (_v, r) => (
        <span style={{ fontWeight: 500 }}>
          {formatPeriod(r.periodYear, r.periodMonth)}
        </span>
      ),
    },
    {
      ...employeeColumn<PayslipQueueRow>({
        employee: (r) => r.employee,
        to: (r) => (r.employee.id != null ? `/people/${r.employee.id}` : undefined),
      }),
      // Stop bubble so the cell's profile Link wins over the row-level
      // "open detail drawer" handler.
      onCell: () => ({ onClick: (e) => e.stopPropagation() }),
    },
    {
      title: 'Net',
      dataIndex: 'net',
      key: 'net',
      align: 'right',
      render: (v: number) => (
        <MoneyCell amount={v} style={{ fontWeight: 600 }} />
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: PayslipStatus) => (
        <StatusPill tone={PAYSLIP_STATUS_TONE[s]}>
          {PAYSLIP_STATUS_LABEL[s]}
        </StatusPill>
      ),
    },
    {
      title: 'Reason',
      dataIndex: 'heldReason',
      key: 'heldReason',
      ellipsis: true,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Run',
      key: 'run',
      onCell: () => ({ onClick: (e) => e.stopPropagation() }),
      render: (_v, r) => (
        <Link to="/payroll/runs">
          {r.fiscalYearCode}
        </Link>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow="Money"
        title="Disbursement queue"
        description="Payslips parked in HELD or RELEASE_PENDING. Resolve hold reasons here and act on individual rows from the run detail page."
        actions={
          <Tooltip title="Refresh">
            <Button
              icon={<RefreshCcw size={14} strokeWidth={1.75} />}
              onClick={() => queueQuery.refetch()}
              loading={queueQuery.isFetching}
              aria-label="Refresh"
            />
          </Tooltip>
        }
      />

      <KpiGrid>
        <StatCard
          label={status === 'HELD' ? 'Held payslips' : 'Releases queued'}
          value={total.toString()}
          tone={status === 'HELD' ? 'warning' : 'info'}
          icon={Hand}
        />
        <StatCard
          label={status === 'HELD' ? 'Net on hold' : 'Net to release'}
          value={<MoneyCell amount={heldNet} compact />}
          tone="primary"
        />
      </KpiGrid>

      <Section
        title={status === 'HELD' ? 'Held' : 'Release pending'}
        actions={
          <Segmented<QueueStatus>
            value={status}
            onChange={setStatus}
            options={[
              { label: 'Held', value: 'HELD' },
              { label: 'Release pending', value: 'RELEASE_PENDING' },
            ]}
          />
        }
      >
        {queueQuery.isError ? (
          <Alert
            type="error"
            showIcon
            title="Couldn't load queue"
            description={getErrorMessage(queueQuery.error)}
          />
        ) : rows.length === 0 && !queueQuery.isLoading ? (
          <EmptyState
            icon={Hand}
            title={
              status === 'HELD'
                ? 'No payslips on hold'
                : 'No releases queued'
            }
            description={
              status === 'HELD'
                ? 'Holds appear here when HR pauses an individual payslip from the run detail page.'
                : 'A release is queued from the run detail page; it lands as a HELD_SALARY_RELEASE earning on the next payslip.'
            }
          />
        ) : (
          <>
            <Table<PayslipQueueRow>
              rowKey="id"
              loading={queueQuery.isLoading}
              dataSource={rows}
              columns={columns}
              pagination={false}
              locale={{ emptyText: <Empty description="No payslips match this status" /> }}
              scroll={{ x: 800 }}
              rowSelection={{
                selectedRowKeys: selectedIds.map(String),
                onChange: (keys) => setSelectedIds(keys.map((k) => Number(k))),
              }}
              onRow={(r) => ({
                onClick: () => setOpenId(r.id),
                style: { cursor: 'pointer' },
              })}
            />
            <Can I="write" a="payroll">
              <SelectionToolbar
                selectedCount={selectedIds.length}
                totalCount={total}
                onClear={() => setSelectedIds([])}
                actions={
                  status === 'HELD' ? (
                    <>
                      <Button onClick={() => setBulkAction('unhold')}>
                        Un-hold {selectedIds.length}
                      </Button>
                      <Button
                        type="primary"
                        icon={<Send size={14} strokeWidth={1.75} />}
                        onClick={() => setBulkAction('release')}
                      >
                        Release {selectedIds.length}
                      </Button>
                    </>
                  ) : (
                    <Button
                      type="primary"
                      icon={<Send size={14} strokeWidth={1.75} />}
                      onClick={() => setBulkAction('release')}
                    >
                      Release {selectedIds.length}
                    </Button>
                  )
                }
              />
            </Can>
          </>
        )}
      </Section>

      <Modal
        open={bulkAction !== null}
        title={
          bulkAction === 'release'
            ? `Release ${selectedIds.length} ${selectedIds.length === 1 ? 'payslip' : 'payslips'}?`
            : `Un-hold ${selectedIds.length} ${selectedIds.length === 1 ? 'payslip' : 'payslips'}?`
        }
        okText={
          bulkAction === 'release' ? `Release ${selectedIds.length}` : `Un-hold ${selectedIds.length}`
        }
        onOk={() => {
          if (!bulkAction) return;
          bulkMutation.mutate({ ids: selectedIds, action: bulkAction, reason: reason.trim() });
        }}
        confirmLoading={bulkMutation.isPending}
        okButtonProps={{ disabled: reason.trim().length === 0 }}
        onCancel={() => {
          setBulkAction(null);
          setReason('');
        }}
        destroyOnHidden
      >
        <p style={{ marginTop: 0 }}>
          {bulkAction === 'release'
            ? "Each selected payslip will queue a release earning on the employee's next payslip."
            : "The hold is removed and these payslips return to the regular flow."}
        </p>
        <Input.TextArea
          rows={3}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Reason (required) — recorded in the audit log"
          autoFocus
        />
      </Modal>

      <PayslipDetailDrawer
        open={openId != null}
        id={openId}
        onClose={() => setOpenId(null)}
      />
    </div>
  );
}
