// /admin/devices — biometric / kiosk catalog.
//
// Lists every device the poller is allowed to dial, with status from the
// poller's heartbeat (ONLINE / OFFLINE / ERROR / UNKNOWN) + last-seen-at
// freshness. HR can create, edit (everything but the immutable code),
// set the ZK comm password (write-only — only `commPasswordSet` comes
// back), and soft-deactivate. Code is immutable post-create — devices
// keep historical punch FKs.

import { createFileRoute, Outlet, redirect, useLocation } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
  Typography,
  type MenuProps,
  type TableColumnsType,
  Table,
} from 'antd';
import {
  CircleAlert,
  Edit3,
  Inbox,
  KeyRound,
  MonitorSmartphone,
  MoreHorizontal,
  Plus,
  PowerOff,
  Power,
  RefreshCcw,
  RotateCcw,
  Wifi,
  WifiOff,
} from 'lucide-react';

import {
  DrawerForm,
  EmptyState,
  FilterBar,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { Can, buildAbility } from '@/auth';
import {
  forbiddenOr,
  formatDateTime,
  getErrorMessage,
  handleFormError,
  isForbidden,
  readSnapshot,
  useDebounced,
} from '@/lib';
import {
  devicesApi,
  type AttendanceDeviceDto,
  type AttendanceDeviceStatus,
  type CreateAttendanceDeviceRequest,
  type UpdateAttendanceDeviceRequest,
} from '@/attendance';
import { DeviceDetailDrawer } from '@/attendance/admin';

const { Text } = Typography;

const DEVICES_QUERY_KEY = ['attendance', 'devices', 'all'] as const;

const STATUS_TONE: Record<AttendanceDeviceStatus, 'success' | 'warning' | 'error' | 'neutral'> = {
  ONLINE: 'success',
  OFFLINE: 'warning',
  ERROR: 'error',
  UNKNOWN: 'neutral',
};

const STATUS_LABEL: Record<AttendanceDeviceStatus, string> = {
  ONLINE: 'Online',
  OFFLINE: 'Offline',
  ERROR: 'Error',
  UNKNOWN: 'Unknown',
};

export const Route = createFileRoute('/_app/admin/devices')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    const ability = buildAbility(snap.user);
    const allowed =
      ability.can('manage', 'attendance') ||
      ability.can('admin', 'attendance');
    if (!allowed) throw redirect({ to: '/' });
  },
  component: DevicesAdminPage,
});

type StatusFilter = 'ALL' | AttendanceDeviceStatus;
type ScopeFilter = 'active' | 'all';

function DevicesAdminPage() {
  // `/admin/devices/$deviceId/reconcile` is registered as a child of this
  // route, so this component renders for both the catalog and the detail.
  // Defer to the child Outlet whenever the path is deeper than /admin/devices.
  const location = useLocation();
  const isDetail = location.pathname.replace(/\/$/, '') !== '/admin/devices';
  if (isDetail) return <Outlet />;
  return <DevicesListing />;
}

function DevicesListing() {
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<AttendanceDeviceDto | null>(null);
  const [pwTarget, setPwTarget] = useState<AttendanceDeviceDto | null>(null);
  const [detailTarget, setDetailTarget] = useState<AttendanceDeviceDto | null>(null);

  const [searchInput, setSearchInput] = useState('');
  const debouncedSearch = useDebounced(searchInput.trim().toLowerCase(), 200);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [scope, setScope] = useState<ScopeFilter>('active');

  const devicesQuery = useQuery({
    queryKey: DEVICES_QUERY_KEY,
    queryFn: ({ signal }) =>
      devicesApi.list({ includeInactive: true }, signal),
  });

  const all = devicesQuery.data ?? [];
  const activeCount = all.filter((d) => d.active).length;
  const onlineCount = all.filter((d) => d.active && d.status === 'ONLINE').length;
  const issueCount = all.filter(
    (d) => d.active && (d.status === 'OFFLINE' || d.status === 'ERROR'),
  ).length;
  const unmatchedTotal = all
    .filter((d) => d.active)
    .reduce((acc, d) => acc + (d.unmatchedCount ?? 0), 0);
  const enrollmentDevice = all.find((d) => d.enrollment && d.active);

  const filtered = all.filter((d) => {
    if (scope === 'active' && !d.active) return false;
    if (statusFilter !== 'ALL' && d.status !== statusFilter) return false;
    if (debouncedSearch) {
      const haystack = [
        d.code,
        d.name,
        d.location ?? '',
        d.deviceIp ?? '',
      ]
        .join(' ')
        .toLowerCase();
      if (!haystack.includes(debouncedSearch)) return false;
    }
    return true;
  });

  const filtersDirty =
    searchInput.trim() !== '' || statusFilter !== 'ALL' || scope !== 'active';

  const resetFilters = () => {
    setSearchInput('');
    setStatusFilter('ALL');
    setScope('active');
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="Admin"
        title="Devices"
        description="Catalog of biometric kiosks and fingerprint readers. The poller dials these on its cycle and pushes punches + user enrollments. The enrollment device is where HR captures new fingerprints and faces."
        actions={
          <Space>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} />}
                onClick={() => devicesQuery.refetch()}
                loading={devicesQuery.isFetching}
                aria-label="Refresh"
              />
            </Tooltip>
            <Can I="manage" a="attendance">
              <Button
                type="primary"
                icon={<Plus size={14} />}
                onClick={() => setCreateOpen(true)}
              >
                New device
              </Button>
            </Can>
          </Space>
        }
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="primary"
          accentCorner="tr"
          icon={MonitorSmartphone}
          label="Active devices"
          value={`${activeCount}`}
          delta={
            all.length > activeCount
              ? `${all.length - activeCount} archived`
              : undefined
          }
        />
        <StatCard
          variant="plain"
          tone="success"
          icon={Wifi}
          label="Online now"
          value={`${onlineCount}`}
          delta="last heartbeat OK"
        />
        <StatCard
          variant="plain"
          tone={issueCount > 0 ? 'warning' : 'neutral'}
          icon={issueCount > 0 ? CircleAlert : WifiOff}
          label="Need attention"
          value={`${issueCount}`}
          delta="offline or error"
        />
        <StatCard
          variant="plain"
          tone={unmatchedTotal > 0 ? 'warning' : 'neutral'}
          icon={Inbox}
          label="Unmatched punches"
          value={unmatchedTotal.toLocaleString('en-PK')}
          delta={
            enrollmentDevice
              ? `Enrollment: ${enrollmentDevice.code}`
              : 'awaiting card-to-employee mapping'
          }
        />
      </KpiGrid>

      <Section
        title="Catalog"
        subtitle={
          filtersDirty
            ? `${filtered.length} of ${all.length} device${all.length === 1 ? '' : 's'} match`
            : `${all.length} device${all.length === 1 ? '' : 's'} on file`
        }
      >
        <FilterBar
          search={{
            value: searchInput,
            onChange: setSearchInput,
            placeholder: 'Search code, name, location, IP…',
          }}
          filters={
            <>
              <Select<StatusFilter>
                value={statusFilter}
                onChange={setStatusFilter}
                style={{ width: 160 }}
                options={[
                  { value: 'ALL', label: 'All statuses' },
                  { value: 'ONLINE', label: 'Online' },
                  { value: 'OFFLINE', label: 'Offline' },
                  { value: 'ERROR', label: 'Error' },
                  { value: 'UNKNOWN', label: 'Unknown' },
                ]}
              />
              <Segmented<ScopeFilter>
                value={scope}
                onChange={(v) => setScope(v)}
                options={[
                  { value: 'active', label: 'Active only' },
                  { value: 'all', label: 'All' },
                ]}
                size="middle"
              />
            </>
          }
          onReset={filtersDirty ? resetFilters : undefined}
          style={{ marginBottom: 'var(--app-space-md)' }}
        />

        <RequestState
          loading={devicesQuery.isLoading}
          error={devicesQuery.error}
          empty={!devicesQuery.isLoading && filtered.length === 0}
          errorFallback={(err) => (
            <Alert
              type={isForbidden(err) ? 'info' : 'error'}
              showIcon
              title={forbiddenOr(
                err,
                "You don't have permission to view devices.",
                "Couldn't load devices",
              )}
              description={getErrorMessage(err)}
            />
          )}
          emptyFallback={
            all.length === 0 ? (
              <EmptyState
                icon={MonitorSmartphone}
                title="No devices yet"
                description="Add the first kiosk so the poller can start picking up punches."
                action={
                  <Can I="manage" a="attendance">
                    <Button
                      type="primary"
                      icon={<Plus size={14} />}
                      onClick={() => setCreateOpen(true)}
                    >
                      Add a device
                    </Button>
                  </Can>
                }
              />
            ) : (
              <EmptyState
                icon={MonitorSmartphone}
                title="No matches"
                description="No devices fit the current filters."
                action={<Button onClick={resetFilters}>Clear filters</Button>}
              />
            )
          }
        >
          <DevicesTable
            data={filtered}
            onEdit={setEditing}
            onSetPassword={setPwTarget}
            onOpen={setDetailTarget}
          />
        </RequestState>
      </Section>

      <CreateDeviceDrawer
        open={createOpen}
        onClose={() => setCreateOpen(false)}
      />
      <EditDeviceDrawer
        device={editing}
        onClose={() => setEditing(null)}
      />
      <CommPasswordDrawer
        device={pwTarget}
        onClose={() => setPwTarget(null)}
      />
      <DeviceDetailDrawer
        device={detailTarget}
        onClose={() => setDetailTarget(null)}
      />
    </div>
  );
}

/* ------------------------------ Table --------------------------------- */

function DevicesTable({
  data,
  onEdit,
  onSetPassword,
  onOpen,
}: {
  data: AttendanceDeviceDto[];
  onEdit: (d: AttendanceDeviceDto) => void;
  onSetPassword: (d: AttendanceDeviceDto) => void;
  onOpen: (d: AttendanceDeviceDto) => void;
}) {
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const setActiveMutation = useMutation({
    mutationFn: async (args: { row: AttendanceDeviceDto; active: boolean }) =>
      args.active
        ? devicesApi.update(args.row.id, { active: true })
        : devicesApi.deactivate(args.row.id),
    onSuccess: (_v, vars) => {
      message.success(
        vars.active
          ? `Reactivated ${vars.row.code}`
          : `Deactivated ${vars.row.code}`,
      );
      queryClient.invalidateQueries({ queryKey: DEVICES_QUERY_KEY });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const resetHwmMutation = useMutation({
    mutationFn: (row: AttendanceDeviceDto) => devicesApi.resetHwm(row.id),
    onSuccess: (_v, row) => {
      message.success(`High-water-mark cleared on ${row.code}`);
      queryClient.invalidateQueries({ queryKey: DEVICES_QUERY_KEY });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const cols: TableColumnsType<AttendanceDeviceDto> = [
    {
      title: 'Device',
      key: 'device',
      render: (_v, row) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 2 }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <code style={{ fontSize: 12, fontWeight: 500 }}>{row.code}</code>
            {row.enrollment ? (
              <Tag color="blue" style={{ marginInlineEnd: 0 }}>
                Enrollment
              </Tag>
            ) : null}
            {!row.active ? (
              <Tag style={{ marginInlineEnd: 0 }}>Archived</Tag>
            ) : null}
          </span>
          <span style={{ fontWeight: 500, fontSize: 13 }}>{row.name}</span>
          {row.location ? (
            <Text type="secondary" style={{ fontSize: 11 }}>
              {row.location}
            </Text>
          ) : null}
        </span>
      ),
    },
    {
      title: 'Network',
      key: 'network',
      width: 170,
      render: (_v, row) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 2 }}>
          <span style={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}>
            {row.deviceIp ?? '—'}
            {row.deviceIp && row.commPort ? `:${row.commPort}` : ''}
          </span>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {row.commPasswordSet ? 'Password set' : 'No password'}
          </Text>
        </span>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 180,
      render: (_v, row) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 4 }}>
          <StatusPill tone={STATUS_TONE[row.status]} size="sm">
            {STATUS_LABEL[row.status]}
          </StatusPill>
          <Text type="secondary" style={{ fontSize: 11, fontVariantNumeric: 'tabular-nums' }}>
            {row.lastSeenAt ? formatDateTime(row.lastSeenAt) : 'never seen'}
          </Text>
        </span>
      ),
    },
    {
      title: 'Sync',
      key: 'sync',
      width: 200,
      render: (_v, row) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 4 }}>
          <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
            {row.lastPunchedAt ? (
              formatDateTime(row.lastPunchedAt)
            ) : (
              <Text type="secondary" style={{ fontSize: 12 }}>
                no punches yet
              </Text>
            )}
          </span>
          {row.unmatchedCount > 0 ? (
            <Tooltip title="Punches the poller pulled but couldn't match to an employee. Map the card numbers to clear them.">
              <Tag color="warning" style={{ marginInlineEnd: 0, alignSelf: 'flex-start' }}>
                {row.unmatchedCount.toLocaleString('en-PK')} unmatched
              </Tag>
            </Tooltip>
          ) : null}
        </span>
      ),
    },
    {
      title: '',
      key: '__actions',
      width: 140,
      align: 'right',
      render: (_v, row) => (
        <Space size={4}>
          <Tooltip title="Snapshots, sync tasks, enrolled users">
            <Button
              size="small"
              icon={<MonitorSmartphone size={13} strokeWidth={1.75} />}
              onClick={() => onOpen(row)}
            >
              Open
            </Button>
          </Tooltip>
          <Can I="manage" a="attendance">
            <RowActions
              row={row}
              onEdit={onEdit}
              onSetPassword={onSetPassword}
              onResetHwm={(r) => resetHwmMutation.mutate(r)}
              onSetActive={(r, active) => setActiveMutation.mutate({ row: r, active })}
              isResettingThisRow={
                resetHwmMutation.isPending &&
                resetHwmMutation.variables?.id === row.id
              }
              isTogglingActive={
                setActiveMutation.isPending &&
                setActiveMutation.variables?.row.id === row.id
              }
            />
          </Can>
        </Space>
      ),
    },
  ];

  return (
    <Table
      rowKey="id"
      dataSource={data}
      columns={cols}
      size="middle"
      scroll={{ x: 'max-content' }}
      pagination={{ pageSize: 20, hideOnSinglePage: true }}
    />
  );
}

/* ------------------------------ Row actions --------------------------- */

function RowActions({
  row,
  onEdit,
  onSetPassword,
  onResetHwm,
  onSetActive,
  isResettingThisRow,
  isTogglingActive,
}: {
  row: AttendanceDeviceDto;
  onEdit: (d: AttendanceDeviceDto) => void;
  onSetPassword: (d: AttendanceDeviceDto) => void;
  onResetHwm: (d: AttendanceDeviceDto) => void;
  onSetActive: (d: AttendanceDeviceDto, active: boolean) => void;
  isResettingThisRow: boolean;
  isTogglingActive: boolean;
}) {
  const [resetOpen, setResetOpen] = useState(false);
  const [deactivateOpen, setDeactivateOpen] = useState(false);

  const items: MenuProps['items'] = [
    {
      key: 'password',
      icon: <KeyRound size={13} />,
      label: 'Set comm password',
      onClick: () => onSetPassword(row),
    },
    {
      key: 'reset-hwm',
      icon: <RotateCcw size={13} />,
      label: 'Reset high-water-mark',
      onClick: () => setResetOpen(true),
    },
    { type: 'divider' },
    row.active
      ? {
          key: 'deactivate',
          icon: <PowerOff size={13} />,
          label: 'Deactivate',
          danger: true,
          onClick: () => setDeactivateOpen(true),
        }
      : {
          key: 'reactivate',
          icon: <Power size={13} />,
          label: 'Reactivate',
          onClick: () => onSetActive(row, true),
        },
  ];

  return (
    <Space size={4}>
      <Tooltip title="Edit">
        <Button
          size="small"
          icon={<Edit3 size={13} />}
          onClick={() => onEdit(row)}
          aria-label={`Edit ${row.code}`}
        />
      </Tooltip>
      <Dropdown menu={{ items }} trigger={['click']} placement="bottomRight">
        <Button
          size="small"
          icon={<MoreHorizontal size={14} />}
          loading={isResettingThisRow || isTogglingActive}
          aria-label={`More actions for ${row.code}`}
        />
      </Dropdown>

      {/* Hidden Popconfirms wired to the dropdown items via open state */}
      <Popconfirm
        title="Reset high-water-mark?"
        description={
          <span style={{ display: 'block', maxWidth: 280 }}>
            Clears <code>lastPunchedAt</code>. The next poller cycle will pull
            every record currently on the device. Use after a clock reset or
            to re-ingest late-bridged punches.
          </span>
        }
        okText="Reset"
        open={resetOpen}
        onOpenChange={(o) => setResetOpen(o)}
        onConfirm={() => {
          setResetOpen(false);
          onResetHwm(row);
        }}
        onCancel={() => setResetOpen(false)}
      >
        <span aria-hidden="true" />
      </Popconfirm>
      <Popconfirm
        title="Deactivate device?"
        description={
          <span style={{ display: 'block', maxWidth: 260 }}>
            Existing punches keep their reference. The poller will skip this
            device until you reactivate it.
          </span>
        }
        okText="Deactivate"
        okButtonProps={{ danger: true }}
        open={deactivateOpen}
        onOpenChange={(o) => setDeactivateOpen(o)}
        onConfirm={() => {
          setDeactivateOpen(false);
          onSetActive(row, false);
        }}
        onCancel={() => setDeactivateOpen(false)}
      >
        <span aria-hidden="true" />
      </Popconfirm>
    </Space>
  );
}

/* --------------------------- Create drawer ---------------------------- */

type CreateForm = {
  code: string;
  name: string;
  location?: string;
  deviceIp?: string;
  commPort: number;
  enrollment: boolean;
};

function CreateDeviceDrawer({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const [form] = Form.useForm<CreateForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const mutation = useMutation({
    mutationFn: (body: CreateAttendanceDeviceRequest) =>
      devicesApi.create(body),
    onSuccess: (d) => {
      message.success(`Created device ${d.code}`);
      queryClient.invalidateQueries({ queryKey: DEVICES_QUERY_KEY });
      form.resetFields();
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <DrawerForm<CreateForm>
      open={open}
      onClose={onClose}
      title="New device"
      form={form}
      submitText="Create"
      submitting={mutation.isPending}
      initialValues={{
        commPort: 4370,
        enrollment: false,
      }}
      onFinish={(v) =>
        mutation.mutate({
          code: v.code.trim().toUpperCase(),
          name: v.name.trim(),
          location: v.location?.trim() || undefined,
          deviceIp: v.deviceIp?.trim() || undefined,
          commPort: v.commPort,
          enrollment: v.enrollment,
        })
      }
    >
      <Form.Item
        label="Code"
        name="code"
        rules={[
          { required: true, message: 'Pick a short code' },
          { max: 32, message: 'Max 32 characters' },
        ]}
        extra="Stable identifier — immutable after create. Normalized to upper-case."
      >
        <Input
          placeholder="RND_RECEPTION"
          style={{ fontFamily: 'ui-monospace, monospace', textTransform: 'uppercase' }}
          maxLength={32}
        />
      </Form.Item>

      <Form.Item
        label="Name"
        name="name"
        rules={[
          { required: true, message: 'Name the device' },
          { max: 128 },
        ]}
      >
        <Input placeholder="Reception kiosk" maxLength={128} />
      </Form.Item>

      <Form.Item label="Location" name="location" rules={[{ max: 128 }]}>
        <Input placeholder="HQ — Ground floor" maxLength={128} />
      </Form.Item>

      <Space.Compact style={{ width: '100%' }}>
        <Form.Item label="IP address" name="deviceIp" style={{ flex: 2 }} rules={[{ max: 64 }]}>
          <Input placeholder="10.10.20.5" maxLength={64} />
        </Form.Item>
        <Form.Item
          label="Port"
          name="commPort"
          style={{ flex: 1 }}
          rules={[{ required: true, message: 'Required' }]}
          extra="ZKTeco default 4370"
        >
          <InputNumber min={1} max={65535} style={{ width: '100%' }} />
        </Form.Item>
      </Space.Compact>

      <Form.Item
        label="Enrollment device"
        name="enrollment"
        valuePropName="checked"
        extra="Mark the single device HR uses to capture new fingerprints / faces. Captured prints are stored centrally — push them onto each device from that device's Enrolled users tab."
      >
        <Switch />
      </Form.Item>
    </DrawerForm>
  );
}

/* ----------------------------- Edit drawer ---------------------------- */

type EditForm = {
  name: string;
  location?: string;
  deviceIp?: string;
  commPort: number;
  enrollment: boolean;
};

function EditDeviceDrawer({
  device,
  onClose,
}: {
  device: AttendanceDeviceDto | null;
  onClose: () => void;
}) {
  const [form] = Form.useForm<EditForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  useEffect(() => {
    if (device) {
      form.setFieldsValue({
        name: device.name,
        location: device.location ?? undefined,
        deviceIp: device.deviceIp ?? undefined,
        commPort: device.commPort ?? 4370,
        enrollment: device.enrollment,
      });
    }
  }, [device, form]);

  const mutation = useMutation({
    mutationFn: (body: UpdateAttendanceDeviceRequest) => {
      if (!device) throw new Error('No device selected');
      return devicesApi.update(device.id, body);
    },
    onSuccess: (d) => {
      message.success(`Updated ${d.code}`);
      queryClient.invalidateQueries({ queryKey: DEVICES_QUERY_KEY });
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <DrawerForm<EditForm>
      open={!!device}
      onClose={onClose}
      title={device ? `Edit ${device.code}` : 'Edit device'}
      form={form}
      submitText="Save"
      submitting={mutation.isPending}
      onFinish={(v) =>
        mutation.mutate({
          name: v.name.trim(),
          location: v.location?.trim() || undefined,
          deviceIp: v.deviceIp?.trim() || undefined,
          commPort: v.commPort,
          enrollment: v.enrollment,
        })
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        title="Code is immutable"
        description="Existing punches reference this device by code; create a new row if the code is wrong."
      />

      <Form.Item
        label="Name"
        name="name"
        rules={[
          { required: true, message: 'Name the device' },
          { max: 128 },
        ]}
      >
        <Input maxLength={128} />
      </Form.Item>

      <Form.Item label="Location" name="location" rules={[{ max: 128 }]}>
        <Input maxLength={128} />
      </Form.Item>

      <Space.Compact style={{ width: '100%' }}>
        <Form.Item label="IP address" name="deviceIp" style={{ flex: 2 }} rules={[{ max: 64 }]}>
          <Input placeholder="10.10.20.5" maxLength={64} />
        </Form.Item>
        <Form.Item
          label="Port"
          name="commPort"
          style={{ flex: 1 }}
          rules={[{ required: true, message: 'Required' }]}
        >
          <InputNumber min={1} max={65535} style={{ width: '100%' }} />
        </Form.Item>
      </Space.Compact>

      <Form.Item
        label="Enrollment device"
        name="enrollment"
        valuePropName="checked"
        extra="Only one device should carry this flag."
      >
        <Switch />
      </Form.Item>
    </DrawerForm>
  );
}

/* ----------------------- Comm-password drawer ------------------------- */

type CommPasswordForm = {
  commPassword: number;
};

function CommPasswordDrawer({
  device,
  onClose,
}: {
  device: AttendanceDeviceDto | null;
  onClose: () => void;
}) {
  const [form] = Form.useForm<CommPasswordForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  useEffect(() => {
    if (device) form.resetFields();
  }, [device, form]);

  const mutation = useMutation({
    mutationFn: (body: CommPasswordForm) => {
      if (!device) throw new Error('No device selected');
      return devicesApi.setCommPassword(device.id, body);
    },
    onSuccess: (_v, vars) => {
      message.success(
        vars.commPassword === 0
          ? 'Comm password cleared.'
          : 'Comm password updated.',
      );
      queryClient.invalidateQueries({ queryKey: DEVICES_QUERY_KEY });
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <DrawerForm<CommPasswordForm>
      open={!!device}
      onClose={onClose}
      title={device ? `Comm password — ${device.code}` : 'Comm password'}
      form={form}
      submitText="Save password"
      submitting={mutation.isPending}
      initialValues={{ commPassword: 0 }}
      onFinish={(v) => mutation.mutate({ commPassword: v.commPassword })}
    >
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        title="Write-only"
        description="The current value is never returned in any read. Clearing requires sending 0."
      />

      <Form.Item
        label="Numeric password"
        name="commPassword"
        rules={[{ required: true, message: 'Enter a value (0 to clear)' }]}
        extra="Numeric ZK comm password. Range 0–999999. Send 0 to clear."
      >
        <InputNumber
          min={0}
          max={999999}
          style={{ width: '100%' }}
          autoFocus
        />
      </Form.Item>
    </DrawerForm>
  );
}
