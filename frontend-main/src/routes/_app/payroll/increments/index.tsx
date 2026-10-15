// /payroll/increments — bulk basic-salary revisions.
//
// One DRAFT batch per cycle (annual or one-off). Each line is one
// employee's old → new basic, with optional INCOME_TAX update. Apply runs
// in a single transaction; refuses if any line is in ERROR.
//
// File upload is not in the OpenAPI spec mirror — JSON-form creation is the
// only path for now (1 line = ad-hoc raise; many = annual cycle). See
// BFF_PAYROLL_WISHLIST.md for the upload gap.

import { createFileRoute, Link } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
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
  Segmented,
  Space,
  Table,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Plus, RefreshCcw, TrendingUp } from 'lucide-react';

import {
  EmptyState,
  PageHeader,
  Section,
  StatusPill,
} from '@/components';
import { Can } from '@/auth';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import {
  INCREMENT_BATCH_STATUS_LABEL,
  INCREMENT_BATCH_STATUS_TONE,
  formatDateShort,
  incrementBatchesApi,
  type IncrementBatchDto,
  type IncrementBatchStatus,
} from '@/payroll';
import { EmployeePicker } from '@/people';

const { Text } = Typography;

export const Route = createFileRoute('/_app/payroll/increments/')({
  component: IncrementsPage,
});

type Filter = 'ALL' | IncrementBatchStatus;

function IncrementsPage() {
  const [filter, setFilter] = useState<Filter>('ALL');
  const [openCreate, setOpenCreate] = useState(false);

  const listQuery = useQuery({
    queryKey: ['payroll', 'increments', filter],
    queryFn: ({ signal }) =>
      incrementBatchesApi.list(
        { status: filter === 'ALL' ? undefined : filter },
        signal,
      ),
  });

  const rows = listQuery.data ?? [];

  const columns: TableColumnsType<IncrementBatchDto> = [
    {
      title: 'Name',
      key: 'name',
      render: (_v, r) => (
        <Link to="/payroll/increments/$id" params={{ id: String(r.id) }}>
          <span style={{ fontWeight: 600 }}>{r.name}</span>
        </Link>
      ),
    },
    {
      title: 'Effective',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      render: (v: string) => formatDateShort(v),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: IncrementBatchStatus) => (
        <StatusPill tone={INCREMENT_BATCH_STATUS_TONE[s]}>
          {INCREMENT_BATCH_STATUS_LABEL[s]}
        </StatusPill>
      ),
    },
    {
      title: 'Source file',
      dataIndex: 'sourceFilename',
      key: 'sourceFilename',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: 'Applied',
      dataIndex: 'appliedAt',
      key: 'appliedAt',
      render: (v: string | null) =>
        v
          ? new Date(v).toLocaleDateString('en-GB', {
              day: 'numeric',
              month: 'short',
              year: 'numeric',
            })
          : '—',
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow="Money"
        title="Salary increments"
        description="Bulk revisions to employee basic salary. A single-line batch covers ad-hoc raises; many lines cover the annual cycle."
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
                New batch
              </Button>
            </Can>
          </Space>
        }
      />

      <Section
        title="All batches"
        actions={
          <Segmented<Filter>
            value={filter}
            onChange={setFilter}
            options={[
              { label: 'All', value: 'ALL' },
              { label: 'Draft', value: 'DRAFT' },
              { label: 'Applied', value: 'APPLIED' },
              { label: 'Cancelled', value: 'CANCELLED' },
            ]}
          />
        }
      >
        {listQuery.isError ? (
          <Alert
            type="error"
            showIcon
            title="Couldn't load batches"
            description={getErrorMessage(listQuery.error)}
          />
        ) : rows.length === 0 && !listQuery.isLoading ? (
          <EmptyState
            icon={TrendingUp}
            title="No batches"
            description="Create a draft batch to plan a raise. Apply runs in a single transaction once previewed."
          />
        ) : (
          <Table<IncrementBatchDto>
            rowKey="id"
            loading={listQuery.isLoading}
            dataSource={rows}
            columns={columns}
            pagination={false}
            // Scroll wide columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: <Empty description="No batches" /> }}
          />
        )}
      </Section>

      <CreateBatchModal open={openCreate} onClose={() => setOpenCreate(false)} />
    </div>
  );
}

/* ----------------------- Create batch (1-line) ------------------------ */
//
// JSON form creates a 1-line batch — fits the ad-hoc raise / promotion
// path. Multi-line batches need either a richer UI (out of v1) or the
// upload endpoint that's still pending.

type CreateForm = {
  name: string;
  effectiveFrom: Dayjs;
  notes?: string;
  employeeId: number;
  newBasicRupees: number;
  newIncomeTaxRupees?: number;
  lineNotes?: string;
};

function CreateBatchModal({
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
      incrementBatchesApi.create({
        name: v.name.trim(),
        effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
        notes: v.notes?.trim() || undefined,
        lines: [
          {
            employeeId: v.employeeId,
            newBasic: v.newBasicRupees,
            newIncomeTax: v.newIncomeTaxRupees,
            notes: v.lineNotes?.trim() || undefined,
          },
        ],
      }),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'increments'] });
      message.success(`Created draft batch "${created.name}".`);
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
      title="New increment batch"
      open={open}
      onCancel={onClose}
      okText="Create draft"
      confirmLoading={create.isPending}
      onOk={() => form.validateFields().then((v) => create.mutateAsync(v))}
      destroyOnHidden
      width={520}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ effectiveFrom: dayjs().add(1, 'month').startOf('month') }}
      >
        <Form.Item
          name="name"
          label="Name"
          tooltip='Pick something specific — e.g. "Promotion: Ali Raza" or "FY26 annual".'
          rules={[{ required: true, max: 128 }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="effectiveFrom"
          label="Effective from"
          rules={[{ required: true }]}
        >
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="notes" label="Notes">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Text type="secondary" style={{ fontSize: 12 }}>
          One-employee batches cover ad-hoc raises. Bulk uploads are coming soon.
        </Text>
        <div
          style={{
            marginTop: 16,
            padding: 12,
            border: '1px dashed var(--ant-color-border)',
            borderRadius: 8,
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
            <Form.Item
              name="newBasicRupees"
              label="New basic (Rs)"
              rules={[{ required: true }]}
            >
              <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
            </Form.Item>
          </div>
          <Form.Item
            name="newIncomeTaxRupees"
            label="New income tax (Rs)"
            tooltip="Only allowed if the employee already has an INCOME_TAX line."
          >
            <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
          </Form.Item>
          <Form.Item name="lineNotes" label="Line notes">
            <Input />
          </Form.Item>
        </div>
      </Form>
    </Modal>
  );
}
