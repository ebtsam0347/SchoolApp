// /payroll/increments/$id — increment batch detail.
//
// DRAFT: per-line edit / remove, batch-level Apply / Cancel buttons. Apply
//   refuses if any line is in ERROR.
// APPLIED: per-line view; the operation is terminal.
// CANCELLED: read-only.

import { createFileRoute, Link, useParams } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  Empty,
  Popconfirm,
  Space,
  Table,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import { Ban, ChevronLeft, Send, XCircle } from 'lucide-react';

import {
  KpiGrid,
  MoneyCell,
  PageHeader,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { Can } from '@/auth';
import { getErrorMessage } from '@/lib';
import {
  INCREMENT_BATCH_STATUS_LABEL,
  INCREMENT_BATCH_STATUS_TONE,
  INCREMENT_LINE_STATUS_LABEL,
  INCREMENT_LINE_STATUS_TONE,
  formatDateTime,
  formatDateShort,
  incrementBatchesApi,
  payrollComposites,
  type IncrementBatchLineWithEmployee,
} from '@/payroll';

const { Text } = Typography;

export const Route = createFileRoute('/_app/payroll/increments/$id')({
  component: IncrementBatchDetailPage,
});

function IncrementBatchDetailPage() {
  const { id: idParam } = useParams({ from: '/_app/payroll/increments/$id' });
  const id = Number(idParam);
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const batchQuery = useQuery({
    queryKey: ['payroll', 'increment', id, 'bff'],
    queryFn: ({ signal }) => payrollComposites.incrementBatch(id, signal),
  });

  const batch = batchQuery.data;
  const lines = batch?.lines ?? [];
  const errorCount = lines.filter((l) => l.status === 'ERROR').length;

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['payroll', 'increment', id, 'bff'] });
    void queryClient.invalidateQueries({ queryKey: ['payroll', 'increments'] });
  }

  const apply = useMutation({
    mutationFn: () => incrementBatchesApi.apply(id),
    onSuccess: () => {
      message.success('Batch applied.');
      refresh();
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });
  const cancel = useMutation({
    mutationFn: () => incrementBatchesApi.cancel(id),
    onSuccess: () => {
      message.success('Batch cancelled.');
      refresh();
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });
  const removeLine = useMutation({
    mutationFn: (lineId: number) => incrementBatchesApi.removeLine(id, lineId),
    onSuccess: () => {
      message.success('Line removed.');
      refresh();
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const isDraft = batch?.status === 'DRAFT';
  const totalDelta = lines.reduce((s, l) => s + (l.newBasic - l.oldBasic), 0);

  const columns: TableColumnsType<IncrementBatchLineWithEmployee> = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_v, r) => (
        <Space size={4} orientation="vertical">
          <span style={{ fontWeight: 500 }}>{r.employee.fullName}</span>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {r.employee.employeeCode ?? `#${r.employee.id}`}
            {r.employee.departmentName && ` · ${r.employee.departmentName}`}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Old basic',
      dataIndex: 'oldBasic',
      key: 'oldBasic',
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} />,
    },
    {
      title: 'New basic',
      dataIndex: 'newBasic',
      key: 'newBasic',
      align: 'right',
      render: (v: number, r) => (
        <Space size={4}>
          <MoneyCell amount={v} style={{ fontWeight: 600 }} />
          <DeltaTag delta={v - r.oldBasic} />
        </Space>
      ),
    },
    {
      title: 'Income tax',
      key: 'tax',
      align: 'right',
      render: (_v, r) =>
        r.newIncomeTax != null ? (
          <Space size={4}>
            <MoneyCell amount={r.newIncomeTax} />
            {r.oldIncomeTax != null && (
              <DeltaTag delta={r.newIncomeTax - r.oldIncomeTax} />
            )}
          </Space>
        ) : (
          '—'
        ),
    },
    {
      title: 'Effective',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      render: (v: string | null) => (v ? formatDateShort(v) : '—'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: IncrementBatchLineWithEmployee['status'], r) => (
        <Space>
          <StatusPill tone={INCREMENT_LINE_STATUS_TONE[s]}>
            {INCREMENT_LINE_STATUS_LABEL[s]}
          </StatusPill>
          {r.errorMessage && (
            <Tooltip title={r.errorMessage}>
              <XCircle size={14} strokeWidth={1.75} color="var(--ant-color-error)" />
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: '',
      key: 'actions',
      render: (_v, row) =>
        isDraft && (
          <Can I="manage" a="payroll">
            <Popconfirm
              title="Remove this line?"
              okText="Remove"
              okButtonProps={{ danger: true }}
              onConfirm={() => removeLine.mutateAsync(row.id)}
            >
              <Button size="small" danger>
                Remove
              </Button>
            </Popconfirm>
          </Can>
        ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow={
          <Link to="/payroll/increments">
            <ChevronLeft size={12} style={{ verticalAlign: -1, marginRight: 2 }} />
            Increments
          </Link>
        }
        title={batch?.name ?? 'Loading…'}
        description={
          batch && (
            <Space size={10} wrap>
              <StatusPill tone={INCREMENT_BATCH_STATUS_TONE[batch.status]}>
                {INCREMENT_BATCH_STATUS_LABEL[batch.status]}
              </StatusPill>
              <Text type="secondary">
                Effective {formatDateShort(batch.effectiveFrom)}
              </Text>
              {batch.appliedAt && (
                <Text type="secondary">Applied {formatDateTime(batch.appliedAt)}</Text>
              )}
              {batch.notes && <Text type="secondary">— {batch.notes}</Text>}
            </Space>
          )
        }
        actions={
          batch && isDraft && (
            <Can I="manage" a="payroll">
              <Space>
                <Tooltip
                  title={
                    errorCount > 0
                      ? `Resolve ${errorCount} ERROR line${errorCount === 1 ? '' : 's'} before applying.`
                      : undefined
                  }
                >
                  <Popconfirm
                    title={`Apply ${lines.length} line${lines.length === 1 ? '' : 's'}? Runs in one transaction.`}
                    okText="Apply"
                    onConfirm={() => apply.mutateAsync()}
                    disabled={errorCount > 0}
                  >
                    <Button
                      type="primary"
                      icon={<Send size={14} strokeWidth={1.75} />}
                      loading={apply.isPending}
                      disabled={errorCount > 0}
                    >
                      Apply batch
                    </Button>
                  </Popconfirm>
                </Tooltip>
                <Popconfirm
                  title="Cancel this draft batch?"
                  okText="Cancel batch"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => cancel.mutateAsync()}
                >
                  <Button danger icon={<Ban size={14} strokeWidth={1.75} />} loading={cancel.isPending}>
                    Cancel
                  </Button>
                </Popconfirm>
              </Space>
            </Can>
          )
        }
      />

      {batchQuery.isError && (
        <Alert
          type="error"
          showIcon
          title="Couldn't load batch"
          description={getErrorMessage(batchQuery.error)}
        />
      )}

      {batch && (
        <KpiGrid>
          <StatCard label="Lines" value={lines.length.toString()} tone="info" />
          <StatCard
            label="Errors"
            value={errorCount.toString()}
            tone={errorCount > 0 ? 'error' : 'success'}
          />
          <StatCard
            label="Total monthly delta"
            value={
              <MoneyCell
                amount={totalDelta}
                compact
                showSign
                coloredSign
              />
            }
            tone={totalDelta < 0 ? 'warning' : 'primary'}
          />
        </KpiGrid>
      )}

      <Section title="Lines">
        {lines.length === 0 ? (
          <Empty description="No lines on this batch" />
        ) : (
          <Table<IncrementBatchLineWithEmployee>
            rowKey="id"
            dataSource={lines}
            columns={columns}
            pagination={{ pageSize: 50, showSizeChanger: false }}
            scroll={{ x: 800 }}
          />
        )}
      </Section>
    </div>
  );
}

function DeltaTag({ delta }: { delta: number }) {
  if (delta === 0) return null;
  const color = delta > 0 ? 'var(--ant-color-success)' : 'var(--ant-color-error)';
  return (
    <span style={{ color, fontSize: 12 }}>
      {delta > 0 ? '▲' : '▼'} <MoneyCell amount={Math.abs(delta)} />
    </span>
  );
}
