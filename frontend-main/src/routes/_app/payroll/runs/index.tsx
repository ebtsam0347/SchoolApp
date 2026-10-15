// /payroll/runs — payroll run admin list.
//
// One row per (year, month) run, scoped to a fiscal year (defaults to the
// current FY). Status pill plus the run's net + employee count. Row click →
// detail page that drives the saga forward (lock-attendance → compute →
// approve → post). New run is a single year/month picker — the service
// derives the FY and rejects creation if the period overlaps an existing run.
//
// Backed by GET /payroll/runs/with-summary?fiscalYear=… (P3). The composite
// folds the per-run rollup (gross / net / employees / on-hold) into the list
// payload so the page can render a meaningful overview without N+1 calls.

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
  Modal,
  Select,
  Space,
  Table,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Plus, RefreshCcw, Wallet } from 'lucide-react';

import {
  EmptyState,
  MoneyCell,
  PageHeader,
  Section,
  StatusPill,
} from '@/components';
import { Can } from '@/auth';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import {
  PAYROLL_RUN_STATUS_LABEL,
  PAYROLL_RUN_STATUS_TONE,
  fiscalYearsApi,
  formatPeriod,
  payrollComposites,
  payrollRunsApi,
  type PayrollRunsListItem,
} from '@/payroll';

const { Text } = Typography;

export const Route = createFileRoute('/_app/payroll/runs/')({
  component: PayrollRunsPage,
});

function PayrollRunsPage() {
  const fyQuery = useQuery({
    queryKey: ['payroll', 'fiscal-years'],
    queryFn: ({ signal }) => fiscalYearsApi.list(signal),
    staleTime: 60_000,
  });

  const fyList = fyQuery.data ?? [];
  const currentFy = fyList.find((f) => f.status === 'CURRENT')?.code;
  const [selectedFy, setSelectedFy] = useState<string | undefined>(undefined);
  const fy = selectedFy ?? currentFy;

  const runsQuery = useQuery({
    queryKey: ['payroll', 'runs', 'bff', fy],
    queryFn: ({ signal }) => payrollComposites.runs(fy!, signal),
    enabled: !!fy,
  });

  const [createOpen, setCreateOpen] = useState(false);

  const rows = (runsQuery.data?.runs ?? []).slice().sort((a, b) => {
    if (a.run.periodYear !== b.run.periodYear)
      return b.run.periodYear - a.run.periodYear;
    return b.run.periodMonth - a.run.periodMonth;
  });

  const columns: TableColumnsType<PayrollRunsListItem> = [
    {
      title: 'Month',
      key: 'month',
      render: (_v, item) => (
        <Link
          to="/payroll/runs/$id"
          params={{ id: String(item.run.id) }}
          style={{ fontWeight: 600 }}
        >
          {new Date(item.run.periodYear, item.run.periodMonth - 1, 1).toLocaleDateString(
            'en-GB',
            { month: 'long' },
          )}
        </Link>
      ),
    },
    {
      title: 'Fiscal year',
      key: 'fiscalYear',
      render: (_v, item) => (
        <Text type="secondary">{item.run.fiscalYearCode}</Text>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      render: (_v, item) => (
        <StatusPill tone={PAYROLL_RUN_STATUS_TONE[item.run.status]}>
          {PAYROLL_RUN_STATUS_LABEL[item.run.status]}
        </StatusPill>
      ),
    },
    {
      title: 'Employees',
      key: 'employees',
      align: 'right',
      render: (_v, item) =>
        item.summary.employees > 0 ? item.summary.employees : '—',
    },
    {
      title: 'Net',
      key: 'net',
      align: 'right',
      render: (_v, item) =>
        item.summary.net > 0 ? (
          <MoneyCell amount={item.summary.net} compact />
        ) : (
          '—'
        ),
    },
    {
      title: 'On hold',
      key: 'held',
      align: 'right',
      render: (_v, item) =>
        item.summary.held > 0 || item.summary.releasePending > 0
          ? `${item.summary.held} / ${item.summary.releasePending}`
          : '—',
    },
    {
      title: 'Posted',
      key: 'postedAt',
      render: (_v, item) =>
        item.run.postedAt
          ? new Date(item.run.postedAt).toLocaleDateString('en-GB', {
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
        title="Payroll runs"
        description="One run per month. Open a run to lock attendance, compute, approve, and post."
        actions={
          <Space>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => runsQuery.refetch()}
                loading={runsQuery.isFetching}
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
            <Can I="manage" a="payroll">
              <Button
                type="primary"
                icon={<Plus size={14} strokeWidth={1.75} />}
                onClick={() => setCreateOpen(true)}
              >
                New run
              </Button>
            </Can>
          </Space>
        }
      />

      <Section
        title={fy ? `Runs in ${fy}` : 'Select a fiscal year'}
        subtitle={
          fy
            ? 'Newest period first. Open a run to advance it through the workflow.'
            : undefined
        }
      >
        {runsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            title="Couldn't load runs"
            description={getErrorMessage(runsQuery.error)}
          />
        ) : !fy ? (
          <Text type="secondary">Pick a fiscal year above.</Text>
        ) : rows.length === 0 && !runsQuery.isLoading ? (
          <EmptyState
            icon={Wallet}
            title="No runs yet for this fiscal year"
            description="Use “New run” to draft the first month."
          />
        ) : (
          <Table<PayrollRunsListItem>
            rowKey={(item) => item.run.id}
            loading={runsQuery.isLoading}
            dataSource={rows}
            columns={columns}
            pagination={false}
            // Scroll wide columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: <Empty description="No runs" /> }}
          />
        )}
      </Section>

      <CreateRunModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        defaultFy={fy}
      />
    </div>
  );
}

/* ------------------------ Create-run modal ---------------------------- */

type CreateRunForm = { period: Dayjs };

function CreateRunModal({
  open,
  onClose,
  defaultFy,
}: {
  open: boolean;
  onClose: () => void;
  defaultFy?: string;
}) {
  const [form] = Form.useForm<CreateRunForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const create = useMutation({
    mutationFn: (body: { year: number; month: number }) =>
      payrollRunsApi.create(body),
    onSuccess: (run) => {
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'runs'] });
      message.success(
        `Created ${formatPeriod(run.periodYear, run.periodMonth)} draft.`,
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
      title="New payroll run"
      open={open}
      onCancel={onClose}
      okText="Create draft"
      confirmLoading={create.isPending}
      onOk={() =>
        form.validateFields().then((v) => {
          const d = v.period;
          return create.mutateAsync({ year: d.year(), month: d.month() + 1 });
        })
      }
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          period: dayjs().subtract(1, 'month').startOf('month'),
        }}
      >
        <Form.Item
          name="period"
          label="Period"
          rules={[{ required: true, message: 'Pick a month' }]}
        >
          <DatePicker
            picker="month"
            format="MMM YYYY"
            style={{ width: '100%' }}
          />
        </Form.Item>
        <Text type="secondary" style={{ fontSize: 12 }}>
          The fiscal year is derived from the period. {defaultFy ? `Current: ${defaultFy}.` : ''}
        </Text>
      </Form>
    </Modal>
  );
}
