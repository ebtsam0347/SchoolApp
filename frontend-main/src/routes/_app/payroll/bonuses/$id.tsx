// /payroll/bonuses/$id — bonus run detail.
//
// Same three layers as the payroll run detail:
//   1. RunStateMachine (bonus config) — DRAFT → COMPUTED → APPROVED, with
//      the transient COMPUTING animated on the edge and Reset / Failed
//      side-routes. Actions: compute, edit percentage, delete (DRAFT);
//      approve, reset (COMPUTED); retry (FAILED).
//   2. KPIs — bonus no, percentage of salary, FY, employees computed.
//   3. Per-employee bonus transactions, server-paginated, filterable by
//      status, payment mode and employee code (search-as-you-type,
//      debounced). All filters are applied server-side.
//
// Polling model: while the run is COMPUTING, only
// GET /bonuses/no-settings/{id} is polled. Transactions load once status
// leaves DRAFT / COMPUTING, and refetch on mutation invalidation.

import { createFileRoute, Link, useNavigate, useParams } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useContext, useState } from 'react';
import {
  App as AntdApp,
  Form,
  Input,
  Modal,
  Progress,
  Segmented,
  Select,
  Space,
  Typography,
} from 'antd';
import { CalendarRange, ChevronLeft, Gift, Percent, Search, Users } from 'lucide-react';

import {
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { AbilityContext } from '@/auth';
import { applyApiErrorToForm, getErrorMessage, isApiError, useDebounced } from '@/lib';
import {
  BONUS_PAYMENT_MODE_LABEL,
  BONUS_RUN_STATUS_LABEL,
  BONUS_RUN_STATUS_TONE,
  bonusRunsApi,
  formatBonusRun,
  type BonusNoSettingsDto,
  type BonusPaymentMode,
  type BonusPercentage,
  type PayslipStatus,
} from '@/payroll';
import { RunStateMachine } from '@/payroll/runs';
import {
  BONUS_RUN_MACHINE,
  BonusPercentagePicker,
  BonusRunActions,
  BonusTransactionsTable,
  requirePayrollAdmin,
} from '@/payroll/bonuses';

const { Text } = Typography;

const PAYMENT_MODE_OPTIONS = (
  Object.keys(BONUS_PAYMENT_MODE_LABEL) as BonusPaymentMode[]
).map((m) => ({ value: m, label: BONUS_PAYMENT_MODE_LABEL[m] }));

export const Route = createFileRoute('/_app/payroll/bonuses/$id')({
  beforeLoad: requirePayrollAdmin,
  component: BonusRunDetailPage,
});

function BonusRunDetailPage() {
  const { id: idParam } = useParams({ from: '/_app/payroll/bonuses/$id' });
  const id = Number(idParam);
  const ability = useContext(AbilityContext);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();

  const [txFilter, setTxFilter] = useState<PayslipStatus | 'ALL'>('ALL');
  const [paymentMode, setPaymentMode] = useState<BonusPaymentMode | undefined>(
    undefined,
  );
  // Search-as-you-type: the transactions query keys off the debounced code,
  // so a request goes out 300 ms after typing stops.
  const [codeInput, setCodeInput] = useState('');
  const employeeCode = useDebounced(codeInput.trim(), 300);
  const [txPage, setTxPage] = useState(0);
  const [txPageSize, setTxPageSize] = useState(20);
  const [editOpen, setEditOpen] = useState(false);

  // Lightweight poll while computing; drives the header, the state
  // machine, and gates the transactions call below.
  const runQuery = useQuery({
    queryKey: ['payroll', 'bonus-run', id],
    queryFn: ({ signal }) => bonusRunsApi.get(id, signal),
    refetchInterval: (q) =>
      q.state.data?.status === 'COMPUTING' ? 4_000 : false,
  });

  const run = runQuery.data;
  const status = run?.status;
  const isComputing = status === 'COMPUTING';
  // Before compute there are no transactions to fetch.
  const hasTransactions =
    !!run && status !== 'DRAFT' && !isComputing && status !== 'CANCELLED';

  const txQuery = useQuery({
    queryKey: [
      'payroll',
      'bonus-run',
      id,
      'transactions',
      txFilter,
      paymentMode,
      employeeCode,
      txPage,
      txPageSize,
    ],
    queryFn: ({ signal }) =>
      bonusRunsApi.transactions(
        id,
        {
          status: txFilter === 'ALL' ? undefined : txFilter,
          paymentMode,
          employeeCode: employeeCode || undefined,
          page: txPage,
          size: txPageSize,
        },
        signal,
      ),
    enabled: hasTransactions,
  });

  // Unfiltered head count for the KPI — size=1, only totalElements is read.
  const countQuery = useQuery({
    queryKey: ['payroll', 'bonus-run', id, 'transactions', 'count'],
    queryFn: ({ signal }) => bonusRunsApi.transactions(id, { size: 1 }, signal),
    enabled: hasTransactions,
  });

  const txPageData = txQuery.data;
  const transactions = txPageData?.content ?? [];

  function refreshAll() {
    void queryClient.invalidateQueries({ queryKey: ['payroll', 'bonus-run', id] });
    void queryClient.invalidateQueries({ queryKey: ['payroll', 'bonus-runs'] });
  }
  function onMutErr(e: unknown) {
    message.error(getErrorMessage(e));
  }

  const compute = useMutation({
    mutationFn: () => bonusRunsApi.compute(id),
    onSuccess: () => {
      message.success('Compute started.');
      refreshAll();
    },
    onError: onMutErr,
  });
  const approve = useMutation({
    mutationFn: () => bonusRunsApi.approve(id),
    onSuccess: () => {
      message.success('Bonus run approved.');
      refreshAll();
    },
    onError: onMutErr,
  });
  const reset = useMutation({
    mutationFn: () => bonusRunsApi.reset(id),
    onSuccess: () => {
      message.success('Bonus run reset. Adjust the settings, then compute again.');
      refreshAll();
    },
    onError: onMutErr,
  });
  const remove = useMutation({
    mutationFn: () => bonusRunsApi.remove(id),
    onSuccess: () => {
      message.success('Bonus run deleted.');
      queryClient.removeQueries({ queryKey: ['payroll', 'bonus-run', id] });
      void queryClient.invalidateQueries({ queryKey: ['payroll', 'bonus-runs'] });
      void navigate({ to: '/payroll/bonuses' });
    },
    onError: onMutErr,
  });

  // Every bonus-no settings write requires `payroll.admin` → CASL `manage`.
  const canWrite = ability.can('manage', 'payroll');

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
      }}
    >
      <PageHeader
        eyebrow={
          <Link to="/payroll/bonuses">
            <ChevronLeft size={12} style={{ verticalAlign: -1, marginRight: 2 }} />
            Bonus runs
          </Link>
        }
        title={run ? `${formatBonusRun(run.bonusNo)} run` : 'Loading bonus run…'}
        description={
          run ? (
            <Space size={10} wrap>
              <StatusPill tone={BONUS_RUN_STATUS_TONE[run.status]}>
                {BONUS_RUN_STATUS_LABEL[run.status]}
              </StatusPill>
              <Text type="secondary">FY {run.fiscalYearCode}</Text>
              <Text type="secondary">{run.percentageOfSalary}% of salary</Text>
            </Space>
          ) : undefined
        }
      />

      <RequestState
        loading={runQuery.isLoading}
        error={
          runQuery.isError
            ? runQuery.error
            : txQuery.isError
              ? txQuery.error
              : undefined
        }
        errorTitle="Couldn't load bonus run"
      >
        {run && (
          <RunStateMachine
            status={run.status}
            config={BONUS_RUN_MACHINE}
            actions={
              <BonusRunActions
                status={run.status}
                canWrite={canWrite}
                compute={compute}
                approve={approve}
                reset={reset}
                remove={remove}
                onEditPercentage={() => setEditOpen(true)}
              />
            }
          />
        )}

        {run && isComputing && (
          <Section
            title="Compute progress"
            subtitle="Calculating the bonus for every employee. This page refreshes automatically."
          >
            <Progress percent={100} status="active" showInfo={false} />
          </Section>
        )}

        {run && (
          <KpiGrid>
            <StatCard
              label="Bonus"
              value={formatBonusRun(run.bonusNo)}
              tone="info"
              icon={Gift}
            />
            <StatCard
              label="Percentage of salary"
              value={`${run.percentageOfSalary}%`}
              tone="primary"
              icon={Percent}
            />
            <StatCard
              label="Fiscal year"
              value={run.fiscalYearCode}
              tone="neutral"
              icon={CalendarRange}
            />
            <StatCard
              label="Employees"
              value={
                hasTransactions && countQuery.data
                  ? String(countQuery.data.totalElements)
                  : '—'
              }
              tone="success"
              icon={Users}
            />
          </KpiGrid>
        )}

        <Section
          title="Bonus transactions"
          subtitle={
            txPageData
              ? `${txPageData.totalElements} employee${txPageData.totalElements === 1 ? '' : 's'}`
              : undefined
          }
          actions={
            hasTransactions ? (
              <Space wrap>
                <Input
                  value={codeInput}
                  onChange={(e) => {
                    setCodeInput(e.target.value);
                    setTxPage(0);
                  }}
                  placeholder="Employee code"
                  allowClear
                  prefix={<Search size={14} strokeWidth={1.75} />}
                  style={{ width: 200 }}
                />
                <Select<BonusPaymentMode>
                  value={paymentMode}
                  onChange={(v) => {
                    setPaymentMode(v);
                    setTxPage(0);
                  }}
                  placeholder="Payment mode"
                  allowClear
                  options={PAYMENT_MODE_OPTIONS}
                  style={{ width: 160 }}
                />
                <Segmented<PayslipStatus | 'ALL'>
                  value={txFilter}
                  onChange={(v) => {
                    setTxFilter(v);
                    setTxPage(0);
                  }}
                  options={[
                    { label: 'All', value: 'ALL' },
                    { label: 'Draft', value: 'DRAFT' },
                    { label: 'Paid', value: 'PAID' },
                    { label: 'Held', value: 'HELD' },
                    { label: 'Release pending', value: 'RELEASE_PENDING' },
                    { label: 'Released', value: 'RELEASED' },
                  ]}
                />
              </Space>
            ) : undefined
          }
        >
          {!run || !hasTransactions ? (
            <Text type="secondary">
              {status === 'CANCELLED'
                ? 'This bonus run was cancelled.'
                : 'Bonus transactions appear once the run is computed.'}
            </Text>
          ) : (
            <BonusTransactionsTable
              rows={transactions}
              loading={txQuery.isLoading}
              page={txPage}
              pageSize={txPageSize}
              totalElements={txPageData?.totalElements ?? 0}
              onPageChange={(p, s) => {
                setTxPage(p);
                setTxPageSize(s);
              }}
            />
          )}
        </Section>
      </RequestState>

      {run ? (
        <EditPercentageModal
          open={editOpen}
          run={run}
          onClose={() => setEditOpen(false)}
          onSaved={refreshAll}
        />
      ) : null}
    </div>
  );
}

/* ---------------------- Edit-percentage modal ------------------------- */

type EditPercentageForm = { percentageOfSalary: BonusPercentage };

function EditPercentageModal({
  open,
  run,
  onClose,
  onSaved,
}: {
  open: boolean;
  run: BonusNoSettingsDto;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<EditPercentageForm>();
  const { message } = AntdApp.useApp();

  const update = useMutation({
    mutationFn: (v: EditPercentageForm) => bonusRunsApi.update(run.id, v),
    onSuccess: (updated) => {
      message.success(
        `${formatBonusRun(updated.bonusNo)} now pays ${updated.percentageOfSalary}% of salary.`,
      );
      onSaved();
      onClose();
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });

  return (
    <Modal
      title={`Edit ${formatBonusRun(run.bonusNo)} percentage`}
      open={open}
      onCancel={onClose}
      okText="Save"
      confirmLoading={update.isPending}
      onOk={() => form.validateFields().then((v) => update.mutateAsync(v))}
      destroyOnHidden
      width={440}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ percentageOfSalary: run.percentageOfSalary }}
      >
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
