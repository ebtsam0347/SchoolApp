// /payroll/off-cycle — money paid outside the monthly run.
//
// Cash extras, special OT paid on the spot, ad-hoc bonuses. Rows from
// bonus-installment apply (sourceEventId set) are read-only — to fix one,
// HR must void the installment at the bonus-event level.
//
// HR-recorded rows can be created, edited, and deleted here. The list is
// paginated by the upstream service.

import { createFileRoute } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Plus, Receipt, RefreshCcw } from 'lucide-react';

import {
  EmptyState,
  MoneyCell,
  PageHeader,
  Section,
  StatusPill,
} from '@/components';
import { Can } from '@/auth';
import { applyApiErrorToForm, forbiddenOr, getErrorMessage, isApiError } from '@/lib';
import {
  formatDateShort,
  offCycleApi,
  PAYMENT_MODE_LABEL,
  payrollComposites,
  type OffCyclePaymentRowWithEmployee,
  type PaymentMode,
} from '@/payroll';
import { catalogsApi, EmployeePicker } from '@/people';

const { Text } = Typography;

export const Route = createFileRoute('/_app/payroll/off-cycle')({
  component: OffCyclePage,
});

type CategoryFilter = 'ALL' | number;
type CategoryOption = { value: number; label: string };

const PAYMENT_MODE_OPTIONS = (Object.keys(PAYMENT_MODE_LABEL) as PaymentMode[]).map((k) => ({
  value: k,
  label: PAYMENT_MODE_LABEL[k],
}));

function OffCyclePage() {
  const [category, setCategory] = useState<CategoryFilter>('ALL');
  const [employeeId, setEmployeeId] = useState<number | undefined>(undefined);
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [openCreate, setOpenCreate] = useState(false);
  const [editing, setEditing] = useState<OffCyclePaymentRowWithEmployee | null>(null);

  const listQuery = useQuery({
    queryKey: [
      'payroll',
      'off-cycle',
      'bff',
      employeeId,
      range?.[0]?.toISOString(),
      range?.[1]?.toISOString(),
    ],
    queryFn: ({ signal }) =>
      payrollComposites.offCyclePayments(
        {
          employeeId,
          from: range?.[0]?.format('YYYY-MM-DD'),
          to: range?.[1]?.format('YYYY-MM-DD'),
        },
        signal,
      ),
  });

  const pageRows = listQuery.data?.content ?? [];
  // The upstream list no longer filters by type, so narrow the loaded page here.
  const rows =
    category === 'ALL'
      ? pageRows
      : pageRows.filter((r) => r.offCyclePaymentTypeId === category);

  const categoriesQuery = useQuery({
    queryKey: ['payroll', 'off-cycle', 'categories'],
    queryFn: ({ signal }) => catalogsApi.offCyclePayments({ active: true }, signal),
    staleTime: 5 * 60_000,
  });

  const categoryOptions: CategoryOption[] = useMemo(() => {
    const types = categoriesQuery.data ?? [];
    return types
      .filter((type): type is { id: number; name: string } =>
        typeof type.id === 'number' && typeof type.name === 'string',
      )
      .map((type) => ({
        value: type.id,
        label: type.name,
      }))
      .sort((a, b) => a.label.localeCompare(b.label));
  }, [categoriesQuery.data]);

  const columns: TableColumnsType<OffCyclePaymentRowWithEmployee> = [
    {
      title: 'Paid on',
      dataIndex: 'paidOn',
      key: 'paidOn',
      render: (v: string) => formatDateShort(v),
    },
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
      title: 'Category',
      key: 'category',
      render: (_v, r) => {
        const label =
          r.offCyclePaymentTypeName ??
          categoryOptions.find((option) => option.value === r.offCyclePaymentTypeId)?.label;
        return label ? <StatusPill tone="neutral">{label}</StatusPill> : '—';
      },
    },
    {
      title: 'Mode',
      dataIndex: 'paymentMode',
      key: 'paymentMode',
      render: (v: PaymentMode | null) => (v ? PAYMENT_MODE_LABEL[v] : '—'),
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      render: (v: number) => (
        <MoneyCell amount={v} style={{ fontWeight: 600 }} />
      ),
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
    {
      title: 'Source',
      key: 'source',
      render: (_v, r) =>
        r.sourceEventId ? (
          <Tooltip
            title={`Auto-recorded by bonus event #${r.sourceEventId}, installment #${r.sourceInstallmentId}`}
          >
            <Tag color="purple">Bonus</Tag>
          </Tooltip>
        ) : (
          <Tag>HR</Tag>
        ),
    },
    {
      title: '',
      key: 'edit',
      render: (_v, row) =>
        row.sourceEventId == null && (
          <Can I="write" a="payroll">
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
        title="Off-cycle payments"
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
            <Can I="write" a="payroll">
              <Button
                type="primary"
                icon={<Plus size={14} strokeWidth={1.75} />}
                onClick={() => setOpenCreate(true)}
              >
                Record payment
              </Button>
            </Can>
          </Space>
        }
      />

      <Section
        title="All payments"
        actions={
          <Space wrap>
            <Segmented<CategoryFilter>
              value={category}
              onChange={setCategory}
              options={[{ label: 'All', value: 'ALL' }, ...categoryOptions]}
              disabled={categoryOptions.length === 0 && categoriesQuery.isLoading}
            />
            <DatePicker.RangePicker
              allowClear
              value={range}
              onChange={(v) => setRange(v as [Dayjs, Dayjs] | null)}
            />
            <EmployeePicker
              value={employeeId}
              onChange={setEmployeeId}
              placeholder="Search by name or code"
              style={{ width: 240 }}
            />
          </Space>
        }
      >
        {listQuery.isError ? (
          <Alert
            type="error"
            showIcon
            title={forbiddenOr(
              listQuery.error,
              "You don't have permission to view payments",
              "Couldn't load payments",
            )}
            description={getErrorMessage(listQuery.error)}
          />
        ) : rows.length === 0 && !listQuery.isLoading ? (
          <EmptyState
            icon={Receipt}
            title="No payments match the filter"
            description="Try widening the date range, clearing the employee filter, or recording a new HR-driven payment."
          />
        ) : (
          <Table<OffCyclePaymentRowWithEmployee>
            rowKey="id"
            loading={listQuery.isLoading}
            dataSource={rows}
            columns={columns}
            pagination={false}
            // Scroll wide columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: <Empty description="No payments" /> }}
          />
        )}
        {listQuery.data && listQuery.data.totalElements > pageRows.length && (
          <Text type="secondary" style={{ fontSize: 12, marginTop: 12 }}>
            Showing first {pageRows.length} of {listQuery.data.totalElements}. Tighter
            filters return faster results.
          </Text>
        )}
      </Section>

      <RecordPaymentModal
        open={openCreate}
        onClose={() => setOpenCreate(false)}
        categoryOptions={categoryOptions}
        categoriesLoading={categoriesQuery.isLoading}
      />
      <EditPaymentModal
        payment={editing}
        onClose={() => setEditing(null)}
        categoryOptions={categoryOptions}
        categoriesLoading={categoriesQuery.isLoading}
      />
    </div>
  );
}

/* -------------------------- Create modal ------------------------------ */

type CreateForm = {
  employeeId: number;
  paidOn: Dayjs;
  amountRupees: number;
  offCyclePaymentTypeId: number;
  reason: string;
  paymentMode: PaymentMode;
  referenceNo?: string;
};

function RecordPaymentModal({
  open,
  onClose,
  categoryOptions,
  categoriesLoading,
}: {
  open: boolean;
  onClose: () => void;
  categoryOptions: CategoryOption[];
  categoriesLoading: boolean;
}) {
  const [form] = Form.useForm<CreateForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  // The form instance outlives the modal, so a stale mode would survive
  // `initialValues` — set the default explicitly on every open.
  useEffect(() => {
    if (open) form.setFieldsValue({ paymentMode: 'CASH' });
  }, [open, form]);

  useEffect(() => {
    if (!open || categoryOptions.length === 0) return;
    if (!form.getFieldValue('offCyclePaymentTypeId')) {
      form.setFieldsValue({ offCyclePaymentTypeId: categoryOptions[0].value });
    }
  }, [open, categoryOptions, form]);

  const create = useMutation({
    mutationFn: (v: CreateForm) =>
      offCycleApi.create({
        employeeId: v.employeeId,
        paidOn: v.paidOn.format('YYYY-MM-DD'),
        amount: v.amountRupees,
        offCyclePaymentTypeId: v.offCyclePaymentTypeId,
        reason: v.reason.trim(),
        paymentMode: v.paymentMode,
        referenceNo: v.referenceNo?.trim() || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'off-cycle'] });
      message.success('Payment recorded.');
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
      title="Record off-cycle payment"
      open={open}
      onCancel={onClose}
      okText="Record"
      confirmLoading={create.isPending}
      onOk={() => form.validateFields().then((v) => create.mutateAsync(v))}
      destroyOnHidden
      width={520}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          paidOn: dayjs(),
          paymentMode: 'CASH',
        }}
      >
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item
            name="employeeId"
            label="Employee"
            rules={[{ required: true, message: 'Pick an employee.' }]}
          >
            <EmployeePicker placeholder="Search by name or code" />
          </Form.Item>
          <Form.Item name="paidOn" label="Paid on" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item
            name="amountRupees"
            label="Amount (Rs)"
            rules={[{ required: true }, { type: 'number', min: 0.01 }]}
          >
            <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
          </Form.Item>
          <Form.Item
            name="offCyclePaymentTypeId"
            label="Category"
            rules={[{ required: true }]}
          >
            <Select
              options={categoryOptions}
              loading={categoriesLoading}
              disabled={categoriesLoading || categoryOptions.length === 0}
            />
          </Form.Item>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item name="paymentMode" label="Mode" rules={[{ required: true }]}>
            <Select options={PAYMENT_MODE_OPTIONS} />
          </Form.Item>
          {/* <Form.Item name="referenceNo" label="Reference">
            <Input placeholder="Cheque / txn ref" />
          </Form.Item> */}
        </div>
        <Form.Item
          name="reason"
          label="Reason"
          rules={[{ required: true, message: 'Reason is required.' }]}
        >
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------------- Edit modal ------------------------------- */

type EditForm = {
  paidOn: Dayjs;
  amountRupees: number;
  offCyclePaymentTypeId: number;
  paymentMode: PaymentMode;
  referenceNo?: string;
  reason: string;
};

function EditPaymentModal({
  payment,
  onClose,
  categoryOptions,
  categoriesLoading,
}: {
  payment: OffCyclePaymentRowWithEmployee | null;
  onClose: () => void;
  categoryOptions: CategoryOption[];
  categoriesLoading: boolean;
}) {
  const [form] = Form.useForm<EditForm>();
  const queryClient = useQueryClient();

  const editCategoryOptions = useMemo(() => {
    const options = [...categoryOptions];
    // Keep a since-deactivated type selectable so the row still shows its name.
    const typeId = payment?.offCyclePaymentTypeId;
    if (typeId != null && !options.some((option) => option.value === typeId)) {
      options.push({
        value: typeId,
        label: payment?.offCyclePaymentTypeName ?? `#${typeId}`,
      });
    }
    return options.sort((a, b) => a.label.localeCompare(b.label));
  }, [categoryOptions, payment]);

  // Rows recorded before the type master carry only the name snapshot (id is
  // null), so resolve them against the active types by name.
  const selectedTypeId = useMemo(() => {
    if (!payment) return undefined;
    if (payment.offCyclePaymentTypeId != null) return payment.offCyclePaymentTypeId;
    const name = payment.offCyclePaymentTypeName?.trim().toLowerCase();
    if (!name) return undefined;
    return categoryOptions.find((option) => option.label.trim().toLowerCase() === name)
      ?.value;
  }, [categoryOptions, payment]);

  useEffect(() => {
    if (payment) {
      form.setFieldsValue({
        paidOn: dayjs(payment.paidOn),
        amountRupees: payment.amount,
        paymentMode: payment.paymentMode ?? 'CASH',
        referenceNo: payment.referenceNo ?? '',
        reason: payment.reason,
      });
    } else {
      form.resetFields();
    }
  }, [form, payment]);

  // Separate so the name match can fill in once the category list loads.
  useEffect(() => {
    if (payment) form.setFieldsValue({ offCyclePaymentTypeId: selectedTypeId });
  }, [form, payment, selectedTypeId]);
  const { message } = AntdApp.useApp();

  const update = useMutation({
    mutationFn: (v: EditForm) =>
      offCycleApi.update(payment?.id ?? 0, {
        paidOn: v.paidOn.format('YYYY-MM-DD'),
        amount: v.amountRupees,
        offCyclePaymentTypeId: v.offCyclePaymentTypeId,
        paymentMode: v.paymentMode,
        referenceNo: v.referenceNo?.trim() || undefined,
        reason: v.reason.trim(),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'off-cycle'] });
      message.success('Saved.');
      onClose();
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });

  const remove = useMutation({
    mutationFn: () => offCycleApi.remove(payment?.id ?? 0),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'off-cycle'] });
      message.success('Deleted.');
      onClose();
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  return (
    <Modal
      title={payment ? `Edit payment #${payment.id}` : 'Edit'}
      open={payment !== null}
      onCancel={onClose}
      okText="Save"
      confirmLoading={update.isPending}
      onOk={() => form.validateFields().then((v) => update.mutateAsync(v))}
      destroyOnHidden
      width={520}
      footer={(_, { OkBtn, CancelBtn }) => (
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Popconfirm
            title="Delete this payment? Recorded for audit but the row is hard-deleted."
            okText="Delete"
            okButtonProps={{ danger: true }}
            onConfirm={() => remove.mutateAsync()}
          >
            <Button danger loading={remove.isPending}>
              Delete
            </Button>
          </Popconfirm>
          <Space>
            <CancelBtn />
            <OkBtn />
          </Space>
        </Space>
      )}
    >
      {payment && (
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            paidOn: dayjs(payment.paidOn),
            amountRupees: payment.amount,
            offCyclePaymentTypeId: selectedTypeId,
            paymentMode: payment.paymentMode ?? 'CASH',
            referenceNo: payment.referenceNo ?? '',
            reason: payment.reason,
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Form.Item name="paidOn" label="Paid on" rules={[{ required: true }]}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="amountRupees"
              label="Amount (Rs)"
              rules={[{ required: true }, { type: 'number', min: 0.01 }]}
            >
              <InputNumber style={{ width: '100%' }} addonBefore="Rs" />
            </Form.Item>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Form.Item
              name="offCyclePaymentTypeId"
              label="Category"
              rules={[{ required: true }]}
            >
              <Select
                options={editCategoryOptions}
                loading={categoriesLoading}
                disabled={categoriesLoading || editCategoryOptions.length === 0}
              />
            </Form.Item>
            <Form.Item name="paymentMode" label="Mode" rules={[{ required: true }]}>
              <Select options={PAYMENT_MODE_OPTIONS} />
            </Form.Item>
          </div>
          {/* <Form.Item name="referenceNo" label="Reference">
            <Input />
          </Form.Item> */}
          <Form.Item name="reason" label="Reason" rules={[{ required: true }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}
