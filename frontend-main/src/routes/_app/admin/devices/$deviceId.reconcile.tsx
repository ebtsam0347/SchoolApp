// /admin/devices/$deviceId/reconcile — review a COMPLETED snapshot's device
// users against the employee mirror, then clean up.
//
// A device's captured user dump is reconciled by code into three buckets:
// matched-active (keep), matched-inactive (terminated staff still enrolled),
// and unmatched (strays with no employee). HR filters by category, exports the
// full list to Excel, selects rows, and bulk un-enrolls — which queues
// DELETE_USER tasks the poller pushes to the device on its next cycle.
//
// Reached from the Reconcile button on a COMPLETED snapshot row in the device
// drawer (/admin/devices); snapshotId rides in the URL.

import {
  createFileRoute,
  Link,
  redirect,
  useNavigate,
} from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  App as AntdApp,
  Button,
  Segmented,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import {
  ArrowLeft,
  FileSpreadsheet,
  ScanSearch,
  UserCheck,
  UserMinus,
  Users,
  UserX,
} from 'lucide-react';

import {
  EmptyState,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { Can, buildAbility } from '@/auth';
import { forbiddenOr, getErrorMessage, readSnapshot } from '@/lib';
import {
  devicesApi,
  type DeviceUserReconciliationDto,
  type ReconciliationCategory,
} from '@/attendance';

const { Text } = Typography;

type CategoryFilter = 'ALL' | ReconciliationCategory;

interface ReconcileSearch {
  snapshotId?: number;
  category?: ReconciliationCategory;
  page?: number;
  size?: number;
}

const CATEGORY_LABEL: Record<ReconciliationCategory, string> = {
  MATCHED_ACTIVE: 'Active',
  MATCHED_INACTIVE: 'Terminated',
  UNMATCHED: 'Unmatched',
};

const CATEGORY_TONE: Record<
  ReconciliationCategory,
  'success' | 'warning' | 'error'
> = {
  MATCHED_ACTIVE: 'success',
  MATCHED_INACTIVE: 'warning',
  UNMATCHED: 'error',
};

function isCategory(v: unknown): v is ReconciliationCategory {
  return v === 'MATCHED_ACTIVE' || v === 'MATCHED_INACTIVE' || v === 'UNMATCHED';
}

function numOrUndef(v: unknown): number | undefined {
  const n = typeof v === 'string' ? Number(v) : typeof v === 'number' ? v : NaN;
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

export const Route = createFileRoute('/_app/admin/devices/$deviceId/reconcile')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    const allowed =
      ability.can('manage', 'attendance') || ability.can('admin', 'attendance');
    if (!allowed) throw redirect({ to: '/' });
  },
  validateSearch: (search): ReconcileSearch => ({
    snapshotId: numOrUndef(search.snapshotId),
    category: isCategory(search.category) ? search.category : undefined,
    page: numOrUndef(search.page),
    size: numOrUndef(search.size),
  }),
  component: ReconcilePage,
});

const PAGE_STYLE: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 24,
  width: '100%',
};

function ReconcilePage() {
  const { deviceId } = Route.useParams();
  const search = Route.useSearch();
  const navigate = useNavigate({ from: Route.fullPath });
  const queryClient = useQueryClient();
  const { message, modal } = AntdApp.useApp();

  const numericDeviceId = Number(deviceId);
  const snapshotId = search.snapshotId;
  const category: CategoryFilter = search.category ?? 'ALL';
  const page = search.page ?? 1;
  const size = search.size ?? 100;

  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);

  // Selection maps to the current page's rows only; reset it whenever the
  // server query (category / page / size) changes so the count stays honest.
  const updateSearch = (partial: Partial<ReconcileSearch>) => {
    setSelectedKeys([]);
    void navigate({ search: (prev) => ({ ...prev, ...partial }) });
  };

  const summaryQuery = useQuery({
    queryKey: [
      'device',
      numericDeviceId,
      'reconciliation',
      'summary',
      snapshotId,
    ],
    enabled: snapshotId != null,
    queryFn: ({ signal }) =>
      devicesApi.reconciliationSummary(numericDeviceId, snapshotId!, signal),
  });

  const rowsQuery = useQuery({
    queryKey: [
      'device',
      numericDeviceId,
      'reconciliation',
      'rows',
      snapshotId,
      category,
      page,
      size,
    ],
    enabled: snapshotId != null,
    queryFn: ({ signal }) =>
      devicesApi.reconciliation(
        numericDeviceId,
        snapshotId!,
        // the endpoint is 0-based, so we send `page - 1`.
        {
          category: category === 'ALL' ? undefined : category,
          page: page - 1,
          size,
        },
        signal,
      ),
  });

  const exportMutation = useMutation({
    mutationFn: () =>
      devicesApi.exportReconciliation(numericDeviceId, snapshotId!),
    onSuccess: (res) => {
      window.open(res.downloadUrl, '_blank', 'noopener');
      message.success('Excel ready — opening the download.');
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const unenrollMutation = useMutation({
    mutationFn: (rows: DeviceUserReconciliationDto[]) =>
      devicesApi.bulkUnenroll(numericDeviceId, {
        employeeIds: rows
          .filter((r) => r.employeeId != null)
          .map((r) => r.employeeId!),
        deviceUserIds: rows
          .filter((r) => r.employeeId == null)
          .map((r) => r.deviceUserId),
      }),
    onSuccess: (res) => {
      message.success(
        `${res.queued.toLocaleString('en-PK')} un-enroll task${res.queued === 1 ? '' : 's'} queued · ` +
          `${res.droppedBridges} enrollment${res.droppedBridges === 1 ? '' : 's'} dropped` +
          `${res.skipped ? ` · ${res.skipped} skipped` : ''}. The poller pushes them on its next cycle.`,
      );
      setSelectedKeys([]);
      void queryClient.invalidateQueries({
        queryKey: ['device', numericDeviceId, 'reconciliation'],
      });
      void queryClient.invalidateQueries({
        queryKey: ['device', numericDeviceId, 'users'],
      });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const rows = rowsQuery.data?.content ?? [];
  const total = rowsQuery.data?.totalElements ?? 0;
  const summary = summaryQuery.data;
  const selectedRows = rows.filter((r) => selectedKeys.includes(r.deviceUserId));

  if (snapshotId == null) {
    return (
      <div style={PAGE_STYLE}>
        <PageHeader eyebrow="Admin" title="Reconcile" />
        <EmptyState
          icon={ScanSearch}
          title="No snapshot selected"
          description="Open this page from a completed snapshot in the device drawer."
          action={
            <Link to="/admin/devices">
              <Button>Back to devices</Button>
            </Link>
          }
        />
      </div>
    );
  }

  const columns: TableColumnsType<DeviceUserReconciliationDto> = [
    {
      title: 'Device user',
      key: 'deviceUserId',
      width: 150,
      render: (_, r) => (
        <Space size={6}>
          <code style={{ fontSize: 12 }}>{r.deviceUserId || '—'}</code>
          {r.privilege >= 14 ? (
            <Tag color="gold" style={{ margin: 0 }}>
              Admin
            </Tag>
          ) : null}
        </Space>
      ),
    },
    {
      title: 'Employee',
      key: 'employee',
      render: (_, r) =>
        r.employeeId != null ? (
          <Space direction="vertical" size={0}>
            <span style={{ fontSize: 13 }}>{r.name ?? `#${r.employeeId}`}</span>
            <Text type="secondary" style={{ fontSize: 11 }}>
              {r.employeeCode ?? `#${r.employeeId}`}
            </Text>
          </Space>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            no matching employee
          </Text>
        ),
    },
    {
      title: 'Employment',
      key: 'employmentStatus',
      width: 140,
      render: (_, r) =>
        r.employmentStatus ? (
          <Tag
            color={
              r.employmentStatus === 'ACTIVE'
                ? 'green'
                : r.employmentStatus === 'TERMINATED'
                  ? 'red'
                  : 'default'
            }
            style={{ margin: 0 }}
          >
            {r.employmentStatus}
          </Tag>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
    {
      title: 'Category',
      key: 'category',
      width: 130,
      render: (_, r) => (
        <StatusPill tone={CATEGORY_TONE[r.category]} size="sm">
          {CATEGORY_LABEL[r.category]}
        </StatusPill>
      ),
    },
  ];

  return (
    <div style={PAGE_STYLE}>
      <PageHeader
        eyebrow="Admin"
        title={`Reconcile — snapshot #${snapshotId}`}
        description="Device users matched to employees by code. Keep the active; un-enroll the terminated and the strays with no employee."
        actions={
          <Space>
            <Link to="/admin/devices">
              <Button icon={<ArrowLeft size={14} />}>Devices</Button>
            </Link>
            <Button
              icon={<FileSpreadsheet size={14} />}
              loading={exportMutation.isPending}
              onClick={() => exportMutation.mutate()}
            >
              Export Excel
            </Button>
          </Space>
        }
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="primary"
          accentCorner="tr"
          icon={Users}
          label="Device users"
          value={(summary?.total ?? 0).toLocaleString('en-PK')}
        />
        <StatCard
          variant="plain"
          tone="success"
          icon={UserCheck}
          label="Active — keep"
          value={(summary?.matchedActive ?? 0).toLocaleString('en-PK')}
        />
        <StatCard
          variant="plain"
          tone={(summary?.matchedInactive ?? 0) > 0 ? 'warning' : 'neutral'}
          icon={UserMinus}
          label="Terminated"
          value={(summary?.matchedInactive ?? 0).toLocaleString('en-PK')}
          delta="still enrolled"
        />
        <StatCard
          variant="plain"
          tone={(summary?.unmatched ?? 0) > 0 ? 'warning' : 'neutral'}
          icon={UserX}
          label="Unmatched"
          value={(summary?.unmatched ?? 0).toLocaleString('en-PK')}
          delta="no employee"
        />
      </KpiGrid>

      <Section
        title="Reconciliation"
        subtitle={`${total.toLocaleString('en-PK')} ${
          category === 'ALL' ? 'users' : `${CATEGORY_LABEL[category].toLowerCase()} users`
        }`}
      >
        <Space
          wrap
          style={{
            width: '100%',
            justifyContent: 'space-between',
            marginBottom: 'var(--app-space-md)',
          }}
        >
          <Segmented<CategoryFilter>
            value={category}
            onChange={(v) =>
              updateSearch({
                category: v === 'ALL' ? undefined : v,
                page: undefined,
              })
            }
            options={[
              { value: 'ALL', label: 'All' },
              { value: 'MATCHED_ACTIVE', label: 'Active' },
              { value: 'MATCHED_INACTIVE', label: 'Terminated' },
              { value: 'UNMATCHED', label: 'Unmatched' },
            ]}
          />
          {selectedRows.length > 0 ? (
            <Can I="manage" a="attendance">
              <Button
                danger
                icon={<UserMinus size={14} />}
                loading={unenrollMutation.isPending}
                onClick={() => {
                  const empCount = selectedRows.filter(
                    (r) => r.employeeId != null,
                  ).length;
                  const strayCount = selectedRows.length - empCount;
                  modal.confirm({
                    title: `Un-enroll ${selectedRows.length} user${selectedRows.length === 1 ? '' : 's'}?`,
                    content: `Queues a DELETE_USER per selection — ${empCount} matched employee${empCount === 1 ? '' : 's'} (bridge row dropped) and ${strayCount} stray id${strayCount === 1 ? '' : 's'}. The poller pushes them to the device on its next cycle.`,
                    okText: 'Un-enroll',
                    okButtonProps: { danger: true },
                    onOk: () => unenrollMutation.mutateAsync(selectedRows),
                  });
                }}
              >
                Un-enroll selected ({selectedRows.length})
              </Button>
            </Can>
          ) : null}
        </Space>

        <RequestState
          loading={rowsQuery.isLoading}
          error={rowsQuery.isError ? rowsQuery.error : undefined}
          empty={!rowsQuery.isLoading && rows.length === 0}
          errorTitle={forbiddenOr(
            rowsQuery.error,
            "You don't have permission to reconcile this device",
            "Couldn't load reconciliation",
          )}
          emptyTitle="Nothing to reconcile"
          emptyDescription="No device users in this category."
          emptyIcon={ScanSearch}
        >
          <Table<DeviceUserReconciliationDto>
            rowKey="deviceUserId"
            size="small"
            dataSource={rows}
            columns={columns}
            // Scroll wide columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
            loading={rowsQuery.isFetching && !rowsQuery.isLoading}
            rowSelection={{
              selectedRowKeys: selectedKeys,
              onChange: (keys) => setSelectedKeys(keys as string[]),
            }}
            pagination={{
              current: page,
              pageSize: size,
              total,
              showSizeChanger: true,
              pageSizeOptions: [50, 100, 250, 500, 1000],
              showTotal: (t) => `${t.toLocaleString('en-PK')} users`,
              onChange: (nextPage, nextSize) =>
                updateSearch({
                  page: nextPage === 1 ? undefined : nextPage,
                  size: nextSize === 100 ? undefined : nextSize,
                }),
            }}
          />
        </RequestState>
      </Section>
    </div>
  );
}
