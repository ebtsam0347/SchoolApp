import { createFileRoute } from '@tanstack/react-router';
import {
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd';

import {
  leavesApi,
  LEAVE_CYCLE_ANCHOR_LABEL,
  type LeaveCycleAnchor,
  type LeaveTypeDto,
} from '@/attendance';
import { CatalogShell } from '@/people/CatalogShell';

const { Text } = Typography;

type LeaveTypeForm = {
  code: string;
  name: string;
  paid: boolean;
  defaultPerYear: number;
  cycleAnchor: LeaveCycleAnchor;
  prorateOnEntry: boolean;
  encashable: boolean;
  carryForwardMax: number;
  requiresApproval: boolean;
  showOnDashboard: boolean;
};

const CYCLE_ANCHOR_OPTIONS: { value: LeaveCycleAnchor; label: string }[] = [
  {
    value: 'JOINING',
    label: 'Joining anniversary — cycle renews on the join date (e.g. annual leave)',
  },
  {
    value: 'FISCAL',
    label: 'Fiscal year — cycle renews each Jul–Jun (e.g. casual)',
  },
  { value: 'NONE', label: 'Occasional — no fixed renewal' },
];

export const Route = createFileRoute('/_app/admin/lookups/leave-types')({
  component: LeaveTypesLookup,
});

function LeaveTypesLookup() {
  return (
    <CatalogShell<LeaveTypeDto, LeaveTypeForm>
      config={{
        title: 'Leave types',
        description:
          'Catalog of leave types. Code is the stable identifier — it is denormalized into balance rows, leave requests, and audit events, so it cannot be edited after creation.',
        queryKey: ['catalog', 'leave-types-all'],
        loadList: (signal) => leavesApi.types({ includeInactive: true, signal }),
        columns: [
          {
            title: 'Code',
            dataIndex: 'code',
            key: 'code',
            width: 120,
            render: (v: string) => (
              <Text code style={{ fontSize: 12 }}>
                {v}
              </Text>
            ),
          },
          { title: 'Name', dataIndex: 'name', key: 'name' },
          {
            title: 'Paid',
            dataIndex: 'paid',
            key: 'paid',
            width: 80,
            render: (v: boolean) =>
              v ? <Tag color="success">Paid</Tag> : <Tag>Unpaid</Tag>,
          },
          {
            title: 'Per year',
            dataIndex: 'defaultPerYear',
            key: 'defaultPerYear',
            width: 100,
            render: (v: number) => `${v} day${v === 1 ? '' : 's'}`,
          },
          {
            title: 'Cycle',
            dataIndex: 'cycleAnchor',
            key: 'cycleAnchor',
            width: 150,
            render: (v: LeaveCycleAnchor) => (
              <Tag color={v === 'NONE' ? 'default' : 'blue'}>
                {LEAVE_CYCLE_ANCHOR_LABEL[v]}
              </Tag>
            ),
          },
          {
            title: 'Encashable',
            dataIndex: 'encashable',
            key: 'encashable',
            width: 110,
            render: (v: boolean) =>
              v ? <Tag color="gold">Encashable</Tag> : <Tag>—</Tag>,
          },
          {
            title: 'Carry-fwd',
            dataIndex: 'carryForwardMax',
            key: 'carryForwardMax',
            width: 110,
            render: (v: number) => (v > 0 ? `${v} max` : '—'),
          },
          {
            title: 'Approval',
            dataIndex: 'requiresApproval',
            key: 'requiresApproval',
            width: 110,
            render: (v: boolean) =>
              v ? <Tag color="warning">Required</Tag> : <Tag>Auto</Tag>,
          },
          {
            title: 'Dashboard',
            dataIndex: 'showOnDashboard',
            key: 'showOnDashboard',
            width: 120,
            render: (v: boolean) =>
              v ? (
                <Tag color="success">Visible</Tag>
              ) : (
                <Tooltip title="Hidden from the dashboard balance card. Still listed in the apply-leave drawer.">
                  <Tag>Hidden</Tag>
                </Tooltip>
              ),
          },
        ],
        renderForm: (_form, editing) => <LeaveTypeFormBody editing={editing} />,
        toCreate: (v) =>
          leavesApi.createType({
            code: v.code.trim().toUpperCase(),
            name: v.name.trim(),
            paid: v.paid,
            defaultPerYear: v.defaultPerYear,
            cycleAnchor: v.cycleAnchor,
            prorateOnEntry: v.cycleAnchor === 'FISCAL' ? v.prorateOnEntry : false,
            encashable: v.encashable,
            carryForwardMax: v.carryForwardMax,
            requiresApproval: v.requiresApproval,
            showOnDashboard: v.showOnDashboard,
          }),
        toUpdate: (v, editing) =>
          leavesApi.updateType(editing.id!, {
            name: v.name.trim(),
            paid: v.paid,
            defaultPerYear: v.defaultPerYear,
            cycleAnchor: v.cycleAnchor,
            prorateOnEntry: v.cycleAnchor === 'FISCAL' ? v.prorateOnEntry : false,
            encashable: v.encashable,
            carryForwardMax: v.carryForwardMax,
            requiresApproval: v.requiresApproval,
            showOnDashboard: v.showOnDashboard,
          }),
        setActive: (row, active) =>
          active
            ? leavesApi.updateType(row.id!, { active: true })
            : leavesApi.deleteType(row.id!),
      }}
    />
  );
}

function LeaveTypeFormBody({ editing }: { editing: LeaveTypeDto | null }) {
  const cycleAnchor = Form.useWatch<LeaveCycleAnchor>('cycleAnchor');
  return (
    <>
      <Form.Item
        name="code"
        label="Code"
        rules={[
          { required: true, message: 'Code is required' },
          { max: 32, message: 'Max 32 characters' },
        ]}
        extra={
          editing
            ? 'Code is immutable — it lives on existing balance rows, leave requests, and audit events.'
            : 'Stable identifier. Normalized to upper-case on save.'
        }
      >
        <Input
          placeholder="ANNUAL"
          disabled={!!editing}
          style={{ textTransform: 'uppercase' }}
          maxLength={32}
        />
      </Form.Item>

      <Form.Item
        name="name"
        label="Name"
        rules={[
          { required: true, message: 'Name is required' },
          { max: 64, message: 'Max 64 characters' },
        ]}
      >
        <Input placeholder="Annual leave" maxLength={64} />
      </Form.Item>

      <Form.Item
        name="defaultPerYear"
        label="Default days per year"
        rules={[{ required: true, message: 'Required' }]}
        initialValue={0}
      >
        <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
      </Form.Item>

      <Form.Item
        name="cycleAnchor"
        label="Entitlement cycle"
        rules={[{ required: true, message: 'Required' }]}
        extra="When the balance renews — on the employee's joining anniversary, on the Jul–Jun fiscal year, or never (occasional)."
        initialValue="JOINING"
      >
        <Select options={CYCLE_ANCHOR_OPTIONS} />
      </Form.Item>

      {cycleAnchor === 'FISCAL' ? (
        <Form.Item
          name="prorateOnEntry"
          label="Prorate on entry"
          valuePropName="checked"
          extra="Prorate the first cycle for someone who joins mid-year."
          initialValue={false}
        >
          <Switch />
        </Form.Item>
      ) : null}

      <Form.Item
        name="encashable"
        label="Encashable"
        valuePropName="checked"
        extra="Unused balance is paid out when the cycle closes at renewal."
        initialValue={false}
      >
        <Switch />
      </Form.Item>

      <Form.Item
        name="carryForwardMax"
        label="Carry-forward max"
        extra="Max days that roll over to the next cycle. Set 0 to disable."
        initialValue={0}
      >
        <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
      </Form.Item>

      <Form.Item name="paid" label="Paid" valuePropName="checked" initialValue={true}>
        <Switch />
      </Form.Item>

      <Form.Item
        name="requiresApproval"
        label="Requires approval"
        valuePropName="checked"
        extra="When off, applications auto-approve at submit."
        initialValue={true}
      >
        <Switch />
      </Form.Item>

      <Form.Item
        name="showOnDashboard"
        label="Show on dashboard"
        valuePropName="checked"
        extra="When off, the dashboard balance card hides this type. Apply-leave drawer is unaffected."
        initialValue={true}
      >
        <Switch />
      </Form.Item>
    </>
  );
}
