// Basic-salary panel: KPI strip showing the current basic + revisions
// count, history table underneath, and a Revise modal that closes the
// current row and inserts a new one (effective-dated).

import { Can } from '@/auth';
import { KpiGrid, MoneyCell, Section, StatCard } from '@/components';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import {
  employeeSalaryApi,
  formatDateShort,
  type EmployeeSalaryDto,
} from '@/payroll';
import { useMutation, useQueryClient } from '@tanstack/react-query';
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
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Edit3, Wallet } from 'lucide-react';
import { useState } from 'react';
import { salaryTabKey, useSalaryTab } from './query-keys';

const { Text } = Typography;

export function BasicSection({ employeeId }: { employeeId: number }) {
  const tabQuery = useSalaryTab(employeeId);
  const [openRevise, setOpenRevise] = useState(false);

  const current = tabQuery.data?.currentBasic ?? null;
  const history = tabQuery.data?.basicHistory ?? [];

  const lines = tabQuery.data?.components ?? [];
  const earnings = lines.filter((l) => l.type === 'EARNING');
  const deductions = lines.filter((l) => l.type === 'DEDUCTION');
  const totalEarnings = earnings.reduce((s, l) => s + l.amount, 0);
  const totalDeductions = deductions.reduce((s, l) => s + l.amount, 0);
  // Same figure as the "Total (12mo)" card in OffCycleSection.
  const totalOffCycle = (tabQuery.data?.offCycle12Months ?? []).reduce(
    (s, r) => s + r.amount,
    0,
  );


  const columns: TableColumnsType<EmployeeSalaryDto> = [
    {
      title: 'Effective from',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      render: (v: string) => formatDateShort(v),
    },
    {
      title: 'Effective to',
      dataIndex: 'effectiveTo',
      key: 'effectiveTo',
      render: (v: string | null) =>
        v ? formatDateShort(v) : <Tag color="success">Current</Tag>,
    },
    {
      title: 'Basic',
      dataIndex: 'basic',
      key: 'basic',
      align: 'right',
      render: (v: number) => (
        <MoneyCell amount={v} style={{ fontWeight: 500 }} />
      ),
    },
    {
      title: 'Reason',
      dataIndex: 'revisionReason',
      key: 'revisionReason',
      ellipsis: true,
    },
  ];

  return (
    <Section
      title="Basic salary"
      subtitle="Saving creates a new period from the date you pick; the previous amount stays on the timeline."
      actions={
        <Can I="write" a="payroll">
          <Button
            icon={<Edit3 size={14} strokeWidth={1.75} />}
            onClick={() => setOpenRevise(true)}
          >
            Revise basic
          </Button>
        </Can>
      }
    >
      {tabQuery.isLoading ? null : !current ? (
        <Alert
          type="warning"
          showIcon
          title="No basic salary on file"
          description="A skeleton row is created when the employee is onboarded; HR fills it in."
        />
      ) : (
        <KpiGrid>
          <StatCard
            label="Current basic"
            value={
              <MoneyCell
                amount={current.basic}
                style={{ fontWeight: 600 }}
              />
            }
            icon={Wallet}
            tone="primary"
            variant="accent-radial"
            delta={`Effective from ${formatDateShort(current.effectiveFrom)}`}
          />
           <StatCard
              label="Earnings (monthly)"
              value={<MoneyCell amount={totalEarnings} compact />}
              tone="success"
            />
            <StatCard
              label="Deductions (monthly)"
              value={<MoneyCell amount={totalDeductions} compact />}
              tone="warning"
            />
            <StatCard
              label="Total off-cycle payments"
              value={<MoneyCell amount={totalOffCycle} compact />}
              tone="info"
              delta="Last 12 months"
            />


        </KpiGrid>
      )}

      <div style={{ marginTop: 'var(--app-space-md)' }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          History
        </Text>
        <Table<EmployeeSalaryDto>
          rowKey="id"
          loading={tabQuery.isLoading}
          dataSource={history}
          columns={columns}
          pagination={false}
          size="small"
          // Scroll wide columns inside the table, not the page.
          scroll={{ x: 'max-content' }}
          style={{ marginTop: 8 }}
          locale={{ emptyText: <Empty description="No history" /> }}
        />
      </div>

      <ReviseBasicModal
        employeeId={employeeId}
        open={openRevise}
        onClose={() => setOpenRevise(false)}
      />
    </Section>
  );
}

type ReviseForm = {
  basicRupees: number;
  effectiveFrom: Dayjs;
  revisionReason?: string;
};

function ReviseBasicModal({
  employeeId,
  open,
  onClose,
}: {
  employeeId: number;
  open: boolean;
  onClose: () => void;
}) {
  const [form] = Form.useForm<ReviseForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const revise = useMutation({
    mutationFn: (v: ReviseForm) =>
      employeeSalaryApi.revise(employeeId, {
        basic: v.basicRupees,
        effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD'),
        revisionReason: v.revisionReason?.trim() || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: salaryTabKey(employeeId) });
      message.success('Basic revised.');
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
      title="Revise basic salary"
      open={open}
      onCancel={onClose}
      okText="Revise"
      confirmLoading={revise.isPending}
      onOk={() => form.validateFields().then((v) => revise.mutateAsync(v))}
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ effectiveFrom: dayjs().add(1, 'month').startOf('month') }}
      >
        <Form.Item
          name="basicRupees"
          label="New basic (Rs / month)"
          rules={[{ required: true }]}
        >
          <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
        </Form.Item>
        <Form.Item
          name="effectiveFrom"
          label="Effective from"
          rules={[{ required: true }]}
        >
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="revisionReason" label="Reason">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Text type="secondary" style={{ fontSize: 12 }}>
          When basic changes, remember to revise the INCOME_TAX line below to
          match. The system doesn't auto-adjust tax.
        </Text>
      </Form>
    </Modal>
  );
}
