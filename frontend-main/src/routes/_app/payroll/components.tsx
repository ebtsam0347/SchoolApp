// /payroll/components — salary component catalog.
//
// Flat catalog of EARNING + DEDUCTION rows. Each employee picks from this
// catalog (per-employee amounts live in employee_salary_components — the
// employee detail page edits those, not this one).
//
// Permissions: payroll.components.read to see, .write to create/update.
// Updates are name / defaultAmount / displayOrder / active. Code + type are
// immutable after creation by design (rebuilding history is harder than
// adding a new code).

import { createFileRoute } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Space,
  Switch,
  Table,
  Tooltip,
  type TableColumnsType,
} from 'antd';
import { Plus, RefreshCcw, Sliders } from 'lucide-react';

import {
  EmptyState,
  KpiGrid,
  MoneyCell,
  PageHeader,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { Can } from '@/auth';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import {
  COMPONENT_CALC_LABEL,
  COMPONENT_TYPE_LABEL,
  COMPONENT_TYPE_TONE,
  salaryComponentsApi,
  type ComponentCalcType,
  type ComponentType,
  type SalaryComponentDto,
} from '@/payroll';

export const Route = createFileRoute('/_app/payroll/components')({
  component: SalaryComponentsPage,
});

type Filter = 'ALL' | ComponentType;

function SalaryComponentsPage() {
  const [filter, setFilter] = useState<Filter>('ALL');
  const [openCreate, setOpenCreate] = useState(false);
  const [editing, setEditing] = useState<SalaryComponentDto | null>(null);

  const listQuery = useQuery({
    queryKey: ['payroll', 'components'],
    queryFn: ({ signal }) => salaryComponentsApi.list({}, signal),
  });

  const all = listQuery.data ?? [];
  const filtered = filter === 'ALL' ? all : all.filter((c) => c.type === filter);
  const earningsCount = all.filter((c) => c.type === 'EARNING' && c.active).length;
  const deductionsCount = all.filter((c) => c.type === 'DEDUCTION' && c.active).length;
  const inactive = all.filter((c) => !c.active).length;

  const columns: TableColumnsType<SalaryComponentDto> = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      render: (v: string) => (
        <span style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
          {v}
        </span>
      ),
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (v: string) => <span style={{ fontWeight: 500 }}>{v}</span>,
    },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      render: (v: ComponentType) => (
        <StatusPill tone={COMPONENT_TYPE_TONE[v]}>
          {COMPONENT_TYPE_LABEL[v]}
        </StatusPill>
      ),
    },
    {
      title: 'Calc',
      dataIndex: 'calcType',
      key: 'calcType',
      render: (v: ComponentCalcType) => COMPONENT_CALC_LABEL[v],
    },
    {
      title: 'Default amount',
      dataIndex: 'defaultAmount',
      key: 'defaultAmount',
      align: 'right',
      render: (v: number | null) =>
        v != null ? <MoneyCell amount={v} /> : '—',
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      render: (v: boolean) =>
        v ? (
          <StatusPill tone="success">Active</StatusPill>
        ) : (
          <StatusPill tone="neutral">Disabled</StatusPill>
        ),
    },
    {
      title: 'Calculate with salary',
      dataIndex: 'calculateWithSalary',
      key: 'calculateWithSalary',
      render: (v: boolean) =>
        v ? (
          <StatusPill tone="success">Yes</StatusPill>
        ) : (
          <StatusPill tone="neutral">No</StatusPill>
        ),
    },
    {
      title: 'Order',
      dataIndex: 'displayOrder',
      key: 'displayOrder',
      width: 80,
    },
    {
      title: '',
      key: 'edit',
      width: 80,
      render: (_v, row) => (
        <Can I="manage" a="payroll">
          <Button size="small" onClick={() => setEditing(row)}>
            Edit
          </Button>
        </Can>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow="Money"
        title="Salary components"
        description="Flat catalog of earnings and deductions. Each employee picks lines from here; per-employee amounts live on the employee profile."
        actions={
          <Space>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => listQuery.refetch()}
                loading={listQuery.isFetching}
                aria-label="Refresh"
              />
            </Tooltip>
            <Can I="manage" a="payroll">
              <Button
                type="primary"
                icon={<Plus size={14} strokeWidth={1.75} />}
                onClick={() => setOpenCreate(true)}
              >
                New component
              </Button>
            </Can>
          </Space>
        }
      />

      <KpiGrid>
        <StatCard label="Earnings" value={earningsCount.toString()} tone="success" />
        <StatCard
          label="Deductions"
          value={deductionsCount.toString()}
          tone="warning"
        />
        <StatCard label="Disabled" value={inactive.toString()} tone="neutral" />
      </KpiGrid>

      <Section
        title="Catalog"
        actions={
          <Segmented<Filter>
            value={filter}
            onChange={setFilter}
            options={[
              { label: 'All', value: 'ALL' },
              { label: 'Earnings', value: 'EARNING' },
              { label: 'Deductions', value: 'DEDUCTION' },
            ]}
          />
        }
      >
        {listQuery.isError ? (
          <Alert
            type="error"
            showIcon
            title="Couldn't load components"
            description={getErrorMessage(listQuery.error)}
          />
        ) : filtered.length === 0 && !listQuery.isLoading ? (
          <EmptyState
            icon={Sliders}
            title="No components"
            description="Create the first component (e.g. HRA) to start composing employee salaries."
          />
        ) : (
          <Table<SalaryComponentDto>
            rowKey="id"
            loading={listQuery.isLoading}
            dataSource={filtered}
            columns={columns}
            pagination={false}
            // Scroll wide columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
          />
        )}
      </Section>

      <CreateComponentModal
        open={openCreate}
        onClose={() => setOpenCreate(false)}
      />
      <EditComponentModal
        component={editing}
        onClose={() => setEditing(null)}
      />
    </div>
  );
}

/* --------------------- Create component modal ------------------------- */

type CreateForm = {
  code: string;
  name: string;
  type: ComponentType;
  calcType: ComponentCalcType;
  defaultAmountRupees?: number;
  displayOrder?: number;
  calculateWithSalary?: boolean;
};

function CreateComponentModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const [form] = Form.useForm<CreateForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const create = useMutation({
    mutationFn: (v: CreateForm) =>
      salaryComponentsApi.create({
        code: v.code.trim().toUpperCase(),
        name: v.name.trim(),
        type: v.type,
        calcType: v.calcType,
        defaultAmount: v.defaultAmountRupees,
        displayOrder: v.displayOrder,
        calculateWithSalary: v.calculateWithSalary,
      }),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'components'] });
      message.success(`Created ${created.code}.`);
      form.resetFields();
      onClose();
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });

  const watchedType = Form.useWatch('type', form);

  return (
    <Modal
      title="New salary component"
      open={open}
      onCancel={onClose}
      okText="Create"
      confirmLoading={create.isPending}
      onOk={() => form.validateFields().then((v) => create.mutateAsync(v))}
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          type: 'EARNING',
          calcType: 'FLAT',
          displayOrder: 0,
          calculateWithSalary: false,
        }}
      >
        <Form.Item
          name="code"
          label="Code"
          tooltip="Stable short code (HRA, CONVEYANCE). Immutable after creation."
          rules={[
            { required: true, message: 'Code is required.' },
            { max: 32 },
            {
              pattern: /^[A-Z][A-Z0-9_]*$/i,
              message: 'Use letters, digits, underscore. Will be upper-cased.',
            },
          ]}
        >
          <Input placeholder="HRA" autoFocus />
        </Form.Item>
        <Form.Item
          name="name"
          label="Display name"
          rules={[{ required: true, message: 'Name is required.' }, { max: 64 }]}
        >
          <Input placeholder="House Rent Allowance" />
        </Form.Item>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item
            name="type"
            label="Type"
            rules={[{ required: true }]}
          >
            <Select
              options={[
                { label: 'Earning', value: 'EARNING' },
                { label: 'Deduction', value: 'DEDUCTION' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="calcType"
            label="Calculation"
            rules={[{ required: true }]}
            tooltip="PER_DAY is restricted to earnings (per-day deductions don't model anything real)."
          >
            <Select
              options={[
                { label: 'Flat (monthly)', value: 'FLAT' },
                {
                  label: 'Per day × days present',
                  value: 'PER_DAY',
                  disabled: watchedType === 'DEDUCTION',
                },
              ]}
            />
          </Form.Item>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 12 }}>
          <Form.Item name="defaultAmountRupees" label="Default amount (Rs)" tooltip="Hint HR can override per employee.">
            <InputNumber
              style={{ width: '100%' }}
              min={0}
              step={500}
              addonBefore="Rs"
            />
          </Form.Item>
          <Form.Item name="displayOrder" label="Order">
            <InputNumber style={{ width: '100%' }} min={0} max={9999} />
          </Form.Item>
        </div>
        <Form.Item
          name="calculateWithSalary"
          label="Calculate with salary"
          valuePropName="checked"
          tooltip="Enable to include this component when computing salary."
        >
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* ---------------------- Edit component modal -------------------------- */

type EditForm = {
  name: string;
  defaultAmountRupees?: number;
  displayOrder: number;
  calculateWithSalary: boolean;
  active: boolean;
};

function EditComponentModal({
  component,
  onClose,
}: {
  component: SalaryComponentDto | null;
  onClose: () => void;
}) {
  const [form] = Form.useForm<EditForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const update = useMutation({
    mutationFn: (v: EditForm) =>
      salaryComponentsApi.update(component?.id ?? 0, {
        name: v.name.trim(),
        defaultAmount: v.defaultAmountRupees,
        displayOrder: v.displayOrder,
        calculateWithSalary: v.calculateWithSalary,
        active: v.active,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'components'] });
      message.success('Saved.');
      onClose();
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });

  return (
    <Modal
      title={component ? `Edit ${component.code}` : 'Edit'}
      open={component !== null}
      onCancel={onClose}
      okText="Save"
      confirmLoading={update.isPending}
      onOk={() => form.validateFields().then((v) => update.mutateAsync(v))}
      destroyOnHidden
    >
      {component && (
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            name: component.name,
            defaultAmountRupees: component.defaultAmount ?? undefined,
            displayOrder: component.displayOrder,
            calculateWithSalary: component.calculateWithSalary,
            active: component.active,
          }}
        >
          <Form.Item
            name="name"
            label="Display name"
            rules={[{ required: true, max: 64 }]}
          >
            <Input />
          </Form.Item>
          <Form.Item label="Default amount (Rs)" name="defaultAmountRupees">
            <InputNumber
              style={{ width: '100%' }}
              min={0}
              step={500}
              addonBefore="Rs"
            />
          </Form.Item>
          <Form.Item label="Order" name="displayOrder">
            <InputNumber style={{ width: '100%' }} min={0} max={9999} />
          </Form.Item>
          <Form.Item
            label="Calculate with salary"
            name="calculateWithSalary"
            valuePropName="checked"
            tooltip="Enable to include this component when computing salary."
          >
            <Switch />
          </Form.Item>
          <Form.Item label="Active" name="active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}
