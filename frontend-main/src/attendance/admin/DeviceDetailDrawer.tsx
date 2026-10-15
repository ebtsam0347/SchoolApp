// Per-device admin drawer — three sections backed by per-device endpoints:
//   Snapshots — list + reconcile / adopt users + request a fresh snapshot
//   Sync tasks — list + retry a failed task
//   Enrolled users — list + un-enroll
//
// Opened from the /admin/devices catalog row. Keeps the catalog page lean
// (no separate /admin/devices/$id route) while giving HR everything they
// need to inspect a device end-to-end.

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import {
  App as AntdApp,
  Button,
  Drawer,
  Segmented,
  Skeleton,
  Space,
  Tabs,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import {
  Camera,
  CircleAlert,
  Clock,
  Fingerprint,
  ListChecks,
  Play,
  RefreshCcw,
  ScanLine,
  ScanSearch,
  Sparkles,
  Trash2,
  Upload,
  UserCircle,
  UserPlus,
  Users,
} from 'lucide-react';

import { Can } from '@/auth';
import { RequestState, StatusPill } from '@/components';
import { forbiddenOr, getErrorMessage, isForbidden } from '@/lib';
import {
  devicesApi,
  formatDateTime,
  type AttendanceDeviceDto,
  type DeviceSnapshotStatus,
  type DeviceSnapshotSummaryDto,
  type DeviceSyncTaskDto,
  type DeviceSyncTaskStatus,
  type DeviceUserDto,
} from '@/attendance';

const { Text } = Typography;

const SNAPSHOT_TONE: Record<DeviceSnapshotStatus, 'warning' | 'success' | 'error'> = {
  PENDING: 'warning',
  COMPLETED: 'success',
  FAILED: 'error',
};

const TASK_TONE: Record<DeviceSyncTaskStatus, 'warning' | 'info' | 'success' | 'error'> = {
  PENDING: 'warning',
  IN_PROGRESS: 'info',
  DONE: 'success',
  FAILED: 'error',
};

const TASK_LABEL: Record<string, string> = {
  UPSERT_USER: 'Upsert user',
  DELETE_USER: 'Delete user',
  PUSH_TEMPLATE: 'Push template',
  SNAPSHOT_DEVICE: 'Snapshot device',
};

export type DeviceDetailDrawerProps = {
  device: AttendanceDeviceDto | null;
  onClose: () => void;
};

export function DeviceDetailDrawer({
  device,
  onClose,
}: DeviceDetailDrawerProps) {
  return (
    <Drawer
      open={device !== null}
      title={
        device ? (
          <Space size={8}>
            <Fingerprint size={14} strokeWidth={1.75} />
            <code style={{ fontSize: 13 }}>{device.code}</code>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {device.name}
            </Text>
          </Space>
        ) : (
          'Device'
        )
      }
      size={780}
      onClose={onClose}
      destroyOnHidden
    >
      {device ? <DeviceDetailBody device={device} /> : null}
    </Drawer>
  );
}

function DeviceDetailBody({ device }: { device: AttendanceDeviceDto }) {
  return (
    <Tabs
      defaultActiveKey="snapshots"
      items={[
        {
          key: 'snapshots',
          label: (
            <Space size={6}>
              <Camera size={13} strokeWidth={1.75} />
              Snapshots
            </Space>
          ),
          children: <SnapshotsTab device={device} />,
        },
        {
          key: 'tasks',
          label: (
            <Space size={6}>
              <ListChecks size={13} strokeWidth={1.75} />
              Sync tasks
            </Space>
          ),
          children: <SyncTasksTab device={device} />,
        },
        {
          key: 'users',
          label: (
            <Space size={6}>
              <Users size={13} strokeWidth={1.75} />
              Enrolled users
            </Space>
          ),
          children: <EnrolledUsersTab device={device} />,
        },
      ]}
    />
  );
}

/* ----------------------------- Snapshots ----------------------------- */

function SnapshotsTab({ device }: { device: AttendanceDeviceDto }) {
  const queryClient = useQueryClient();
  const { message, modal } = AntdApp.useApp();
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState<DeviceSnapshotStatus | 'ALL'>(
    'ALL',
  );

  const listQuery = useQuery({
    queryKey: ['device', device.id, 'snapshots', statusFilter],
    queryFn: ({ signal }) =>
      devicesApi.snapshots(
        device.id,
        { status: statusFilter === 'ALL' ? undefined : statusFilter },
        signal,
      ),
    refetchInterval: 15_000,
  });

  const requestMutation = useMutation({
    mutationFn: () => devicesApi.requestSnapshot(device.id),
    onSuccess: () => {
      message.success('Snapshot queued. The poller picks it up on its next cycle.');
      void queryClient.invalidateQueries({
        queryKey: ['device', device.id, 'snapshots'],
      });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const adoptMutation = useMutation({
    mutationFn: (snapshotId: number) =>
      devicesApi.adoptSnapshotUsers(device.id, snapshotId),
    onSuccess: (result) => {
      modal.success({
        title: 'Users adopted',
        content: (
          <Space direction="vertical" size={4}>
            <Text>
              {result.created.toLocaleString('en-PK')} created ·{' '}
              {result.updated.toLocaleString('en-PK')} updated ·{' '}
              {result.skipped.toLocaleString('en-PK')} skipped of{' '}
              {result.totalUsers.toLocaleString('en-PK')} on the device.
            </Text>
            {result.unmatchedUserIds.length > 0 ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                No matching employee for: {result.unmatchedUserIds.join(', ')}
              </Text>
            ) : null}
          </Space>
        ),
      });
      void queryClient.invalidateQueries({
        queryKey: ['device', device.id, 'users'],
      });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const rows = listQuery.data?.content ?? [];

  const columns: TableColumnsType<DeviceSnapshotSummaryDto> = [
    {
      title: 'Status',
      key: 'status',
      width: 110,
      render: (_, row) => (
        <StatusPill tone={SNAPSHOT_TONE[row.status]} size="sm">
          {row.status === 'PENDING' ? 'Pending' : row.status === 'COMPLETED' ? 'Completed' : 'Failed'}
        </StatusPill>
      ),
    },
    {
      title: 'Requested',
      key: 'requestedAt',
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <span style={{ fontSize: 12 }}>{formatDateTime(row.requestedAt)}</span>
          <Text type="secondary" style={{ fontSize: 11 }}>
            by {row.requestedBy}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Counts',
      key: 'counts',
      width: 150,
      render: (_, row) =>
        row.status === 'COMPLETED' ? (
          <Space size={8} style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
            <Tooltip title="Users captured">
              <Tag color="default" style={{ margin: 0 }}>
                {(row.userCount ?? 0).toLocaleString('en-PK')} users
              </Tag>
            </Tooltip>
            <Tooltip title="Records captured">
              <Tag color="default" style={{ margin: 0 }}>
                {(row.recordCount ?? 0).toLocaleString('en-PK')} records
              </Tag>
            </Tooltip>
          </Space>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ),
    },
    {
      title: '',
      key: 'actions',
      width: 180,
      align: 'right',
      render: (_, row) =>
        row.status === 'COMPLETED' ? (
          <Space size={6}>
            <Tooltip title="Review this snapshot's users against employees — categorize, export, and bulk un-enroll">
              <Button
                size="small"
                icon={<ScanSearch size={13} strokeWidth={1.75} />}
                onClick={() =>
                  navigate({
                    to: '/admin/devices/$deviceId/reconcile',
                    params: { deviceId: String(device.id) },
                    search: { snapshotId: row.id },
                  })
                }
              >
                Reconcile
              </Button>
            </Tooltip>
            <Tooltip title="Reconcile this snapshot's users into the device's enrolled-users list">
              <Button
                size="small"
                icon={<UserPlus size={13} strokeWidth={1.75} />}
                loading={
                  adoptMutation.isPending && adoptMutation.variables === row.id
                }
                onClick={() =>
                  modal.confirm({
                    title: `Adopt users from snapshot #${row.id}?`,
                    content:
                      "Matches each device user to an employee by code and creates or refreshes their row in this device's enrolled-users list. Users with no matching employee are skipped. Safe to re-run.",
                    okText: 'Adopt users',
                    onOk: () => adoptMutation.mutateAsync(row.id),
                  })
                }
              >
                Adopt
              </Button>
            </Tooltip>
          </Space>
        ) : row.status === 'FAILED' && row.error ? (
          <Tooltip title={row.error}>
            <CircleAlert size={14} strokeWidth={1.75} aria-label="Failed" />
          </Tooltip>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      <Space wrap>
        <Segmented<DeviceSnapshotStatus | 'ALL'>
          value={statusFilter}
          onChange={setStatusFilter}
          options={[
            { value: 'ALL', label: 'All' },
            { value: 'PENDING', label: 'Pending' },
            { value: 'COMPLETED', label: 'Completed' },
            { value: 'FAILED', label: 'Failed' },
          ]}
          size="small"
        />
        <Button
          type="primary"
          size="small"
          icon={<Sparkles size={13} strokeWidth={1.75} />}
          loading={requestMutation.isPending}
          onClick={() => requestMutation.mutate()}
        >
          Request snapshot
        </Button>
        <Button
          size="small"
          icon={<RefreshCcw size={13} strokeWidth={1.75} />}
          onClick={() => listQuery.refetch()}
          loading={listQuery.isFetching && !listQuery.isLoading}
          aria-label="Refresh"
        />
      </Space>
      <RequestState
        loading={listQuery.isLoading}
        error={listQuery.isError ? listQuery.error : undefined}
        empty={!listQuery.isLoading && rows.length === 0}
        errorTitle={forbiddenOr(
          listQuery.error,
          "You don't have permission to view snapshots",
          "Couldn't load snapshots",
        )}
        emptyTitle="No snapshots yet"
        emptyDescription="Click Request snapshot to queue one. The poller picks it up on its next cycle."
        emptyIcon={Camera}
      >
        <Table<DeviceSnapshotSummaryDto>
          rowKey="id"
          size="small"
          dataSource={rows}
          columns={columns}
          // Scroll wide columns inside the table, not the drawer.
          scroll={{ x: 'max-content' }}
          pagination={rows.length > 25 ? { pageSize: 25 } : false}
        />
      </RequestState>
    </Space>
  );
}

/* ----------------------------- Sync tasks ---------------------------- */

function SyncTasksTab({ device }: { device: AttendanceDeviceDto }) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const [statusFilter, setStatusFilter] = useState<DeviceSyncTaskStatus | 'ALL'>(
    'ALL',
  );

  const listQuery = useQuery({
    queryKey: ['device', device.id, 'sync-tasks', statusFilter],
    queryFn: ({ signal }) =>
      devicesApi.syncTasks(
        device.id,
        { status: statusFilter === 'ALL' ? undefined : statusFilter },
        signal,
      ),
    refetchInterval: 15_000,
  });

  const retryMutation = useMutation({
    mutationFn: (taskId: number) => devicesApi.retrySyncTask(device.id, taskId),
    onSuccess: () => {
      message.success('Task re-queued. Poller will re-claim on its next cycle.');
      void queryClient.invalidateQueries({
        queryKey: ['device', device.id, 'sync-tasks'],
      });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const rows = listQuery.data?.content ?? [];

  const columns: TableColumnsType<DeviceSyncTaskDto> = [
    {
      title: 'Task',
      key: 'taskType',
      width: 140,
      render: (_, row) => (
        <code style={{ fontSize: 12 }}>{TASK_LABEL[row.taskType] ?? row.taskType}</code>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 120,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <StatusPill tone={TASK_TONE[row.status]} size="sm">
            {row.status === 'IN_PROGRESS' ? 'In progress' : row.status === 'DONE' ? 'Done' : row.status === 'PENDING' ? 'Pending' : 'Failed'}
          </StatusPill>
          {row.attempts > 0 ? (
            <Text type="secondary" style={{ fontSize: 10 }}>
              {row.attempts} attempt{row.attempts === 1 ? '' : 's'}
            </Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: 'Scheduled',
      key: 'scheduledFor',
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
            {formatDateTime(row.scheduledFor)}
          </span>
          {row.lastError ? (
            <Tooltip title={row.lastError}>
              <Text type="danger" style={{ fontSize: 11 }}>
                last error: {row.lastError.slice(0, 36)}
                {row.lastError.length > 36 ? '…' : ''}
              </Text>
            </Tooltip>
          ) : row.claimedAt ? (
            <Text type="secondary" style={{ fontSize: 11 }}>
              claimed {formatDateTime(row.claimedAt)}
            </Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 90,
      align: 'right',
      render: (_, row) =>
        row.status === 'FAILED' ? (
          <Button
            size="small"
            icon={<Play size={13} strokeWidth={1.75} />}
            loading={
              retryMutation.isPending && retryMutation.variables === row.id
            }
            onClick={() => retryMutation.mutate(row.id)}
          >
            Retry
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      <Space wrap>
        <Segmented<DeviceSyncTaskStatus | 'ALL'>
          value={statusFilter}
          onChange={setStatusFilter}
          options={[
            { value: 'ALL', label: 'All' },
            { value: 'PENDING', label: 'Pending' },
            { value: 'IN_PROGRESS', label: 'In progress' },
            { value: 'FAILED', label: 'Failed' },
            { value: 'DONE', label: 'Done' },
          ]}
          size="small"
        />
        <Button
          size="small"
          icon={<RefreshCcw size={13} strokeWidth={1.75} />}
          onClick={() => listQuery.refetch()}
          loading={listQuery.isFetching && !listQuery.isLoading}
          aria-label="Refresh"
        />
      </Space>
      <RequestState
        loading={listQuery.isLoading}
        error={listQuery.isError ? listQuery.error : undefined}
        empty={!listQuery.isLoading && rows.length === 0}
        errorTitle={forbiddenOr(
          listQuery.error,
          "You don't have permission to view sync tasks",
          "Couldn't load sync tasks",
        )}
        emptyTitle="No sync tasks"
        emptyDescription="The poller has nothing queued for this device."
        emptyIcon={Clock}
      >
        <Table<DeviceSyncTaskDto>
          rowKey="id"
          size="small"
          dataSource={rows}
          columns={columns}
          // Scroll wide columns inside the table, not the drawer.
          scroll={{ x: 'max-content' }}
          pagination={rows.length > 25 ? { pageSize: 25 } : false}
        />
      </RequestState>
    </Space>
  );
}

/* --------------------------- Enrolled users -------------------------- */

function EnrolledUsersTab({ device }: { device: AttendanceDeviceDto }) {
  const queryClient = useQueryClient();
  const { message, modal } = AntdApp.useApp();

  const listQuery = useQuery({
    queryKey: ['device', device.id, 'users'],
    queryFn: ({ signal }) => devicesApi.deviceUsers(device.id, signal),
  });

  const unenrollMutation = useMutation({
    mutationFn: (employeeId: number) =>
      devicesApi.unenrollDeviceUser(device.id, employeeId),
    onSuccess: () => {
      message.success('Un-enrollment scheduled. Poller will push DELETE_USER on its next cycle.');
      void queryClient.invalidateQueries({
        queryKey: ['device', device.id, 'users'],
      });
      void queryClient.invalidateQueries({
        queryKey: ['device', device.id, 'sync-tasks'],
      });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  // Capture is store-only: read the prints off this device into the central
  // store, no push. Track progress under the Sync tasks tab.
  const captureMutation = useMutation({
    mutationFn: (employeeId: number) =>
      devicesApi.captureTemplate(device.id, employeeId),
    onSuccess: () => {
      message.success(
        'Capture queued. The poller reads the prints off this device into the store on its next cycle.',
      );
      void queryClient.invalidateQueries({
        queryKey: ['device', device.id, 'sync-tasks'],
      });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  // Push provisions the employee's stored prints onto THIS device — one
  // PUSH_TEMPLATE per print. 422 if nothing's been captured yet.
  const pushMutation = useMutation({
    mutationFn: (employeeId: number) =>
      devicesApi.pushTemplates(device.id, employeeId),
    onSuccess: (tasks) => {
      message.success(
        tasks.length === 0
          ? 'No prints to push.'
          : `${tasks.length} print${tasks.length === 1 ? '' : 's'} queued to push onto ${device.code}. The poller provisions them on its next cycle.`,
      );
      void queryClient.invalidateQueries({
        queryKey: ['device', device.id, 'sync-tasks'],
      });
      void queryClient.invalidateQueries({
        queryKey: ['device', device.id, 'users'],
      });
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const rows = listQuery.data?.content ?? [];

  const columns: TableColumnsType<DeviceUserDto> = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, row) => (
        <Space size={6}>
          <UserCircle size={13} strokeWidth={1.75} />
          <code style={{ fontSize: 12 }}>#{row.employeeId}</code>
        </Space>
      ),
    },
    {
      title: 'Privilege',
      key: 'privilege',
      width: 100,
      render: (_, row) => (
        <Tag color={row.privilege >= 14 ? 'gold' : 'default'} style={{ margin: 0 }}>
          {row.privilege >= 14 ? 'Admin' : row.privilege >= 1 ? `Lvl ${row.privilege}` : 'User'}
        </Tag>
      ),
    },
    {
      title: 'Biometric',
      key: 'biometric',
      width: 130,
      render: (_, row) => (
        <Space size={6}>
          {row.hasFingerprint ? (
            <Tooltip title="Fingerprint enrolled">
              <Tag color="green" style={{ margin: 0 }}>Print</Tag>
            </Tooltip>
          ) : null}
          {row.hasFace ? (
            <Tooltip title="Face enrolled">
              <Tag color="cyan" style={{ margin: 0 }}>Face</Tag>
            </Tooltip>
          ) : null}
          {!row.hasFingerprint && !row.hasFace ? (
            <Text type="secondary" style={{ fontSize: 11 }}>
              none
            </Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: 'Synced',
      key: 'syncedAt',
      width: 160,
      render: (_, row) =>
        row.syncedAt ? (
          <Text style={{ fontSize: 12 }}>{formatDateTime(row.syncedAt)}</Text>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            never pushed
          </Text>
        ),
    },
    {
      title: '',
      key: 'actions',
      width: 250,
      align: 'right',
      render: (_, row) => (
        <Can I="manage" a="attendance">
          <Space size={6}>
            <Tooltip
              title={
                device.active
                  ? "Read this employee's prints off the device into the central store"
                  : 'Device is inactive — reactivate it first'
              }
            >
              <Button
                size="small"
                icon={<ScanLine size={13} strokeWidth={1.75} />}
                disabled={!device.active}
                loading={
                  captureMutation.isPending &&
                  captureMutation.variables === row.employeeId
                }
                onClick={() =>
                  modal.confirm({
                    title: `Capture prints for #${row.employeeId}?`,
                    content: `Reads this employee's enrolled fingerprints off ${device.code} into the central store. Store-only — it won't push to any device. Run this on the enrollment device right after the employee scans.`,
                    okText: 'Capture',
                    onOk: () => captureMutation.mutateAsync(row.employeeId),
                  })
                }
              >
                Capture
              </Button>
            </Tooltip>
            <Tooltip
              title={
                device.active
                  ? "Push this employee's stored prints onto this device"
                  : 'Device is inactive — reactivate it first'
              }
            >
              <Button
                size="small"
                type="primary"
                ghost
                icon={<Upload size={13} strokeWidth={1.75} />}
                disabled={!device.active}
                loading={
                  pushMutation.isPending &&
                  pushMutation.variables === row.employeeId
                }
                onClick={() =>
                  modal.confirm({
                    title: `Push prints to ${device.code}?`,
                    content:
                      "Queues a push of this employee's stored fingerprints onto this device — the poller provisions them on its next cycle. Capture must have run first; reusable any time with no re-scan.",
                    okText: 'Push',
                    onOk: () => pushMutation.mutateAsync(row.employeeId),
                  })
                }
              >
                Push
              </Button>
            </Tooltip>
            <Button
              size="small"
              danger
              icon={<Trash2 size={13} strokeWidth={1.75} />}
              loading={
                unenrollMutation.isPending &&
                unenrollMutation.variables === row.employeeId
              }
              onClick={() =>
                modal.confirm({
                  title: `Un-enroll #${row.employeeId} from ${device.code}?`,
                  content:
                    'Schedules a DELETE_USER sync task. The employee can no longer punch on this device once the poller pushes it through.',
                  okText: 'Un-enroll',
                  okButtonProps: { danger: true },
                  onOk: () => unenrollMutation.mutateAsync(row.employeeId),
                })
              }
            >
              Un-enroll
            </Button>
          </Space>
        </Can>
      ),
    },
  ];

  if (listQuery.isLoading) return <Skeleton active paragraph={{ rows: 4 }} />;

  if (listQuery.isError) {
    return (
      <Text type="danger">
        {forbiddenOr(
          listQuery.error,
          "You don't have permission to view enrolled users",
          "Couldn't load enrolled users",
        )}
        {!isForbidden(listQuery.error) ? `: ${getErrorMessage(listQuery.error)}` : ''}
      </Text>
    );
  }

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      <Text type="secondary" style={{ fontSize: 12 }}>
        {rows.length.toLocaleString('en-PK')} employee{rows.length === 1 ? '' : 's'} enrolled on this device.
      </Text>
      <Table<DeviceUserDto>
        rowKey="id"
        size="small"
        dataSource={rows}
        columns={columns}
        // Scroll wide columns inside the table, not the drawer.
        scroll={{ x: 'max-content' }}
        pagination={rows.length > 25 ? { pageSize: 25 } : false}
      />
    </Space>
  );
}
