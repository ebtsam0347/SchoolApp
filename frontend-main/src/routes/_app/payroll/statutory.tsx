// /payroll/statutory — payroll config + fiscal-year admin.
//
// Two side-by-side concerns: the effective-dated payroll config row
// (EOBI / PESSI / gratuity thresholds + canteen rates) and the FY rolling
// state (FUTURE → CURRENT → CLOSED). Inserting a new config row closes
// the previous one.

import { createFileRoute } from '@tanstack/react-router';
import {
  useIsFetching,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Landmark, Plus, RefreshCcw } from 'lucide-react';

import {
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
  FY_STATUS_LABEL,
  FY_STATUS_TONE,
  fiscalYearsApi,
  formatDateShort,
  payrollConfigApi,
  type FiscalYearDto,
  type FiscalYearStatus,
  type PayrollConfigDto,
} from '@/payroll';

const { Text } = Typography;

export const Route = createFileRoute('/_app/payroll/statutory')({
  component: StatutoryPage,
});

function StatutoryPage() {
  const queryClient = useQueryClient();
  const fetching =
    useIsFetching({
      predicate: (q) => {
        const root = q.queryKey[0];
        return root === 'payroll'
          ? q.queryKey[1] === 'config' || q.queryKey[1] === 'fiscal-years'
          : false;
      },
    }) > 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow="Money"
        title="Payroll config & fiscal years"
        description="System-wide rates (EOBI, PESSI, gratuity thresholds, canteen) and the rolling fiscal-year state."
        actions={
          <Tooltip title="Refresh">
            <Button
              icon={<RefreshCcw size={14} strokeWidth={1.75} />}
              loading={fetching}
              onClick={() => {
                void queryClient.invalidateQueries({ queryKey: ['payroll', 'config'] });
                void queryClient.invalidateQueries({ queryKey: ['payroll', 'fiscal-years'] });
              }}
              aria-label="Refresh"
            />
          </Tooltip>
        }
      />

      <PayrollConfigSection />
      <FiscalYearsSection />
    </div>
  );
}

/* ------------------------- Payroll config ---------------------------- */

function PayrollConfigSection() {
  const currentQuery = useQuery({
    queryKey: ['payroll', 'config', 'current'],
    queryFn: ({ signal }) => payrollConfigApi.current(signal),
  });
  const historyQuery = useQuery({
    queryKey: ['payroll', 'config', 'history'],
    queryFn: ({ signal }) => payrollConfigApi.history(signal),
  });
  const [openCreate, setOpenCreate] = useState(false);

  const current = currentQuery.data;

  const columns: TableColumnsType<PayrollConfigDto> = [
    {
      title: 'Effective',
      key: 'effective',
      render: (_v, r) => (
        <span>
          {formatDateShort(r.effectiveFrom)} → {r.effectiveTo ? formatDateShort(r.effectiveTo) : '—'}
        </span>
      ),
    },
    {
      title: 'EOBI',
      dataIndex: 'eobiAmount',
      key: 'eobiAmount',
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} />,
    },
    {
      title: 'PESSI',
      key: 'pessi',
      align: 'right',
      render: (_v, r) => (
        <span>
          {r.pessiRatePercent}% on cap{' '}
          <MoneyCell amount={r.pessiSalaryCap} />
        </span>
      ),
    },
    {
      title: 'Gratuity min / partial',
      key: 'gratuity',
      render: (_v, r) =>
        `${r.gratuityMinServiceMonths}mo min · ${r.gratuityPartialYearThresholdMonths}mo partial`,
    },
    {
      title: 'Canteen (mgmt / other)',
      key: 'canteen',
      align: 'right',
      render: (_v, r) => (
        <span>
          {r.canteenPerDayRateManagement != null ? (
            <MoneyCell amount={r.canteenPerDayRateManagement} />
          ) : (
            '—'
          )}
          {' / '}
          {r.canteenPerDayRateOther != null ? (
            <MoneyCell amount={r.canteenPerDayRateOther} />
          ) : (
            '—'
          )}
        </span>
      ),
    },
    {
      title: 'Notes',
      dataIndex: 'notes',
      key: 'notes',
      ellipsis: true,
    },
  ];

  return (
    <Section
      title="Payroll config"
      subtitle="Effective-dated. New rows close the previous one."
      actions={
        <Can I="manage" a="payroll">
          <Button
            icon={<Plus size={14} strokeWidth={1.75} />}
            onClick={() => setOpenCreate(true)}
          >
            New config row
          </Button>
        </Can>
      }
    >
      {currentQuery.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load current config"
          description={getErrorMessage(currentQuery.error)}
        />
      ) : current ? (
        <KpiGrid>
          <StatCard
            label="EOBI (employee)"
            value={<MoneyCell amount={current.eobiAmount} />}
            tone="info"
          />
          <StatCard
            label="PESSI rate (employer)"
            value={`${current.pessiRatePercent}%`}
            tone="warning"
          />
          <StatCard
            label="PESSI cap"
            value={<MoneyCell amount={current.pessiSalaryCap} compact />}
            tone="warning"
          />
          <StatCard
            label="Gratuity"
            value={`${current.gratuityMinServiceMonths}mo min`}
            tone="primary"
            icon={Landmark}
          />
          <StatCard
            label="Canteen — management"
            value={
              current.canteenPerDayRateManagement != null ? (
                <MoneyCell amount={current.canteenPerDayRateManagement} />
              ) : (
                '—'
              )
            }
            tone="neutral"
          />
          <StatCard
            label="Canteen — other"
            value={
              current.canteenPerDayRateOther != null ? (
                <MoneyCell amount={current.canteenPerDayRateOther} />
              ) : (
                '—'
              )
            }
            tone="neutral"
          />
        </KpiGrid>
      ) : null}

      <div style={{ marginTop: 16 }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          History
        </Text>
        <Table<PayrollConfigDto>
          rowKey="id"
          loading={historyQuery.isLoading}
          dataSource={historyQuery.data ?? []}
          columns={columns}
          pagination={false}
          scroll={{ x: 900 }}
          size="small"
          style={{ marginTop: 8 }}
        />
      </div>

      <CreatePayrollConfigModal open={openCreate} onClose={() => setOpenCreate(false)} />
    </Section>
  );
}

type PayrollConfigForm = {
  effectiveFrom: Dayjs;
  eobiAmountRupees: number;
  pessiRatePercent: number;
  pessiSalaryCapRupees: number;
  gratuityMinServiceMonths?: number;
  gratuityPartialYearThresholdMonths?: number;
  canteenPerDayRateManagement?: number;
  canteenPerDayRateOther?: number;
  notes?: string;
};

function CreatePayrollConfigModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const [form] = Form.useForm<PayrollConfigForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const create = useMutation({
    mutationFn: (v: PayrollConfigForm) =>
      payrollConfigApi.create({
        effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
        eobiAmount: v.eobiAmountRupees,
        pessiRatePercent: v.pessiRatePercent,
        pessiSalaryCap: v.pessiSalaryCapRupees,
        gratuityMinServiceMonths: v.gratuityMinServiceMonths,
        gratuityPartialYearThresholdMonths: v.gratuityPartialYearThresholdMonths,
        canteenPerDayRateManagement: v.canteenPerDayRateManagement,
        canteenPerDayRateOther: v.canteenPerDayRateOther,
        notes: v.notes?.trim() || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'config'] });
      message.success('Payroll config row inserted.');
      form.resetFields();
      onClose();
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });

  return (
    <Modal
      title="New payroll config row"
      open={open}
      onCancel={onClose}
      okText="Insert"
      confirmLoading={create.isPending}
      onOk={() => form.validateFields().then((v) => create.mutateAsync(v))}
      destroyOnHidden
      width={560}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          effectiveFrom: dayjs().add(1, 'month').startOf('month'),
          gratuityMinServiceMonths: 12,
          gratuityPartialYearThresholdMonths: 6,
        }}
      >
        <Form.Item
          name="effectiveFrom"
          label="Effective from"
          rules={[{ required: true }]}
        >
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item
            name="eobiAmountRupees"
            label="EOBI (Rs / month)"
            rules={[{ required: true }]}
          >
            <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
          </Form.Item>
          <Form.Item
            name="pessiRatePercent"
            label="PESSI rate (%)"
            rules={[{ required: true }, { type: 'number', min: 0, max: 100 }]}
          >
            <InputNumber style={{ width: '100%' }} min={0} max={100} step={0.1} suffix="%" />
          </Form.Item>
        </div>
        <Form.Item
          name="pessiSalaryCapRupees"
          label="PESSI cap (Rs)"
          rules={[{ required: true }]}
        >
          <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
        </Form.Item>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item
            name="gratuityMinServiceMonths"
            label="Gratuity min service (months)"
          >
            <InputNumber style={{ width: '100%' }} min={0} max={120} />
          </Form.Item>
          <Form.Item
            name="gratuityPartialYearThresholdMonths"
            label="Partial-year threshold (months)"
          >
            <InputNumber style={{ width: '100%' }} min={0} max={11} />
          </Form.Item>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item
            name="canteenPerDayRateManagement"
            label="Canteen rate — management (Rs / meal)"
            tooltip="Charges = weeks-with-canteen × (rate × 7). Defaults to 0 if blank."
          >
            <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
          </Form.Item>
          <Form.Item
            name="canteenPerDayRateOther"
            label="Canteen rate — other (Rs / meal)"
            tooltip="Charges = days × rate. Defaults to 0 if blank."
          >
            <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
          </Form.Item>
        </div>
        <Form.Item name="notes" label="Notes">
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------------- Fiscal years ----------------------------- */

function FiscalYearsSection() {
  const fyQuery = useQuery({
    queryKey: ['payroll', 'fiscal-years'],
    queryFn: ({ signal }) => fiscalYearsApi.list(signal),
  });
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const [openCreate, setOpenCreate] = useState(false);

  const update = useMutation({
    mutationFn: ({ code, status }: { code: string; status: FiscalYearStatus }) =>
      fiscalYearsApi.update(code, { status }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'fiscal-years'] });
      message.success('Status updated.');
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const rows = fyQuery.data ?? [];

  const columns: TableColumnsType<FiscalYearDto> = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      render: (v: string) => <Tag style={{ fontFamily: 'ui-monospace' }}>{v}</Tag>,
    },
    {
      title: 'Range',
      key: 'range',
      render: (_v, r) =>
        `${formatDateShort(r.startDate)} → ${formatDateShort(r.endDate)}`,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: FiscalYearStatus) => (
        <StatusPill tone={FY_STATUS_TONE[s]}>{FY_STATUS_LABEL[s]}</StatusPill>
      ),
    },
    {
      title: 'Set status',
      key: 'setStatus',
      render: (_v, r) => (
        <Can I="manage" a="payroll">
          <Space size={4}>
            {r.status !== 'CURRENT' && (
              <Popconfirm
                title="Mark this year as CURRENT? Only one year may be current at a time."
                onConfirm={() => update.mutateAsync({ code: r.code, status: 'CURRENT' })}
              >
                <Button size="small">Make current</Button>
              </Popconfirm>
            )}
            {r.status !== 'CLOSED' && (
              <Popconfirm
                title="Close this year?"
                onConfirm={() => update.mutateAsync({ code: r.code, status: 'CLOSED' })}
              >
                <Button size="small">Close</Button>
              </Popconfirm>
            )}
          </Space>
        </Can>
      ),
    },
  ];

  return (
    <Section
      title="Fiscal years"
      subtitle="Pakistan fiscal year is July–June. Exactly one row may be CURRENT at a time."
      actions={
        <Can I="manage" a="payroll">
          <Button
            icon={<Plus size={14} strokeWidth={1.75} />}
            onClick={() => setOpenCreate(true)}
          >
            New year
          </Button>
        </Can>
      }
    >
      {fyQuery.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load fiscal years"
          description={getErrorMessage(fyQuery.error)}
        />
      ) : (
        <Table<FiscalYearDto>
          rowKey="code"
          loading={fyQuery.isLoading}
          dataSource={rows}
          columns={columns}
          pagination={false}
        />
      )}

      <CreateFiscalYearModal open={openCreate} onClose={() => setOpenCreate(false)} />
    </Section>
  );
}

type FyForm = { code: string; range: [Dayjs, Dayjs] };

function CreateFiscalYearModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const [form] = Form.useForm<FyForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const create = useMutation({
    mutationFn: (v: FyForm) =>
      fiscalYearsApi.create({
        code: v.code.trim(),
        startDate: v.range[0].format('YYYY-MM-DD'),
        endDate: v.range[1].format('YYYY-MM-DD'),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'fiscal-years'] });
      message.success('Fiscal year created.');
      form.resetFields();
      onClose();
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });

  return (
    <Modal
      title="New fiscal year"
      open={open}
      onCancel={onClose}
      okText="Create"
      confirmLoading={create.isPending}
      onOk={() => form.validateFields().then((v) => create.mutateAsync(v))}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="code"
          label="Code"
          tooltip="Format: YYYY-YY, e.g. 2026-27."
          rules={[
            { required: true },
            { pattern: /^\d{4}-\d{2}$/, message: 'Format YYYY-YY (e.g. 2026-27).' },
          ]}
        >
          <Input placeholder="2026-27" />
        </Form.Item>
        <Form.Item
          name="range"
          label="Date range"
          rules={[{ required: true }]}
          tooltip="Typically Jul 1 → Jun 30."
        >
          <DatePicker.RangePicker style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
