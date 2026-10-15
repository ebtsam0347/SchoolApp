// /people/salary-component-transactions — salary component transaction admin list.
//
// Tabs: All employees / Set allowances / History manage each employee's
// flat allowances (components with calculateWithSalary = false, via
// /allowances/** — see allowances-api.ts);
// Monthly calculation is the run list below.
//
// One row per (year, month) transaction. Status pill plus counts.
// Row click → detail page. New transaction is a simple month picker —
// POST /allowance-run with { allowanceYear, allowanceMonth }.

import { createFileRoute, Link } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useContext, useState } from 'react';
import {
  App as AntdApp,
  Button,
  DatePicker,
  Empty,
  Form,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Plus, RefreshCcw, Sliders } from 'lucide-react';

import { AbilityContext } from '@/auth';
import {
  EmptyState,
  PageHeader,
  Section,
  StatusPill,
} from '@/components';
import { getErrorMessage, isApiError, applyApiErrorToForm } from '@/lib';
import { fiscalYearsApi, formatPeriod } from '@/payroll';
import {
  AllEmployeesTab,
  AllowanceHistoryTab,
  SetAllowancesTab,
  TRANSACTION_STATUS_LABEL,
  TRANSACTION_STATUS_TONE,
  salaryComponentTransactionsApi,
  type AllowanceEmployee,
  type SalaryComponentTransactionDto,
} from '@/people/salary-component-transactions';

const { Text } = Typography;

type TabKey = 'all' | 'set' | 'history' | 'runs';

export const Route = createFileRoute(
  '/_app/people/salary-component-transactions/',
)({
  component: SalaryComponentTransactionsPage,
});

function SalaryComponentTransactionsPage() {
  const fyQuery = useQuery({
    queryKey: ['payroll', 'fiscal-years'],
    queryFn: ({ signal }) => fiscalYearsApi.list(signal),
    staleTime: 60_000,
  });

  const fyList = fyQuery.data ?? [];
  const currentFy = fyList.find((f) => f.status === 'CURRENT')?.code;
  const [selectedFy, setSelectedFy] = useState<string | undefined>(undefined);
  const fy = selectedFy ?? currentFy;

  const transactionsQuery = useQuery({
    queryKey: ['salary-component-transactions', 'list', fy],
    queryFn: ({ signal }) => salaryComponentTransactionsApi.list(fy!, signal),
    enabled: !!fy,
  });

  const [createOpen, setCreateOpen] = useState(false);

  // The allowance tabs read/write payroll lines, so they need payroll
  // permissions; without them the page is just the monthly runs.
  const ability = useContext(AbilityContext);
  const canReadPayroll = ability.can('read', 'payroll');
  const canWritePayroll = ability.can('write', 'payroll');
  const [tab, setTab] = useState<TabKey>(canReadPayroll ? 'all' : 'runs');
  // Set allowances and History each keep their own employee; the row
  // buttons on All employees fill whichever tab they open.
  const [editEmployee, setEditEmployee] = useState<AllowanceEmployee | undefined>();
  const [historyEmployee, setHistoryEmployee] = useState<AllowanceEmployee | undefined>();

  const rows = (transactionsQuery.data ?? []).slice().sort((a, b) => {
    if (a.periodYear !== b.periodYear)
      return b.periodYear - a.periodYear;
    return b.periodMonth - a.periodMonth;
  });

  const columns: TableColumnsType<SalaryComponentTransactionDto> = [
    {
      title: 'Period',
      key: 'period',
      render: (_v, item) => (
        <Link
          to="/people/salary-component-transactions/$id"
          params={{ id: String(item.id) }}
          style={{ fontWeight: 600 }}
        >
          {formatPeriod(item.periodYear, item.periodMonth)}
        </Link>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      render: (_v, item) => (
        <StatusPill tone={TRANSACTION_STATUS_TONE[item.status]}>
          {TRANSACTION_STATUS_LABEL[item.status]}
        </StatusPill>
      ),
    },
    {
      title: 'Total count',
      key: 'totalCount',
      align: 'right',
      render: (_v, item) => item.totalCount > 0 ? item.totalCount : '—',
    },
    {
      title: 'Progress',
      key: 'progressCount',
      align: 'right',
      render: (_v, item) => item.progressCount > 0 ? item.progressCount : '—',
    },
    {
      title: 'Skipped',
      key: 'skippedCount',
      align: 'right',
      render: (_v, item) => item.skippedCount > 0 ? item.skippedCount : '—',
    },
  ];

  const runsTab = (
    <>
      <Section
        title="Transactions"
        subtitle="Newest period first. Open a transaction to advance it through the workflow."
        actions={
          <Space wrap>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => transactionsQuery.refetch()}
                loading={transactionsQuery.isFetching}
                disabled={!fy}
                aria-label="Refresh"
              />
            </Tooltip>
            <Select
              value={fy}
              onChange={(v) => setSelectedFy(v)}
              style={{ minWidth: 160 }}
              options={fyList.map((f) => ({
                label: `${f.code}${f.status === 'CURRENT' ? ' (current)' : ''}`,
                value: f.code,
              }))}
              placeholder="Fiscal year"
              loading={fyQuery.isLoading}
            />
            <Button
              type="primary"
              icon={<Plus size={14} strokeWidth={1.75} />}
              onClick={() => setCreateOpen(true)}
            >
              New transaction
            </Button>
          </Space>
        }
      >
        {transactionsQuery.isError ? (
          <Text type="danger">
            {getErrorMessage(transactionsQuery.error)}
          </Text>
        ) : rows.length === 0 && !transactionsQuery.isLoading ? (
          <EmptyState
            icon={Sliders}
            title="No transactions yet"
            description="Use &ldquo;New transaction&rdquo; to create the first one."
          />
        ) : (
          <Table<SalaryComponentTransactionDto>
            rowKey={(item) => item.id}
            loading={transactionsQuery.isLoading}
            dataSource={rows}
            columns={columns}
            pagination={false}
            // Scroll wide columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: <Empty description="No transactions" /> }}
          />
        )}
      </Section>

      <CreateTransactionModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
      />
    </>
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow="People"
        title="Employee allowances"
        description="Each employee's flat allowances (paid apart from salary), and the monthly run that pays them."
      />

      <Tabs
        activeKey={tab}
        onChange={(k) => setTab(k as TabKey)}
        items={[
          ...(canReadPayroll
            ? [
                {
                  key: 'all',
                  label: 'All employees',
                  children: (
                    <AllEmployeesTab
                      canWrite={canWritePayroll}
                      onEdit={(row) => {
                        setEditEmployee(row.employee);
                        setTab('set');
                      }}
                      onHistory={(row) => {
                        setHistoryEmployee(row.employee);
                        setTab('history');
                      }}
                    />
                  ),
                },
                {
                  key: 'set',
                  label: 'Set allowances',
                  children: (
                    <SetAllowancesTab
                      canWrite={canWritePayroll}
                      employee={editEmployee}
                      onEmployeeChange={setEditEmployee}
                    />
                  ),
                },
                {
                  key: 'history',
                  label: 'History',
                  children: (
                    <AllowanceHistoryTab employee={historyEmployee} onEmployeeChange={setHistoryEmployee} />
                  ),
                },
              ]
            : []),
          { key: 'runs', label: 'Monthly calculation', children: runsTab },
        ]}
      />
    </div>
  );
}

/* -------------------- Create-transaction modal ----------------------- */

type CreateTransactionForm = { period: Dayjs };

function CreateTransactionModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const [form] = Form.useForm<CreateTransactionForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const create = useMutation({
    mutationFn: (body: { year: number; month: number }) =>
      salaryComponentTransactionsApi.create(body),
    onSuccess: (txn) => {
      void queryClient.invalidateQueries({
        queryKey: ['salary-component-transactions'],
      });
      message.success(
        `Created ${formatPeriod(txn.periodYear, txn.periodMonth)} draft.`,
      );
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
      title="New salary component transaction"
      open={open}
      onCancel={onClose}
      okText="Create"
      confirmLoading={create.isPending}
      onOk={() =>
        form.validateFields().then((v) => {
          const d = v.period;
          return create.mutateAsync({
            year: d.year(),
            month: d.month() + 1,
          });
        })
      }
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          period: dayjs().startOf('month'),
        }}
      >
        <Form.Item
          name="period"
          label="Month"
          rules={[{ required: true, message: 'Pick a month' }]}
        >
          <DatePicker
            picker="month"
            format="MMM YYYY"
            style={{ width: '100%' }}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
