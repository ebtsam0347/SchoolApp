// /admin/lookups/shifts — HR shift catalog (lookup tab).
//
// Lists active shifts (`GET /shifts`), exposes a Create drawer that wires up
// `POST /shifts`. Friday adjustments are surfaced inline — `friday_extra`
// minutes are added to break + end on Fridays only. Midnight crossing is
// derived (`endTime <= startTime`).
//
// v1 is read + create. Editing a shift is intentionally not exposed yet:
// changes don't rewrite history (attendance_records snapshot the shift on
// punch), but they do drift the catalog from what's effectively in use.
// We'll add Edit when the contract grows; for now HR can deactivate and
// recreate.

import { createFileRoute } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  TimePicker,
  Typography,
  theme as antdTheme,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Clock, LayoutGrid, MoonStar, Plus, Sunset } from 'lucide-react';

import {
  EmptyState,
  KpiGrid,
  PageHeader,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { Can } from '@/auth';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import {
  formatMinutes,
  formatShiftRange,
  shiftDurationMinutes,
  shiftsApi,
  SHIFT_PERIOD_LABEL,
  type CreateShiftRequest,
  type ShiftDto,
  type ShiftPeriod,
} from '@/attendance';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/admin/lookups/shifts')({
  component: ShiftsAdminPage,
});

function ShiftsAdminPage() {
  const [openCreate, setOpenCreate] = useState(false);

  const shiftsQuery = useQuery({
    queryKey: ['shifts'],
    queryFn: ({ signal }) => shiftsApi.list(signal),
  });

  const all = shiftsQuery.data ?? [];
  const activeCount = all.filter((s) => s.active).length;
  const fridayCount = all.filter(
    (s) => s.fridayExtraMinutes && s.fridayExtraMinutes > 0,
  ).length;
  const midnightCount = all.filter(crossesMidnight).length;

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
        eyebrow="Lookups"
        title="Shifts"
        description="The shift catalog HR uses for assignments. Sundays are weekly off everywhere; midnight crossings are derived from start/end times."
        actions={
          <Can I="manage" a="attendance">
            <Button
              type="primary"
              icon={<Plus size={14} />}
              onClick={() => setOpenCreate(true)}
            >
              New shift
            </Button>
          </Can>
        }
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="primary"
          accentCorner="tr"
          icon={LayoutGrid}
          label="Active shifts"
          value={`${activeCount}`}
          delta={
            all.length > activeCount
              ? `${all.length - activeCount} archived`
              : undefined
          }
        />
        <StatCard
          variant="plain"
          tone="info"
          icon={Sunset}
          label="Friday adjustments"
          value={`${fridayCount}`}
          delta="extra break + end on Fridays"
        />
        <StatCard
          variant="plain"
          tone="warning"
          icon={MoonStar}
          label="Cross midnight"
          value={`${midnightCount}`}
          delta="end ≤ start ⇒ next day"
        />
        <StatCard
          variant="plain"
          tone="neutral"
          icon={Clock}
          label="Total shifts"
          value={`${all.length}`}
        />
      </KpiGrid>

      <Section
        title="Catalog"
        subtitle={`${all.length} shift${all.length === 1 ? '' : 's'} on file`}
      >
        {shiftsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            title="Couldn't load shifts"
            description={getErrorMessage(shiftsQuery.error)}
          />
        ) : !shiftsQuery.isLoading && all.length === 0 ? (
          <EmptyState
            icon={LayoutGrid}
            title="No shifts yet"
            description="Add a base shift like 09:00 – 18:00 with a 60-minute break, then assign employees."
            action={
              <Can I="manage" a="attendance">
                <Button
                  type="primary"
                  icon={<Plus size={14} />}
                  onClick={() => setOpenCreate(true)}
                >
                  Create the first shift
                </Button>
              </Can>
            }
          />
        ) : (
          <ShiftTable data={all} loading={shiftsQuery.isLoading} />
        )}
      </Section>

      <CreateShiftDrawer open={openCreate} onClose={() => setOpenCreate(false)} />
    </div>
  );
}

/* ------------------------------ Table --------------------------------- */

function ShiftTable({ data, loading }: { data: ShiftDto[]; loading: boolean }) {
  const cols: TableColumnsType<ShiftDto> = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      width: 130,
      render: (c: string) => (
        <code style={{ fontSize: 12, fontWeight: 500 }}>{c}</code>
      ),
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (n: string) => <span style={{ fontWeight: 500 }}>{n}</span>,
    },
    {
      title: 'Shift Period',
      dataIndex: 'period',
      key: 'period',
      width: 120,
      render: (p: ShiftPeriod) => (
        <Tag color={p === 'NIGHT' ? 'geekblue' : 'gold'}>{SHIFT_PERIOD_LABEL[p] ?? p}</Tag>
      ),
    },
    {
      title: 'Hours',
      key: 'hours',
      width: 220,
      render: (_v, row) => (
        <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
          <span style={{ fontVariantNumeric: 'tabular-nums', fontSize: 13 }}>
            {formatShiftRange(row.startTime, row.endTime)}
          </span>
          <Text type="secondary" style={{ fontSize: 11 }}>
            Net{' '}
            {formatMinutes(
              shiftDurationMinutes(row.startTime, row.endTime, row.breakMinutes),
            )}
          </Text>
        </span>
      ),
    },
    {
      title: 'Break',
      key: 'break',
      width: 160,
      render: (_v, row) =>
        row.breakStartTime && row.breakEndTime ? (
          <span style={{ display: 'inline-flex', flexDirection: 'column' }}>
            <span style={{ fontVariantNumeric: 'tabular-nums', fontSize: 13 }}>
              {formatShiftRange(row.breakStartTime, row.breakEndTime)}
            </span>
            <Text type="secondary" style={{ fontSize: 11 }}>
              {formatMinutes(row.breakMinutes)}
            </Text>
          </span>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            No break
          </Text>
        ),
    },
    {
      title: 'Grace',
      dataIndex: 'graceMinutes',
      key: 'graceMinutes',
      width: 90,
      render: (m: number) => formatMinutes(m),
    },
    {
      title: 'Friday',
      key: 'friday',
      width: 130,
      render: (_v, row) =>
        row.fridayExtraMinutes && row.fridayExtraMinutes > 0 ? (
          <Tag color="orange">+{formatMinutes(row.fridayExtraMinutes)}</Tag>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            same as M-Th
          </Text>
        ),
    },
    {
      title: 'Crosses midnight',
      key: 'midnight',
      width: 140,
      render: (_v, row) =>
        crossesMidnight(row) ? (
          <StatusPill tone="warning" size="sm">
            Yes
          </StatusPill>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>
            No
          </Text>
        ),
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 90,
      render: (a: boolean) =>
        a ? (
          <StatusPill tone="success" size="sm">
            Active
          </StatusPill>
        ) : (
          <StatusPill tone="neutral" size="sm">
            Archived
          </StatusPill>
        ),
    },
  ];

  return (
    <Table
      rowKey="id"
      dataSource={data}
      columns={cols}
      loading={loading}
      size="middle"
      // Scroll wide columns inside the table, not the page.
      scroll={{ x: 'max-content' }}
      pagination={{
        defaultPageSize: 20,
        pageSizeOptions: [10, 20, 50, 100],
        showSizeChanger: true,
        showTotal: (total, [from, to]) => `${from}–${to} of ${total}`,
        hideOnSinglePage: true,
      }}
    />
  );
}

/* --------------------------- Create drawer ---------------------------- */

type CreateForm = {
  code: string;
  name: string;
  period: ShiftPeriod;
  startTime: Dayjs;
  endTime: Dayjs;
  breakStartTime?: Dayjs;
  breakEndTime?: Dayjs;
  graceMinutes: number;
  fridayDifferent: boolean;
  fridayExtraMinutes?: number;
};

function CreateShiftDrawer({
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
    mutationFn: (body: CreateShiftRequest) => shiftsApi.create(body),
    onSuccess: (s) => {
      message.success(`Created shift ${s.code}`);
      queryClient.invalidateQueries({ queryKey: ['shifts'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => {
      if (isApiError(err)) applyApiErrorToForm(form, err);
      message.error(getErrorMessage(err));
    },
  });

  const startTime = Form.useWatch('startTime', form);
  const endTime = Form.useWatch('endTime', form);
  const breakStart = Form.useWatch('breakStartTime', form);
  const breakEnd = Form.useWatch('breakEndTime', form);
  const fridayDifferent = Form.useWatch('fridayDifferent', form);

  const breakMins =
    breakStart && breakEnd
      ? Math.max(0, breakEnd.diff(breakStart, 'minute'))
      : 0;
  const previewMinutes =
    startTime && endTime
      ? shiftDurationMinutes(
          startTime.format('HH:mm'),
          endTime.format('HH:mm'),
          breakMins,
        )
      : 0;

  return (
    <Drawer
      title="New shift"
      open={open}
      onClose={onClose}
      size={460}
      destroyOnHidden
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" loading={mutation.isPending} onClick={() => form.submit()}>
            Create
          </Button>
        </div>
      }
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          breakStartTime: dayjs('13:00', 'HH:mm'),
          breakEndTime: dayjs('14:00', 'HH:mm'),
          graceMinutes: 10,
          fridayDifferent: false,
          startTime: dayjs('09:00', 'HH:mm'),
          endTime: dayjs('18:00', 'HH:mm'),
        }}
        onFinish={(v) =>
          mutation.mutate({
            code: v.code,
            name: v.name,
            startTime: v.startTime.format('HH:mm'),
            endTime: v.endTime.format('HH:mm'),
            breakStartTime: v.breakStartTime?.format('HH:mm'),
            breakEndTime: v.breakEndTime?.format('HH:mm'),
            graceMinutes: v.graceMinutes,
            fridayExtraMinutes: v.fridayDifferent ? v.fridayExtraMinutes : undefined,
            period: v.period,
          })
        }
      >
        <Form.Item
          label="Code"
          name="code"
          rules={[{ required: true, message: 'Pick a short code' }, { max: 32 }]}
        >
          <Input
            placeholder="GEN-09-18"
            style={{ fontFamily: 'ui-monospace, monospace' }}
          />
        </Form.Item>
        <Form.Item
          label="Name"
          name="name"
          rules={[{ required: true, message: 'Name the shift' }, { max: 64 }]}
        >
          <Input placeholder="General · 9 to 6" />
        </Form.Item>
        <Form.Item
          label="Shift Period"
          name="period"
          rules={[{ required: true, message: 'Pick a shift period' }]}
        >
          <Select<ShiftPeriod>
            placeholder="Day or Night"
            options={[
              { value: 'DAY', label: SHIFT_PERIOD_LABEL.DAY },
              { value: 'NIGHT', label: SHIFT_PERIOD_LABEL.NIGHT },
            ]}
          />
        </Form.Item>

        <Space.Compact style={{ width: '100%' }}>
          <Form.Item
            label="Shift Start Time"
            name="startTime"
            rules={[{ required: true }]}
            style={{ flex: 1 }}
          >
            <TimePicker use12Hours format="hh:mm A" minuteStep={5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            label="Shift End Time"
            name="endTime"
            rules={[{ required: true }]}
            style={{ flex: 1 }}
          >
            <TimePicker use12Hours format="hh:mm A" minuteStep={5} style={{ width: '100%' }} />
          </Form.Item>
        </Space.Compact>

        <Space.Compact style={{ width: '100%' }}>
          <Form.Item
            label="Break Start Time"
            name="breakStartTime"
            style={{ flex: 1 }}
          >
            <TimePicker use12Hours format="hh:mm A" minuteStep={5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            label="Break End Time"
            name="breakEndTime"
            style={{ flex: 1 }}
          >
            <TimePicker use12Hours format="hh:mm A" minuteStep={5} style={{ width: '100%' }} />
          </Form.Item>
        </Space.Compact>

        <Form.Item
          label="Grace (mins)"
          name="graceMinutes"
          rules={[{ required: true }]}
        >
          <InputNumber min={0} max={60} step={5} style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item
          label="Different on Fridays?"
          name="fridayDifferent"
          valuePropName="checked"
        >
          <Switch checkedChildren="Yes" unCheckedChildren="No" />
        </Form.Item>
        {fridayDifferent ? (
          <Form.Item
            label="Friday extra minutes"
            name="fridayExtraMinutes"
            help="Added to break + end on Fridays only. e.g. 90 for a Jumma break."
            rules={[{ required: true, message: 'Set the Friday delta' }]}
          >
            <InputNumber min={0} max={300} step={15} style={{ width: '100%' }} />
          </Form.Item>
        ) : null}

        <PreviewCard
          netMinutes={previewMinutes}
          start={startTime?.format('HH:mm')}
          end={endTime?.format('HH:mm')}
        />
      </Form>
    </Drawer>
  );
}

function PreviewCard({
  netMinutes,
  start,
  end,
}: {
  netMinutes: number;
  start?: string;
  end?: string;
}) {
  const { token } = useToken();
  if (!start || !end) return null;
  const crosses =
    parseTime(start) != null &&
    parseTime(end) != null &&
    parseTime(end)! <= parseTime(start)!;
  return (
    <Card
      size="small"
      style={{
        background: token.colorFillQuaternary,
        border: `1px solid ${token.colorBorderSecondary}`,
      }}
      styles={{ body: { padding: 12, fontSize: 12 } }}
    >
      <span style={{ display: 'flex', justifyContent: 'space-between' }}>
        <span style={{ color: token.colorTextSecondary }}>Net working time</span>
        <strong>{formatMinutes(netMinutes)}</strong>
      </span>
      <span
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginTop: 4,
          color: token.colorTextSecondary,
        }}
      >
        <span>Crosses midnight</span>
        <span>{crosses ? 'Yes' : 'No'}</span>
      </span>
    </Card>
  );
}

/* ------------------------------ helpers ------------------------------- */

function crossesMidnight(s: ShiftDto): boolean {
  const a = parseTime(s.startTime);
  const b = parseTime(s.endTime);
  if (a == null || b == null) return false;
  return b <= a;
}

function parseTime(t: string): number | null {
  const m = /^(\d{1,2}):(\d{2})/.exec(t);
  if (!m) return null;
  return Number(m[1]) * 60 + Number(m[2]);
}
