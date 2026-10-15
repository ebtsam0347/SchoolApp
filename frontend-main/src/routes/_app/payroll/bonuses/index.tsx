// /payroll/bonuses — bonus run admin list.
//
// One row per (fiscal year, bonus no) "bonus no settings" row, scoped to a
// fiscal year (defaults to the current FY). Each row fixes which bonus it is
// (1 or 2) and the percentage of salary it pays. Row click → detail page
// that drives the run forward (compute → approve), same shape as payroll
// runs.
//
// Backed by GET /bonuses/no-settings?fiscalYear=…. Payroll-admin only —
// the create / compute / approve endpoints all require `payroll.admin`.

import { createFileRoute, Link, useNavigate } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Empty,
  Form,
  Modal,
  Segmented,
  Select,
  Space,
  Table,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import { ChevronRight, Gift, Plus, RefreshCcw } from 'lucide-react';

import {
  EmptyState,
  PageHeader,
  Section,
  StatusPill,
} from '@/components';
import { Can } from '@/auth';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import {
  BONUS_NO_OPTIONS,
  BONUS_RUN_STATUS_LABEL,
  BONUS_RUN_STATUS_TONE,
  bonusRunsApi,
  fiscalYearsApi,
  formatBonusRun,
  type BonusNo,
  type BonusNoSettingsDto,
  type BonusPercentage,
} from '@/payroll';
import { BonusPercentagePicker, requirePayrollAdmin } from '@/payroll/bonuses';

const { Text } = Typography;

export const Route = createFileRoute('/_app/payroll/bonuses/')({
  beforeLoad: requirePayrollAdmin,
  component: BonusRunsPage,
});

function BonusRunsPage() {
  const navigate = useNavigate();

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
    queryKey: ['payroll', 'bonus-runs', fy],
    queryFn: ({ signal }) => bonusRunsApi.list({ fiscalYear: fy }, signal),
    enabled: !!fy,
  });

  const [createOpen, setCreateOpen] = useState(false);

  const rows = (runsQuery.data ?? [])
    .slice()
    .sort((a, b) => a.bonusNo - b.bonusNo);

  function openRun(id: number) {
    void navigate({ to: '/payroll/bonuses/$id', params: { id: String(id) } });
  }

  const columns: TableColumnsType<BonusNoSettingsDto> = [
    {
      title: 'Bonus',
      key: 'bonus',
      render: (_v, r) => (
        <Link
          to="/payroll/bonuses/$id"
          params={{ id: String(r.id) }}
          style={{ fontWeight: 600 }}
          // The row itself navigates too; don't fire both.
          onClick={(e) => e.stopPropagation()}
        >
          {formatBonusRun(r.bonusNo)}
        </Link>
      ),
    },
    {
      title: 'Fiscal year',
      dataIndex: 'fiscalYearCode',
      key: 'fiscalYearCode',
    },
    {
      title: 'Percentage of salary',
      dataIndex: 'percentageOfSalary',
      key: 'percentageOfSalary',
      align: 'right',
      render: (v: number) => (
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>{v}%</span>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: BonusNoSettingsDto['status']) => (
        <StatusPill tone={BONUS_RUN_STATUS_TONE[s]}>
          {BONUS_RUN_STATUS_LABEL[s]}
        </StatusPill>
      ),
    },
    {
      title: '',
      key: 'open',
      width: 48,
      align: 'right',
      render: () => (
        <ChevronRight
          size={16}
          strokeWidth={1.75}
          color="var(--ant-color-text-tertiary)"
          aria-hidden
        />
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow="Money"
        title="Bonus runs"
        description="Up to two bonuses per fiscal year, each a percentage of salary. Open a run to compute every employee's bonus and approve it."
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
                New bonus run
              </Button>
            </Can>
          </Space>
        }
      />

      <Section
        title={fy ? `Bonus runs in ${fy}` : 'Select a fiscal year'}
        subtitle={
          fy
            ? 'Open a bonus run to compute, review and approve it.'
            : undefined
        }
      >
        {runsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            title="Couldn't load bonus runs"
            description={getErrorMessage(runsQuery.error)}
          />
        ) : !fy ? (
          <Text type="secondary">Pick a fiscal year above.</Text>
        ) : rows.length === 0 && !runsQuery.isLoading ? (
          <EmptyState
            icon={Gift}
            title="No bonus runs for this fiscal year yet"
            description="Use “New bonus run” to set up Bonus 1 at a percentage of salary."
          />
        ) : (
          <Table<BonusNoSettingsDto>
            rowKey="id"
            loading={runsQuery.isLoading}
            dataSource={rows}
            columns={columns}
            pagination={false}
            // Scroll wide columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
            onRow={(r) => ({
              onClick: () => openRun(r.id),
              style: { cursor: 'pointer' },
            })}
            locale={{ emptyText: <Empty description="No bonus runs" /> }}
          />
        )}
      </Section>

      <CreateBonusRunModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        defaultFy={fy}
      />
    </div>
  );
}

/* ---------------------- Create-bonus-run modal ------------------------ */

type CreateBonusRunForm = {
  fiscalYearCode: string;
  bonusNo: BonusNo;
  percentageOfSalary: BonusPercentage;
};

function CreateBonusRunModal({
  open,
  onClose,
  defaultFy,
}: {
  open: boolean;
  onClose: () => void;
  defaultFy?: string;
}) {
  const [form] = Form.useForm<CreateBonusRunForm>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const fyQuery = useQuery({
    queryKey: ['payroll', 'fiscal-years'],
    queryFn: ({ signal }) => fiscalYearsApi.list(signal),
    staleTime: 60_000,
  });

  // Which bonus slots already exist for the FY picked in the form. Shares
  // the list page's query key, so the default FY is usually cached already.
  const formFy = Form.useWatch('fiscalYearCode', form) ?? defaultFy;
  const existingQuery = useQuery({
    queryKey: ['payroll', 'bonus-runs', formFy],
    queryFn: ({ signal }) => bonusRunsApi.list({ fiscalYear: formFy }, signal),
    enabled: open && !!formFy,
  });
  const taken = new Set(
    (existingQuery.data ?? [])
      .filter((r) => r.status !== 'CANCELLED')
      .map((r) => r.bonusNo),
  );
  const free = BONUS_NO_OPTIONS.filter((n) => !taken.has(n));

  const create = useMutation({
    mutationFn: (body: CreateBonusRunForm) => bonusRunsApi.create(body),
    onSuccess: (run) => {
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'bonus-runs'] });
      message.success(
        `Created ${formatBonusRun(run.bonusNo)} for ${run.fiscalYearCode} at ${run.percentageOfSalary}% of salary.`,
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
      title="New bonus run"
      open={open}
      onCancel={onClose}
      okText="Create"
      okButtonProps={{ disabled: !!formFy && !existingQuery.isLoading && free.length === 0 }}
      confirmLoading={create.isPending}
      onOk={() => form.validateFields().then((v) => create.mutateAsync(v))}
      destroyOnHidden
      width={480}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          fiscalYearCode: defaultFy,
          bonusNo: free[0] ?? 1,
          percentageOfSalary: 100,
        }}
      >
        <Form.Item
          name="fiscalYearCode"
          label="Fiscal year"
          rules={[{ required: true, message: 'Pick a fiscal year' }]}
        >
          <Select
            options={(fyQuery.data ?? []).map((f) => ({
              value: f.code,
              label: `${f.code}${f.status === 'CURRENT' ? ' (current)' : ''}`,
            }))}
            loading={fyQuery.isLoading}
          />
        </Form.Item>

        {formFy && !existingQuery.isLoading && free.length === 0 ? (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            title={`Both bonuses already exist for ${formFy}.`}
            description="Open an existing bonus run from the list to change its percentage."
          />
        ) : null}

        <Form.Item
          name="bonusNo"
          label="Bonus"
          tooltip="Two bonuses per fiscal year."
          rules={[
            { required: true, message: 'Pick a bonus' },
            {
              validator: (_r, v: BonusNo | undefined) =>
                v != null && taken.has(v)
                  ? Promise.reject(
                      new Error(`${formatBonusRun(v)} already exists for ${formFy}.`),
                    )
                  : Promise.resolve(),
            },
          ]}
        >
          <Segmented<BonusNo>
            block
            options={BONUS_NO_OPTIONS.map((n) => ({
              label: formatBonusRun(n),
              value: n,
              disabled: taken.has(n),
            }))}
          />
        </Form.Item>

        <Form.Item
          name="percentageOfSalary"
          label="Percentage of salary"
          extra="Share of each employee's salary paid out as this bonus."
          rules={[{ required: true, message: 'Pick a percentage' }]}
        >
          <BonusPercentagePicker />
        </Form.Item>
      </Form>
    </Modal>
  );
}
