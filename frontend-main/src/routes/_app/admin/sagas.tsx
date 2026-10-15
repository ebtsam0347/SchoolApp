// /admin/sagas — operator console for distributed long-running flows.
//
// Pulls health rollup (running + failed24h + per-name 24h failure series),
// a paged list filterable by status, and a per-row Retry. Two segmented
// services — employee + payroll — each with the same shape. Selecting a
// row opens a detail drawer with `SagaProgress` and a Retry button.
//
// Auto-refreshes every 15s while the page is open; the detail drawer
// polls every 1.5s while the selected saga is RUNNING.

import { useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  Drawer,
  Input,
  InputNumber,
  Segmented,
  Skeleton,
  Space,
  Table,
  Tooltip,
  Typography,
  type TableProps,
  theme as antdTheme,
} from 'antd';
import {
  Activity,
  ArrowUpRight,
  Play,
  RefreshCcw,
  Search,
  Workflow,
} from 'lucide-react';

import {
  KpiGrid,
  PageHeader,
  SagaProgress,
  type SagaProgressStatus,
  SagaStatusGrid,
  StatCard,
  StatusPill,
  type SagaHealth as SagaCardStatus,
  type SagaStatusRow,
} from '@/components';
import { forbiddenOr, getErrorMessage, isApiError } from '@/lib';
import { formatDateTime } from '@/lib/dates';
import { relTime } from '@/auth/format';
import {
  sagasApi,
  type SagaResponse as EmployeeSagaResponse,
  type SagaStatus,
} from '@/people';
import { payrollSagasApi, type SagaResponse as PayrollSagaResponse } from '@/payroll';
import { bffSagasApi, type BffSagaRow, type SagaService } from '@/admin/sagas-bff';

const { Text, Paragraph } = Typography;
const { useToken } = antdTheme;

type ServiceKey = 'employee' | 'payroll';
type TabKey = 'all' | ServiceKey;
type AnySaga = EmployeeSagaResponse | PayrollSagaResponse;
type StatusFilter = 'FAILED' | 'RUNNING' | 'ALL';

const STATUS_TONE: Record<SagaStatus, 'error' | 'success' | 'info'> = {
  RUNNING: 'info',
  COMPLETED: 'success',
  FAILED: 'error',
};

export const Route = createFileRoute('/_app/admin/sagas')({
  component: SagasPage,
});

function SagasPage() {
  const [tab, setTab] = useState<TabKey>('all');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <PageHeader
        eyebrow="Admin"
        title="Sagas"
        description="Health of long-running distributed flows. Inspect, retry, or look up by id."
        actions={
          <Segmented<TabKey>
            value={tab}
            onChange={setTab}
            options={[
              { value: 'all', label: 'All' },
              { value: 'employee', label: 'Employee' },
              { value: 'payroll', label: 'Payroll' },
            ]}
          />
        }
      />

      {tab === 'all' ? (
        <AllServicesPanel key="all" />
      ) : (
        <ServicePanel key={tab} service={tab} />
      )}
    </div>
  );
}

function AllServicesPanel() {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const [open, setOpen] = useState<{ service: ServiceKey; id: number } | null>(null);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('FAILED');
  const [pageSize, setPageSize] = useState(50);
  const [retrying, setRetrying] = useState<number | null>(null);

  const empHealthQuery = useQuery({
    queryKey: ['admin', 'sagas', 'employee', 'health'],
    queryFn: ({ signal }) => sagasApi.health(signal),
    refetchInterval: 15_000,
  });
  const payHealthQuery = useQuery({
    queryKey: ['admin', 'sagas', 'payroll', 'health'],
    queryFn: ({ signal }) => payrollSagasApi.health(signal),
    refetchInterval: 15_000,
  });

  const bffQuery = useQuery({
    queryKey: ['admin', 'sagas', 'bff', 'list', { statusFilter, pageSize }],
    queryFn: ({ signal }) =>
      bffSagasApi.list(
        {
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          size: pageSize,
        },
        signal,
      ),
    refetchInterval: 15_000,
    placeholderData: (prev) => prev,
  });

  const totalRunning =
    (empHealthQuery.data?.running ?? 0) + (payHealthQuery.data?.running ?? 0);
  const totalFailed24h =
    (empHealthQuery.data?.failed24h ?? 0) + (payHealthQuery.data?.failed24h ?? 0);
  const totalTypes =
    (empHealthQuery.data?.perName.length ?? 0) +
    (payHealthQuery.data?.perName.length ?? 0);

  const handleRetry = async (row: BffSagaRow) => {
    setRetrying(row.id);
    try {
      const retried =
        row.service === 'employee'
          ? await sagasApi.retry(row.id)
          : await payrollSagasApi.retry(row.id);
      queryClient.setQueryData(
        ['admin', 'sagas', row.service, 'detail', row.id],
        retried,
      );
      message.success('Re-armed the failed step. Re-running…');
      void bffQuery.refetch();
      void empHealthQuery.refetch();
      void payHealthQuery.refetch();
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setRetrying(null);
    }
  };

  const bffRows = bffQuery.data?.rows ?? [];
  const degraded = bffQuery.data?.employeeDegraded || bffQuery.data?.payrollDegraded;

  const isForbiddenErr =
    bffQuery.isError && isApiError(bffQuery.error)
      ? bffQuery.error.status === 403
      : false;

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      {bffQuery.isError && !isForbiddenErr ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load sagas"
          description={getErrorMessage(bffQuery.error)}
        />
      ) : null}
      {isForbiddenErr ? (
        <Alert
          type="info"
          showIcon
          title={forbiddenOr(
            bffQuery.error,
            "You don't have permission to view sagas",
            "Couldn't load sagas",
          )}
        />
      ) : null}
      {degraded ? (
        <Alert
          type="warning"
          showIcon
          title="Aggregator running degraded"
          description={
            (bffQuery.data?.employeeDegraded ? 'employee-service ' : '') +
            (bffQuery.data?.employeeDegraded && bffQuery.data?.payrollDegraded
              ? '+ '
              : '') +
            (bffQuery.data?.payrollDegraded ? 'payroll-service ' : '') +
            'is not responding — shown rows may be incomplete.'
          }
        />
      ) : null}

      <KpiGrid minColumnWidth={220}>
        <StatCard label="Running now" value={totalRunning.toLocaleString('en-PK')} icon={Activity} tone="info" />
        <StatCard
          label="Failed (last 24h)"
          value={totalFailed24h.toLocaleString('en-PK')}
          tone={totalFailed24h > 0 ? 'warning' : 'success'}
          icon={Workflow}
        />
        <StatCard label="Saga types" value={totalTypes.toLocaleString('en-PK')} tone="neutral" />
      </KpiGrid>

      <section>
        <header
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 12,
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Sagas (all services)</h3>
          <Space size={8}>
            <Segmented<StatusFilter>
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: 'FAILED', label: 'Failed' },
                { value: 'RUNNING', label: 'Running' },
                { value: 'ALL', label: 'All' },
              ]}
            />
            <Tooltip title="Refresh">
              <Button
                size="small"
                icon={<RefreshCcw size={13} strokeWidth={1.75} />}
                onClick={() => {
                  void bffQuery.refetch();
                  void empHealthQuery.refetch();
                  void payHealthQuery.refetch();
                }}
                loading={bffQuery.isFetching && !bffQuery.isLoading}
                aria-label="Refresh"
              />
            </Tooltip>
          </Space>
        </header>

        <BffSagaTable
          loading={bffQuery.isLoading}
          rows={bffRows}
          pageSize={pageSize}
          onPageSizeChange={setPageSize}
          statusFilter={statusFilter}
          retryingId={retrying}
          onOpen={(row) => setOpen({ service: row.service, id: row.id })}
          onRetry={handleRetry}
        />
      </section>

      <section>
        <header style={{ marginBottom: 12 }}>
          <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Per-saga rollup</h3>
          <Text type="secondary" style={{ fontSize: 12 }}>
            24h failure-rate sparkline per saga name.
          </Text>
        </header>
        {empHealthQuery.isLoading || payHealthQuery.isLoading ? (
          <Skeleton active paragraph={{ rows: 3 }} />
        ) : (
          <SagaStatusGrid
            rows={[
              ...(empHealthQuery.data?.perName ?? []).map(
                (r): SagaStatusRow => ({
                  id: `employee:${r.name}`,
                  name: humanizeSagaName(r.name),
                  status: normalizeStatus(r.status),
                  failureRate24h: r.failureRate24h ?? [],
                  caption: `employee · ${r.name}`,
                }),
              ),
              ...(payHealthQuery.data?.perName ?? []).map(
                (r): SagaStatusRow => ({
                  id: `payroll:${r.name}`,
                  name: humanizeSagaName(r.name),
                  status: normalizeStatus(r.status),
                  failureRate24h: r.failureRate24h ?? [],
                  caption: `payroll · ${r.name}`,
                }),
              ),
            ]}
            emptyTitle="No sagas registered"
            emptyDescription="Neither service has durable sagas registered."
          />
        )}
      </section>

      <SagaDetailDrawer
        service={open?.service ?? 'employee'}
        sagaId={open?.id ?? null}
        onClose={() => setOpen(null)}
        onRetried={() => {
          void bffQuery.refetch();
          void empHealthQuery.refetch();
          void payHealthQuery.refetch();
        }}
        onMessage={(text, kind) => {
          if (kind === 'success') message.success(text);
          else message.error(text);
        }}
      />
    </Space>
  );
}

function BffSagaTable({
  loading,
  rows,
  pageSize,
  onPageSizeChange,
  statusFilter,
  retryingId,
  onOpen,
  onRetry,
}: {
  loading: boolean;
  rows: BffSagaRow[];
  pageSize: number;
  onPageSizeChange: (n: number) => void;
  statusFilter: StatusFilter;
  retryingId: number | null;
  onOpen: (row: BffSagaRow) => void;
  onRetry: (row: BffSagaRow) => void;
}) {
  const columns: TableProps<BffSagaRow>['columns'] = [
    {
      title: 'Service',
      key: 'service',
      width: 100,
      render: (_, r) => <ServicePill service={r.service} />,
      filters: [
        { text: 'Employee', value: 'employee' },
        { text: 'Payroll', value: 'payroll' },
      ],
      onFilter: (value, r) => r.service === value,
    },
    {
      title: 'ID',
      key: 'id',
      width: 70,
      render: (_, r) => (
        <code style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>{r.id}</code>
      ),
    },
    {
      title: 'Saga',
      key: 'sagaName',
      render: (_, r) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <code style={{ fontSize: 12 }}>{r.sagaName}</code>
          <Text type="secondary" style={{ fontSize: 11, wordBreak: 'break-all' }}>
            {r.correlationKey}
          </Text>
        </div>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 120,
      render: (_, r) => (
        <StatusPill tone={STATUS_TONE[r.status as SagaStatus]} size="sm">
          {r.status === 'RUNNING' ? 'Running' : r.status === 'COMPLETED' ? 'Completed' : 'Failed'}
        </StatusPill>
      ),
    },
    {
      title: 'Step',
      key: 'currentStep',
      render: (_, r) =>
        r.currentStep ? (
          <code style={{ fontSize: 12 }}>{r.currentStep}</code>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
    {
      title: 'Attempts',
      key: 'attemptCount',
      width: 90,
      align: 'right',
      render: (_, r) => (
        <Text style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {r.attemptCount}
        </Text>
      ),
    },
    {
      title: 'Updated',
      key: 'updatedAt',
      width: 150,
      render: (_, r) => (
        <Tooltip title={formatDateTime(r.updatedAt)}>
          <Text style={{ fontSize: 12 }}>{relTime(r.updatedAt)}</Text>
        </Tooltip>
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 110,
      render: (_, r) => (
        <Space size={4} onClick={(e) => e.stopPropagation()}>
          {r.status === 'FAILED' ? (
            <Tooltip title="Re-arm the failed step">
              <Button
                size="small"
                type="primary"
                icon={<Play size={12} strokeWidth={1.75} />}
                loading={retryingId === r.id}
                onClick={() => onRetry(r)}
              />
            </Tooltip>
          ) : null}
          <Tooltip title="Open">
            <Button
              size="small"
              icon={<ArrowUpRight size={12} strokeWidth={1.75} />}
              onClick={() => onOpen(r)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <Table<BffSagaRow>
      size="small"
      rowKey={(r) => `${r.service}:${r.id}`}
      columns={columns}
      dataSource={rows}
      loading={loading}
      // Scroll wide columns inside the table, not the page.
      scroll={{ x: 'max-content' }}
      locale={{
        emptyText:
          statusFilter === 'FAILED'
            ? 'No failed sagas. Nice.'
            : statusFilter === 'RUNNING'
              ? 'No sagas running.'
              : 'No sagas.',
      }}
      pagination={{
        pageSize,
        showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100],
        onShowSizeChange: (_, ps) => onPageSizeChange(ps),
      }}
      onRow={(record) => ({
        onClick: () => onOpen(record),
        style: { cursor: 'pointer' },
      })}
    />
  );
}

function ServicePill({ service }: { service: SagaService }) {
  return (
    <StatusPill tone={service === 'employee' ? 'info' : 'neutral'} size="sm">
      {service === 'employee' ? 'Employee' : 'Payroll'}
    </StatusPill>
  );
}

function ServicePanel({ service }: { service: ServiceKey }) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const [openSagaId, setOpenSagaId] = useState<number | null>(null);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('FAILED');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const queryKey = ['admin', 'sagas', service] as const;

  const healthQuery = useQuery({
    queryKey: [...queryKey, 'health'],
    queryFn: ({ signal }) =>
      service === 'employee'
        ? sagasApi.health(signal)
        : payrollSagasApi.health(signal),
    refetchInterval: 15_000,
  });

  const listQuery = useQuery({
    queryKey: [...queryKey, 'list', { statusFilter, page, pageSize }],
    queryFn: async ({ signal }): Promise<{ content: AnySaga[]; totalElements: number }> => {
      const params = {
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        page: page - 1,
        size: pageSize,
      };
      const res =
        service === 'employee'
          ? await sagasApi.list(params, signal)
          : await payrollSagasApi.list(params, signal);
      return {
        content: (res.content ?? []) as AnySaga[],
        totalElements: res.totalElements ?? 0,
      };
    },
    refetchInterval: 15_000,
    placeholderData: (prev) => prev,
  });

  const onLookupDone = (saga: AnySaga) => {
    queryClient.setQueryData([...queryKey, 'detail', saga.id], saga);
    setOpenSagaId(saga.id ?? null);
  };

  const data = healthQuery.data;
  const rows: SagaStatusRow[] = (data?.perName ?? []).map((row) => ({
    id: row.name,
    name: humanizeSagaName(row.name),
    status: normalizeStatus(row.status),
    failureRate24h: row.failureRate24h ?? [],
    caption: row.name,
  }));

  const isForbiddenErr =
    healthQuery.isError && isApiError(healthQuery.error)
      ? healthQuery.error.status === 403
      : false;

  const handleRowRetry = async (id: number) => {
    try {
      const saga =
        service === 'employee'
          ? await sagasApi.retry(id)
          : await payrollSagasApi.retry(id);
      queryClient.setQueryData([...queryKey, 'detail', id], saga);
      message.success('Re-armed the failed step. Re-running…');
      void listQuery.refetch();
      void healthQuery.refetch();
    } catch (e) {
      message.error(getErrorMessage(e));
    }
  };

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      {healthQuery.isError && !isForbiddenErr ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load saga health"
          description={getErrorMessage(healthQuery.error)}
        />
      ) : null}

      {isForbiddenErr ? (
        <Alert
          type="info"
          showIcon
          title={forbiddenOr(
            healthQuery.error,
            "You don't have permission to view saga health",
            "Couldn't load saga health",
          )}
        />
      ) : null}

      <KpiGrid minColumnWidth={220}>
        <StatCard
          label="Running now"
          value={data ? data.running.toLocaleString('en-PK') : '—'}
          icon={Activity}
          tone="info"
        />
        <StatCard
          label="Failed (last 24h)"
          value={data ? data.failed24h.toLocaleString('en-PK') : '—'}
          tone={data && data.failed24h > 0 ? 'warning' : 'success'}
          icon={Workflow}
        />
        <StatCard
          label="Saga types"
          value={data ? data.perName.length.toLocaleString('en-PK') : '—'}
          tone="neutral"
        />
      </KpiGrid>

      <section>
        <header
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 12,
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Sagas</h3>
          <Space size={8}>
            <Segmented<StatusFilter>
              value={statusFilter}
              onChange={(v) => {
                setStatusFilter(v);
                setPage(1);
              }}
              options={[
                { value: 'FAILED', label: 'Failed' },
                { value: 'RUNNING', label: 'Running' },
                { value: 'ALL', label: 'All' },
              ]}
            />
            <Tooltip title="Refresh">
              <Button
                size="small"
                icon={<RefreshCcw size={13} strokeWidth={1.75} />}
                onClick={() => {
                  void listQuery.refetch();
                  void healthQuery.refetch();
                }}
                loading={listQuery.isFetching && !listQuery.isLoading}
                aria-label="Refresh"
              />
            </Tooltip>
          </Space>
        </header>

        <SagaTable
          loading={listQuery.isLoading}
          error={listQuery.isError ? listQuery.error : null}
          rows={listQuery.data?.content ?? []}
          totalElements={listQuery.data?.totalElements ?? 0}
          page={page}
          pageSize={pageSize}
          onPageChange={(p, ps) => {
            setPage(p);
            setPageSize(ps);
          }}
          statusFilter={statusFilter}
          onOpen={(id) => setOpenSagaId(id)}
          onRetry={handleRowRetry}
        />
      </section>

      <section>
        <header style={{ marginBottom: 12 }}>
          <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>
            Per-saga rollup
          </h3>
          <Text type="secondary" style={{ fontSize: 12 }}>
            24h failure-rate sparkline per saga name.
          </Text>
        </header>
        {healthQuery.isLoading ? (
          <Skeleton active paragraph={{ rows: 3 }} />
        ) : (
          <SagaStatusGrid
            rows={rows}
            emptyTitle="No sagas registered"
            emptyDescription={
              service === 'employee'
                ? 'employee-service has no durable sagas registered.'
                : 'payroll-service has no durable sagas registered.'
            }
          />
        )}
      </section>

      <LookupPanel service={service} onFound={onLookupDone} />

      <SagaDetailDrawer
        service={service}
        sagaId={openSagaId}
        onClose={() => setOpenSagaId(null)}
        onRetried={() => {
          void listQuery.refetch();
          void healthQuery.refetch();
        }}
        onMessage={(text, kind) => {
          if (kind === 'success') message.success(text);
          else message.error(text);
        }}
      />
    </Space>
  );
}

function SagaTable({
  loading,
  error,
  rows,
  totalElements,
  page,
  pageSize,
  onPageChange,
  statusFilter,
  onOpen,
  onRetry,
}: {
  loading: boolean;
  error: unknown;
  rows: AnySaga[];
  totalElements: number;
  page: number;
  pageSize: number;
  onPageChange: (page: number, pageSize: number) => void;
  statusFilter: StatusFilter;
  onOpen: (id: number) => void;
  onRetry: (id: number) => void;
}) {
  const [retrying, setRetrying] = useState<number | null>(null);

  if (error && isApiError(error) && error.status === 403) {
    return (
      <Alert
        type="info"
        showIcon
        title={forbiddenOr(
          error,
          "You don't have permission to list sagas",
          "Couldn't load sagas",
        )}
      />
    );
  }
  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        title="Couldn't load sagas"
        description={getErrorMessage(error)}
      />
    );
  }

  const columns: TableProps<AnySaga>['columns'] = [
    {
      title: 'ID',
      key: 'id',
      width: 80,
      render: (_, r) => (
        <code style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {r.id ?? '—'}
        </code>
      ),
    },
    {
      title: 'Saga',
      key: 'sagaName',
      render: (_, r) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <code style={{ fontSize: 12 }}>{r.sagaName}</code>
          {r.correlationKey ? (
            <Text
              type="secondary"
              style={{ fontSize: 11, wordBreak: 'break-all' }}
            >
              {r.correlationKey}
            </Text>
          ) : null}
        </div>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 130,
      render: (_, r) => {
        const status = (r.status ?? 'RUNNING') as SagaStatus;
        return (
          <StatusPill tone={STATUS_TONE[status]} size="sm">
            {status === 'RUNNING'
              ? 'Running'
              : status === 'COMPLETED'
                ? 'Completed'
                : 'Failed'}
          </StatusPill>
        );
      },
    },
    {
      title: 'Step',
      key: 'currentStep',
      render: (_, r) =>
        r.currentStep ? (
          <code style={{ fontSize: 12 }}>{r.currentStep}</code>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
    {
      title: 'Attempts',
      key: 'attemptCount',
      width: 90,
      align: 'right',
      render: (_, r) => (
        <Text style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {r.attemptCount ?? 0}
        </Text>
      ),
    },
    {
      title: 'Updated',
      key: 'updatedAt',
      width: 150,
      render: (_, r) =>
        r.updatedAt ? (
          <Tooltip title={formatDateTime(r.updatedAt)}>
            <Text style={{ fontSize: 12 }}>{relTime(r.updatedAt)}</Text>
          </Tooltip>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
    {
      title: '',
      key: 'actions',
      width: 110,
      render: (_, r) => {
        const id = r.id;
        if (typeof id !== 'number') return null;
        const failed = r.status === 'FAILED';
        return (
          <Space size={4} onClick={(e) => e.stopPropagation()}>
            {failed ? (
              <Tooltip title="Re-arm the failed step">
                <Button
                  size="small"
                  type="primary"
                  icon={<Play size={12} strokeWidth={1.75} />}
                  loading={retrying === id}
                  onClick={async () => {
                    setRetrying(id);
                    try {
                      await onRetry(id);
                    } finally {
                      setRetrying(null);
                    }
                  }}
                />
              </Tooltip>
            ) : null}
            <Tooltip title="Open">
              <Button
                size="small"
                icon={<ArrowUpRight size={12} strokeWidth={1.75} />}
                onClick={() => onOpen(id)}
              />
            </Tooltip>
          </Space>
        );
      },
    },
  ];

  return (
    <Table<AnySaga>
      size="small"
      rowKey={(r) => r.id ?? r.correlationKey ?? Math.random()}
      columns={columns}
      dataSource={rows}
      loading={loading}
      // Scroll wide columns inside the table, not the page.
      scroll={{ x: 'max-content' }}
      locale={{
        emptyText:
          statusFilter === 'FAILED'
            ? 'No failed sagas. Nice.'
            : statusFilter === 'RUNNING'
              ? 'No sagas running.'
              : 'No sagas.',
      }}
      pagination={{
        current: page,
        pageSize,
        total: totalElements,
        showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100],
        showTotal: (total, [from, to]) => `${from}–${to} of ${total}`,
        onChange: onPageChange,
      }}
      onRow={(record) => ({
        onClick: () =>
          typeof record.id === 'number' ? onOpen(record.id) : undefined,
        style: { cursor: 'pointer' },
      })}
    />
  );
}

type LookupMode = 'id' | 'key';

function LookupPanel({
  service,
  onFound,
}: {
  service: ServiceKey;
  onFound: (saga: AnySaga) => void;
}) {
  const { token } = useToken();
  const [mode, setMode] = useState<LookupMode>('id');
  const [sagaId, setSagaId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [correlationKey, setCorrelationKey] = useState('');
  const [pendingError, setPendingError] = useState<string | null>(null);

  const lookupMutation = useMutation({
    mutationFn: async (): Promise<AnySaga> => {
      if (mode === 'id') {
        if (sagaId === null) throw new Error('Enter a saga id.');
        return service === 'employee'
          ? await sagasApi.get(sagaId)
          : await payrollSagasApi.get(sagaId);
      }
      if (!name.trim() || !correlationKey.trim()) {
        throw new Error('Name and correlation key are both required.');
      }
      return service === 'employee'
        ? await sagasApi.byKey(name.trim(), correlationKey.trim())
        : await payrollSagasApi.byKey(name.trim(), correlationKey.trim());
    },
    onSuccess: (saga) => {
      setPendingError(null);
      onFound(saga);
    },
    onError: (e) => {
      if (isApiError(e) && e.status === 404) {
        setPendingError('No saga found for that lookup.');
      } else {
        setPendingError(getErrorMessage(e));
      }
    },
  });

  return (
    <section
      style={{
        padding: 16,
        borderRadius: token.borderRadiusLG,
        background: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
      }}
    >
      <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Look up a saga</h3>
      <Paragraph type="secondary" style={{ marginTop: 4, marginBottom: 12, fontSize: 12 }}>
        Jump to a specific saga by its id or by (name + correlation key).
      </Paragraph>

      <Space wrap size={[12, 12]} style={{ width: '100%' }} align="end">
        <Segmented<LookupMode>
          value={mode}
          onChange={(v) => {
            setMode(v);
            setPendingError(null);
          }}
          options={[
            { value: 'id', label: 'By id' },
            { value: 'key', label: 'By name + key' },
          ]}
        />

        {mode === 'id' ? (
          <InputNumber
            min={1}
            value={sagaId ?? undefined}
            onChange={(v) => setSagaId(typeof v === 'number' ? v : null)}
            placeholder="Saga id"
            style={{ width: 160 }}
            onPressEnter={() => lookupMutation.mutate()}
          />
        ) : (
          <>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Saga name (e.g. ONBOARDING)"
              style={{ width: 260 }}
              onPressEnter={() => lookupMutation.mutate()}
            />
            <Input
              value={correlationKey}
              onChange={(e) => setCorrelationKey(e.target.value)}
              placeholder="Correlation key (e.g. employee:1042)"
              style={{ width: 260 }}
              onPressEnter={() => lookupMutation.mutate()}
            />
          </>
        )}

        <Button
          type="primary"
          icon={<Search size={14} strokeWidth={1.75} />}
          loading={lookupMutation.isPending}
          onClick={() => lookupMutation.mutate()}
        >
          Look up
        </Button>
      </Space>

      {pendingError ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 12 }}
          title={pendingError}
        />
      ) : null}
    </section>
  );
}

function SagaDetailDrawer({
  service,
  sagaId,
  onClose,
  onRetried,
  onMessage,
}: {
  service: ServiceKey;
  sagaId: number | null;
  onClose: () => void;
  onRetried: () => void;
  onMessage: (text: string, kind: 'success' | 'error') => void;
}) {
  const queryClient = useQueryClient();
  const queryKey = ['admin', 'sagas', service, 'detail', sagaId] as const;

  const detailQuery = useQuery({
    queryKey,
    enabled: sagaId !== null,
    queryFn: ({ signal }): Promise<AnySaga> =>
      service === 'employee'
        ? sagasApi.get(sagaId!, signal)
        : payrollSagasApi.get(sagaId!, signal),
    refetchInterval: (q) => {
      const status = q.state.data?.status;
      return status === 'RUNNING' ? 1500 : false;
    },
  });

  const retryMutation = useMutation({
    mutationFn: (): Promise<AnySaga> =>
      service === 'employee'
        ? sagasApi.retry(sagaId!)
        : payrollSagasApi.retry(sagaId!),
    onSuccess: (saga) => {
      queryClient.setQueryData(queryKey, saga);
      onMessage('Re-armed the failed step. Re-running…', 'success');
      onRetried();
    },
    onError: (e) => onMessage(getErrorMessage(e), 'error'),
  });

  const saga = detailQuery.data;

  return (
    <Drawer
      open={sagaId !== null}
      onClose={onClose}
      title={sagaId !== null ? `Saga #${sagaId}` : 'Saga'}
      size={560}
      destroyOnHidden
      loading={detailQuery.isLoading}
    >
      {detailQuery.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load saga"
          description={getErrorMessage(detailQuery.error)}
        />
      ) : saga ? (
        <SagaDetailBody
          saga={saga}
          onRetry={() => retryMutation.mutate()}
          retrying={retryMutation.isPending}
        />
      ) : null}
    </Drawer>
  );
}

function SagaDetailBody({
  saga,
  onRetry,
  retrying,
}: {
  saga: AnySaga;
  onRetry: () => void;
  retrying: boolean;
}) {
  const status: SagaProgressStatus =
    saga.status === 'COMPLETED' || saga.status === 'FAILED'
      ? saga.status
      : 'RUNNING';
  const failed = status === 'FAILED';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <SagaProgress
        status={status}
        currentStep={saga.currentStep ?? undefined}
        attemptCount={saga.attemptCount}
        lastError={saga.lastError ?? undefined}
        actions={
          failed ? (
            <Button type="primary" danger loading={retrying} onClick={onRetry}>
              Retry failed step
            </Button>
          ) : null
        }
      />

      <dl
        style={{
          margin: 0,
          display: 'grid',
          gridTemplateColumns: 'auto 1fr',
          gap: '6px 16px',
          fontSize: 12,
        }}
      >
        <Dt>Name</Dt>
        <Dd><code>{saga.sagaName}</code></Dd>
        <Dt>Correlation key</Dt>
        <Dd><code style={{ wordBreak: 'break-all' }}>{saga.correlationKey}</code></Dd>
        <Dt>Step</Dt>
        <Dd>
          {saga.currentStep ?? <Text type="secondary">—</Text>}
          {typeof saga.stepIndex === 'number' ? (
            <Text type="secondary" style={{ marginLeft: 6 }}>
              (index {saga.stepIndex})
            </Text>
          ) : null}
        </Dd>
        <Dt>Attempts</Dt>
        <Dd style={{ fontVariantNumeric: 'tabular-nums' }}>{saga.attemptCount ?? 0}</Dd>
        {saga.nextAttemptAt ? (
          <>
            <Dt>Next attempt</Dt>
            <Dd>{formatDateTime(saga.nextAttemptAt)}</Dd>
          </>
        ) : null}
        <Dt>Created</Dt>
        <Dd>
          {saga.createdAt ? formatDateTime(saga.createdAt) : '—'}
          {saga.createdBy ? (
            <Text type="secondary" style={{ marginLeft: 6 }}>
              by {saga.createdBy}
            </Text>
          ) : null}
        </Dd>
        <Dt>Updated</Dt>
        <Dd>{saga.updatedAt ? formatDateTime(saga.updatedAt) : '—'}</Dd>
      </dl>
    </div>
  );
}

function Dt({ children }: { children: React.ReactNode }) {
  return (
    <dt
      style={{
        textTransform: 'uppercase',
        letterSpacing: '0.04em',
        opacity: 0.6,
      }}
    >
      {children}
    </dt>
  );
}

function Dd({ children, style }: { children: React.ReactNode; style?: React.CSSProperties }) {
  return <dd style={{ margin: 0, ...style }}>{children}</dd>;
}

function normalizeStatus(raw: string | undefined): SagaCardStatus {
  switch ((raw ?? '').toUpperCase()) {
    case 'OK':
      return 'OK';
    case 'DEGRADED':
      return 'DEGRADED';
    case 'INCIDENT':
      return 'INCIDENT';
    default:
      return 'UNKNOWN';
  }
}

// Server step keys are SCREAMING_SNAKE; pretty-print for the rollup card name.
function humanizeSagaName(raw: string): string {
  const lower = raw.replace(/_/g, ' ').toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}
