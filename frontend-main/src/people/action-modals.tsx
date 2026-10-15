// Employee action modals — one component per HR action, dispatched via a
// single <EmployeeActionModal action="…"> from the detail-page kebab and
// the Employment-tab inline buttons. All modals invalidate the detail
// query on success and surface RFC7807 violations inline through
// applyApiErrorToForm.

import { useEffect, useState, type ReactNode } from 'react';
import {
  Alert,
  App,
  Avatar,
  Button,
  Checkbox,
  DatePicker,
  Drawer,
  Form,
  Input,
  Modal,
  Radio,
  Skeleton,
  Select,
  Switch,
  Table,
  Tag,
  theme as antdTheme,
  Typography,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  Calculator,
  CalendarClock,
  ClipboardList,
  Eye,
  Gift,
  Landmark,
  Package,
  Timer,
  TrendingDown,
  TrendingUp,
  UserRound,
  Wallet,
} from 'lucide-react';

import { KvList, MoneyCell } from '@/components';
import { formatDateShort } from '@/lib/dates';
import { AssetDetailModal, belongingIssuanceApi } from '@/belongings';
import {
  applyApiErrorToForm,
  getErrorMessage,
  isApiError,
} from '@/lib';
import {
  settlementApi,
  type SettlementAdjustmentType,
  type SettlementPreviewDto,
  type SubmitSettlementRequest,
} from '@/payroll';
import { employeesApi } from './api';
import { invalidateEmployeeReads, patchEmployeeDetail } from './cache';
import type {
  EmployeeDto,
  ExitDto,
  ResignRequest,
  SuspendRequest,
  SuspensionDto,
  TerminateRequest,
} from './types';
import type { BelongingIssuanceDto } from '@/belongings';

const { Text, Paragraph } = Typography;

export type EmployeeActionKey =
  | 'confirm'
  | 'extend'
  | 'resign'
  | 'terminate'
  | 'withdraw-exit'
  | 'suspend'
  | 'lift'
  | 'grant-ess'
  | 'revoke-ess';

type DispatchProps = {
  /** Active action key, or null to render nothing. */
  action: EmployeeActionKey | null;
  employee: EmployeeDto;
  onClose: () => void;
};

export function EmployeeActionModal({ action, employee, onClose }: DispatchProps) {
  if (!action || !employee.id) return null;
  const id = employee.id;
  switch (action) {
    case 'confirm':
      return <ConfirmProbationModal id={id} onClose={onClose} />;
    case 'extend':
      return <ExtendProbationModal id={id} onClose={onClose} />;
    case 'resign':
      return (
        <ResignModal id={id} employee={employee} onClose={onClose} />
      );
    case 'terminate':
      return (
        <TerminateFlow id={id} employee={employee} onClose={onClose} />
      );
    case 'withdraw-exit':
      return <WithdrawExitModal id={id} onClose={onClose} />;
    case 'suspend':
      return <SuspendModal id={id} onClose={onClose} />;
    case 'lift':
      return <LiftSuspensionModal id={id} onClose={onClose} />;
    case 'grant-ess':
      return <GrantEssModal id={id} employee={employee} onClose={onClose} />;
    case 'revoke-ess':
      return <RevokeEssModal id={id} onClose={onClose} />;
    default:
      return null;
  }
}

/* --------------------- shared helpers --------------------- */

/**
 * Patch the cached EmployeeDetail with what we know from the mutation
 * response, then trigger background refetches of every employee-shaped
 * read. The optimistic patch sidesteps the BFF cache → Kafka-event race
 * (the composite is busted asynchronously, so a plain refetch right after
 * the mutation often returns stale data).
 */
function useOptimisticDetailUpdate(employeeId: number) {
  const qc = useQueryClient();
  return (patch: Parameters<typeof patchEmployeeDetail>[2]) => {
    patchEmployeeDetail(qc, employeeId, patch);
    invalidateEmployeeReads(qc, employeeId);
  };
}

type FormErrorState = { topLevel: string | null };
function useFormErrorState() {
  return useState<FormErrorState>({ topLevel: null });
}

function dateValueToString(d: dayjs.Dayjs | null | undefined): string | undefined {
  return d ? d.format('YYYY-MM-DD') : undefined;
}

/* --------------------- Confirm probation --------------------- */

function ConfirmProbationModal({ id, onClose }: { id: number; onClose: () => void }) {
  const [form] = Form.useForm<{ notes?: string }>();
  const { message } = App.useApp();
  const optimistic = useOptimisticDetailUpdate(id);
  const [errState, setErrState] = useFormErrorState();

  const mutation = useMutation({
    mutationFn: (body: { notes?: string }) => employeesApi.confirm(id, body),
    onSuccess: (response) => {
      message.success('Employee confirmed.');
      // Confirm clears the active probation; the response carries the new
      // confirmationDate. Patching activeProbation requires touching the
      // composite-only field — leave that to the BFF refetch.
      optimistic({ employee: response });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setErrState({ topLevel: getErrorMessage(e) });
      }
    },
  });

  return (
    <Modal
      open
      title="Confirm employee"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Confirm"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      <Paragraph type="secondary">
        Marks the active probation contract as expired and creates a new
        permanent contract starting today. The employee's confirmation date is
        set to today.
      </Paragraph>
      {errState.topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={errState.topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ notes: '' }}
        onFinish={(values) => {
          setErrState({ topLevel: null });
          mutation.mutate({ notes: values.notes?.trim() || undefined });
        }}
      >
        <Form.Item name="notes" label="Notes (optional)">
          <Input.TextArea rows={3} placeholder="Confirmation note for the record" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------- Extend probation --------------------- */

function ExtendProbationModal({ id, onClose }: { id: number; onClose: () => void }) {
  const [form] = Form.useForm<{ newProbationDays: number; reason: string }>();
  const { message } = App.useApp();
  const optimistic = useOptimisticDetailUpdate(id);
  const [errState, setErrState] = useFormErrorState();

  const mutation = useMutation({
    mutationFn: (body: { newProbationDays: number; reason: string }) =>
      employeesApi.extendProbation(id, body),
    onSuccess: (response) => {
      message.success('Probation extended.');
      optimistic({ employee: response });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setErrState({ topLevel: getErrorMessage(e) });
      }
    },
  });

  return (
    <Modal
      open
      title="Extend probation"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Extend probation"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      <Paragraph type="secondary">
        Probation can only be extended once. Pick the new total length — must
        be longer than the current setting.
      </Paragraph>
      {errState.topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={errState.topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ newProbationDays: 120 }}
        onFinish={(values) => {
          setErrState({ topLevel: null });
          mutation.mutate(values);
        }}
      >
        <Form.Item
          name="newProbationDays"
          label="New probation length"
          rules={[{ required: true, message: 'Pick a new probation length' }]}
        >
          <Radio.Group
            optionType="button"
            buttonStyle="solid"
            options={[
              { value: 120, label: '120 days' },
              { value: 180, label: '180 days' },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="reason"
          label="Reason"
          rules={[{ required: true, message: 'Reason is required' }]}
        >
          <Input.TextArea rows={3} placeholder="Why is the probation being extended?" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------- Resign --------------------- */

function ResignModal({
  id,
  employee,
  onClose,
}: {
  id: number;
  employee: EmployeeDto;
  onClose: () => void;
}) {
  type ResignForm = {
    lastWorkingDay: dayjs.Dayjs;
    reason?: string;
    notes?: string;
    reassignReportsTo?: number;
  };

  const [form] = Form.useForm<ResignForm>();
  const { message } = App.useApp();
  const optimistic = useOptimisticDetailUpdate(id);
  const [errState, setErrState] = useFormErrorState();
  const hasReports = (employee.directReportsCount ?? 0) > 0;

  const mutation = useMutation({
    mutationFn: (body: ResignRequest) => employeesApi.resign(id, body),
    onSuccess: (exit: ExitDto) => {
      message.success('Resignation submitted.');
      optimistic({
        exit,
        employee: { employmentStatus: 'ON_NOTICE' },
      });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setErrState({ topLevel: getErrorMessage(e) });
      }
    },
  });

  return (
    <Modal
      open
      title="Submit resignation"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Submit resignation"
      confirmLoading={mutation.isPending}
      destroyOnHidden
      width={520}
    >
      <Paragraph type="secondary">
        Status flips to <Text strong>On notice</Text>. ESS access continues
        through the notice period; the termination saga runs at 02:00 the day
        after the last working day.
      </Paragraph>
      {errState.topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={errState.topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        onFinish={(values) => {
          setErrState({ topLevel: null });
          mutation.mutate({
            lastWorkingDay: dateValueToString(values.lastWorkingDay) ?? '',
            reason: values.reason?.trim() || undefined,
            notes: values.notes?.trim() || undefined,
            reassignReportsTo: values.reassignReportsTo,
          });
        }}
      >
        <Form.Item
          name="lastWorkingDay"
          label="Last working day"
          rules={[{ required: true, message: 'Pick the last working day' }]}
        >
          <DatePicker
            style={{ width: '100%' }}
            disabledDate={(d) => d.isBefore(dayjs().startOf('day'))}
          />
        </Form.Item>
        <Form.Item name="reason" label="Reason (optional)">
          <Input placeholder="Personal · Better opportunity · Relocation…" />
        </Form.Item>
        <Form.Item name="notes" label="Notes (optional)">
          <Input.TextArea rows={3} />
        </Form.Item>
        {hasReports ? (
          <Form.Item
            name="reassignReportsTo"
            label="Reassign direct reports to"
            rules={[
              { required: true, message: 'Reports must be reassigned before exit' },
            ]}
            extra="Required because this employee manages others. Enter the new manager's employee id."
          >
            <Input type="number" placeholder="Employee id" />
          </Form.Item>
        ) : null}
      </Form>
    </Modal>
  );
}

/* --------------------- Terminate --------------------- */

/**
 * Terminating is two steps: review the live full-and-final settlement
 * preview first (big modal, read-only), then the short-and-irreversible
 * confirmation modal that actually calls the terminate mutation.
 */
function TerminateFlow({
  id,
  employee,
  onClose,
}: {
  id: number;
  employee: EmployeeDto;
  onClose: () => void;
}) {
  // Null while on the preview step; set once HR continues with the chosen
  // settlement figures, which the confirm step submits before terminating.
  const [settlement, setSettlement] = useState<SettlementSelection | null>(null);

  if (!settlement) {
    return (
      <SettlementPreviewModal
        employeeId={id}
        onCancel={onClose}
        onContinue={setSettlement}
      />
    );
  }

  return (
    <TerminateModal id={id} employee={employee} settlement={settlement} onClose={onClose} />
  );
}

/** What the settlement preview hands to the confirm step. */
type SettlementSelection = {
  lastWorkingDay: string;
  adjustmentType: SettlementAdjustmentType;
  adjustmentPercentage: number;
};

function toSettlementRequest(
  employeeId: number,
  s: SettlementSelection,
  remarks: string | undefined,
): SubmitSettlementRequest {
  const percent = `PERCENT_${s.adjustmentPercentage}`;
  return {
    employeeId,
    lastWorkingDay: s.lastWorkingDay,
    settlementAdjustmentType: s.adjustmentType,
    noticePayPercentage: s.adjustmentType === 'NOTICE_PAY' ? percent : 'NONE',
    forcedDeductionPercentage: s.adjustmentType === 'FORCED_DEDUCTION' ? percent : 'PERCENT_0',
    bonusRate: 0,
    remarks,
  };
}

/** Titled card used for every detail block, so they line up in a grid. */
export function InfoCard({
  icon,
  title,
  extra,
  children,
}: {
  icon: ReactNode;
  title: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
}) {
  const { token } = antdTheme.useToken();
  return (
    <div
      style={{
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadiusLG,
        background: token.colorBgContainer,
        overflow: 'hidden',
        minWidth: 0,
        height: '100%',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          padding: '10px 16px',
          background: token.colorFillAlter,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <span style={{ display: 'inline-flex', color: token.colorPrimary }}>{icon}</span>
          <Text strong>{title}</Text>
        </span>
        {extra ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {extra}
          </Text>
        ) : null}
      </div>
      <div style={{ padding: '12px 16px' }}>{children}</div>
    </div>
  );
}

export type LedgerRowItem = {
  label: string;
  hint?: ReactNode;
  /** Always positive — sign/color is derived from `tone`. */
  amount: number;
};

/**
 * One side of the earnings/deductions statement — an itemized list with a
 * bold subtotal footer, styled like a standard payslip column so additions
 * and subtractions read at a glance without cross-referencing scattered
 * cards.
 */
export function LedgerCard({
  title,
  icon,
  tone,
  rows,
  totalLabel,
  totalAmount,
}: {
  title: string;
  icon: ReactNode;
  tone: 'addition' | 'deduction';
  rows: LedgerRowItem[];
  totalLabel: string;
  totalAmount: number;
}) {
  const { token } = antdTheme.useToken();
  const accent = tone === 'addition' ? token.colorSuccess : token.colorError;
  const sign = tone === 'addition' ? 1 : -1;

  return (
    <div
      style={{
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadiusLG,
        background: token.colorBgContainer,
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 9,
          padding: '13px 20px',
          background: token.colorFillAlter,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <span style={{ display: 'inline-flex', color: accent }}>{icon}</span>
        <Text strong style={{ fontSize: 15 }}>
          {title}
        </Text>
      </div>
      <div style={{ padding: '4px 20px' }}>
        {rows.map((r, i) => (
          <div
            key={r.label + i}
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'baseline',
              gap: 12,
              padding: '10px 0',
              borderBottom:
                i < rows.length - 1 ? `1px dashed ${token.colorBorderSecondary}` : 'none',
            }}
          >
            <div style={{ minWidth: 0 }}>
              <Text style={{ display: 'block' }}>{r.label}</Text>
              {r.hint ? (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {r.hint}
                </Text>
              ) : null}
            </div>
            <MoneyCell
              amount={r.amount * sign}
              showSign
              coloredSign
              style={{ fontWeight: 500, flexShrink: 0 }}
            />
          </div>
        ))}
      </div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 12,
          padding: '14px 20px',
          background: token.colorFillAlter,
          borderTop: `1px solid ${token.colorBorderSecondary}`,
          marginTop: 'auto',
        }}
      >
        <Text strong>{totalLabel}</Text>
        <MoneyCell
          amount={totalAmount * sign}
          showSign
          coloredSign
          style={{ fontWeight: 700, fontSize: token.fontSizeLG }}
        />
      </div>
    </div>
  );
}

function SettlementPreviewModal({
  employeeId,
  onCancel,
  onContinue,
}: {
  employeeId: number;
  onCancel: () => void;
  onContinue: (selection: SettlementSelection) => void;
}) {
  const { token } = antdTheme.useToken();

  // Preview can be checked against any last-working-day the user picks, so
  // HR can review figures before deciding — actual termination (next step)
  // is still immediate and always effective today regardless of what's
  // previewed here (see the alert in TerminateModal).
  const [lastWorkingDayDate, setLastWorkingDayDate] = useState<dayjs.Dayjs>(() => dayjs());
  const [adjustmentType, setAdjustmentType] = useState<SettlementAdjustmentType>('NONE');
  const [adjustmentPercentage, setAdjustmentPercentage] = useState(100);
  // Preview-only, same as the adjustment controls above — unchecking excludes
  // annual leave encashment from the previewed totals below but isn't sent
  // to the backend on termination (no API field exists for it yet).
  const [payAnnualLeave, setPayAnnualLeave] = useState(true);
  const lastWorkingDay = lastWorkingDayDate.format('YYYY-MM-DD');

  const query = useQuery({
    queryKey: ['settlement-preview', employeeId, lastWorkingDay],
    queryFn: ({ signal }) => settlementApi.preview(employeeId, lastWorkingDay, signal),
  });

  const assetsQuery = useQuery({
    queryKey: ['belonging-issuance', 'employee', employeeId, 'active'],
    queryFn: ({ signal }) => belongingIssuanceApi.employeeActive(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  const s: SettlementPreviewDto | undefined = query.data;
  const assets = assetsQuery.data ?? [];
  const [selectedAsset, setSelectedAsset] = useState<BelongingIssuanceDto | null>(null);

  useEffect(() => {
    const policy = s?.terminationPolicy;
    if (!policy) return;
    setAdjustmentType(
      policy.adjustmentType === 'NOTICE_PAY' || policy.adjustmentType === 'FORCED_DEDUCTION'
        ? policy.adjustmentType
        : 'NONE',
    );
    const percentage = policy.adjustmentType === 'NOTICE_PAY'
      ? policy.noticePayPercentage
      : policy.forcedDeductionPercentage;
    const parsedPercentage = typeof percentage === 'number'
      ? percentage
      : Number.parseInt(percentage.replace('PERCENT_', ''), 10);
    setAdjustmentPercentage(Number.isFinite(parsedPercentage) ? parsedPercentage : 0);
  }, [s]);

  const sectionLabel = (label: string) => (
    <Text
      type="secondary"
      style={{
        fontSize: 11,
        textTransform: 'uppercase',
        letterSpacing: '0.06em',
        display: 'block',
        marginBottom: 6,
      }}
    >
      {label}
    </Text>
  );

  return (
    <Drawer
      open
      // Same footprint as the settlement review drawer: leaves room for the
      // expanded sidebar (256px) so the nav stays visible beside it.
      size="calc(100vw - 256px)"
      destroyOnHidden
      onClose={onCancel}
      styles={{ body: { padding: 16 } }}
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <Wallet size={18} color={token.colorPrimary} />
          <span style={{ fontWeight: 600 }}>Employee Leaving — Full &amp; Final Settlement</span>
          <Tag color="orange" style={{ marginInlineEnd: 0 }}>
            Preview
          </Tag>
        </div>
      }
      footer={
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 12,
          }}
        >
          <Text type="secondary" style={{ fontSize: 13 }}>
            Figures are a live preview for the selected last working day — the exit
            itself remains effective today.
          </Text>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button size="large" onClick={onCancel}>
              Cancel
            </Button>
            <Button
              type="primary"
              danger
              size="large"
              disabled={!s}
              onClick={() => onContinue({ lastWorkingDay, adjustmentType, adjustmentPercentage })}
            >
              Continue to termination
            </Button>
          </div>
        </div>
      }
    >
      {query.isLoading ? <Skeleton active paragraph={{ rows: 12 }} /> : null}

      {query.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load the settlement preview"
          description={getErrorMessage(query.error)}
        />
      ) : null}

      {s
        ? (() => {
            const excessiveLeaveDeduction =
              (s.annualExcessiveLeave?.excessiveAmount ?? 0) + (s.medicalAndCasualExcessiveLeave?.excessiveAmount ?? 0);
            const totalDeductions = s.totalDeductions + s.tax + excessiveLeaveDeduction;
            const adjustmentBase = s.earnedSalary.monthlyBasicSalary;
            const adjustmentAmount = adjustmentBase * (adjustmentPercentage / 100);
            const noticePayAmount = adjustmentType === 'NOTICE_PAY' ? adjustmentAmount : 0;
            const forcedDeductionAmount = adjustmentType === 'FORCED_DEDUCTION' ? adjustmentAmount : 0;
            // s.grossSettlement / s.net are backend-computed and already
            // include annual leave encashment - unchecking "Pay annual leave"
            // subtracts it back out for this preview only.
            const excludedAnnualLeaveAmount = !payAnnualLeave ? (s.annualLeave?.encashmentAmount ?? 0) : 0;
            const totalAdditions = s.grossSettlement + noticePayAmount - excludedAnnualLeaveAmount;
            const adjustedTotalDeductions = totalDeductions + forcedDeductionAmount;
            const netPayable =
              s.net + noticePayAmount - forcedDeductionAmount - excludedAnnualLeaveAmount - excessiveLeaveDeduction;

            const additionRows: LedgerRowItem[] = [
              {
                label: 'Basic salary (earned)',
                hint: (
                  <>
                    Of monthly basic <MoneyCell amount={s.earnedSalary.monthlyBasicSalary} compact />
                  </>
                ),
                amount: s.earnedSalary.earnedBasic,
              },
              { label: 'Allowances', amount: s.earnedSalary.allowances },
              {
                label: 'Gratuity',
                hint: s.gratuity.eligible
                  ? `${s.gratuity.completedYears} completed years of service`
                  : 'Not eligible',
                amount: s.gratuity.amount,
              },
              // Annual leave carries its own "pay or not" checkbox (adjustment
              // bar above) - unchecked drops this row entirely so the ledger
              // matches what's actually included in the totals.
              ...(s.annualLeave
                ? payAnnualLeave
                  ? [
                      {
                        label: 'Leave encashment',
                        hint: `${s.annualLeave.leaveName} · ${s.annualLeave.payableDays} payable days`,
                        amount: s.annualLeave.encashmentAmount,
                      },
                    ]
                  : []
                : s.leaveSummary
                  ? [
                      {
                        label: 'Leave encashment',
                        hint: `${s.leaveSummary.leaveName} · ${s.leaveSummary.payableDays} payable days`,
                        amount: s.leaveSummary.encashmentAmount,
                      },
                    ]
                  : []),
              {
                label: 'Bonus',
                hint: s.bonus.name || undefined,
                amount: s.bonus.outstanding,
              },
              ...(noticePayAmount > 0
                ? [{ label: 'Notice pay', hint: `${adjustmentPercentage}% of monthly basic`, amount: noticePayAmount }]
                : []),
            ];

            const deductionRows: LedgerRowItem[] = [
              {
                label: 'Advances',
                hint: (
                  <>
                    Permanent <MoneyCell amount={s.advances.permanent} compact /> · Short term{' '}
                    <MoneyCell amount={s.advances.shortTerm} compact />
                  </>
                ),
                amount: s.advances.total,
              },
              ...(s.leaveSummary && s.leaveSummary.recoveryAmount > 0
                ? [
                    {
                      label: 'Leave recovery',
                      hint: `${s.leaveSummary.recoverableDays} recoverable days`,
                      amount: s.leaveSummary.recoveryAmount,
                    },
                  ]
                : []),
              // Excessive leave isn't folded into the backend's totalDeductions -
              // it's deducted here in the preview, same as the manual
              // notice-pay/forced-deduction adjustment.
              ...(s.annualExcessiveLeave && s.annualExcessiveLeave.excessiveAmount > 0
                ? [
                    {
                      label: 'Annual leave — excessive',
                      hint: `${s.annualExcessiveLeave.excessiveDays} excessive days`,
                      amount: s.annualExcessiveLeave.excessiveAmount,
                    },
                  ]
                : []),
              ...(s.medicalAndCasualExcessiveLeave && s.medicalAndCasualExcessiveLeave.excessiveAmount > 0
                ? [
                    {
                      label: 'Medical & casual leave — excessive',
                      hint: `${s.medicalAndCasualExcessiveLeave.excessiveDays} excessive days`,
                      amount: s.medicalAndCasualExcessiveLeave.excessiveAmount,
                    },
                  ]
                : []),
              { label: 'Fair price shop', amount: s.fairPriceShop.outstandingAmount },
              ...s.outstandingDeductions.map((d) => ({ label: d.code, amount: d.amount })),
              { label: 'Tax withheld', amount: s.tax },
              ...(forcedDeductionAmount > 0
                ? [
                    {
                      label: 'Forced deduction',
                      hint: `${adjustmentPercentage}% of monthly basic`,
                      amount: forcedDeductionAmount,
                    },
                  ]
                : []),
            ];

            const att = s.attendanceSummary;
            const ot = att?.normalOvertime;
            const al = s.annualLeave;
            const mc = s.medicalAndCasualLeave;
            const otValue = (b: { hours: string; amount: number } | undefined) =>
              b ? (
                <>
                  {b.hours} · <MoneyCell amount={b.amount} compact />
                </>
              ) : (
                '—'
              );

            return (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {/* Identity strip */}
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    flexWrap: 'wrap',
                    gap: 16,
                    padding: '12px 16px',
                    borderRadius: token.borderRadiusLG,
                    background: token.colorFillAlter,
                    border: `1px solid ${token.colorBorderSecondary}`,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 14, minWidth: 0 }}>
                    <Avatar
                      size={52}
                      icon={<UserRound size={26} />}
                      style={{ background: token.colorPrimary, flexShrink: 0 }}
                    />
                    <div style={{ minWidth: 0 }}>
                      <Text strong style={{ fontSize: token.fontSizeLG, display: 'block', lineHeight: 1.25 }}>
                        {s.employee.employeeName}
                      </Text>
                      <Text type="secondary">
                        {[s.employee.designation, s.employee.department, s.employee.section]
                          .filter(Boolean)
                          .join(' · ')}
                      </Text>
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    <Tag>Code {s.employee.employeeCode}</Tag>
                    {s.employee.grade ? (
                      <Tag>
                        Grade {s.employee.grade}
                        {s.employee.gradeType ? ` · ${s.employee.gradeType}` : ''}
                      </Tag>
                    ) : null}
                    {s.employee.hireDate ? <Tag>Hired {formatDateShort(s.employee.hireDate)}</Tag> : null}
                    {s.employee.gratuityStartDate ? (
                      <Tag>Gratuity from {formatDateShort(s.employee.gratuityStartDate)}</Tag>
                    ) : null}
                    <Tag color="red">Last working day {formatDateShort(s.lastWorkingDay)}</Tag>
                  </div>
                </div>

                {/* Settlement inputs — everything HR can change, in one bar */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
                    gap: 0,
                    borderRadius: token.borderRadiusLG,
                    border: `1px solid ${token.colorBorderSecondary}`,
                    background: token.colorBgContainer,
                    overflow: 'hidden',
                  }}
                >
                  <div style={{ padding: '12px 20px' }}>
                    {sectionLabel('Last working day')}
                    <DatePicker
                      value={lastWorkingDayDate}
                      onChange={(d) => setLastWorkingDayDate(d ?? dayjs())}
                      allowClear={false}
                      format="DD MMM YYYY"
                      style={{ width: 200 }}
                    />
                  </div>
                  <div
                    style={{
                      padding: '12px 20px',
                      borderInlineStart: `1px solid ${token.colorBorderSecondary}`,
                    }}
                  >
                    {sectionLabel('Settlement adjustment')}
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                      <Radio.Group
                        value={adjustmentType}
                        optionType="button"
                        buttonStyle="solid"
                        options={[
                          { value: 'NONE', label: 'None' },
                          { value: 'NOTICE_PAY', label: 'Notice pay' },
                          { value: 'FORCED_DEDUCTION', label: 'Forced deduction' },
                        ]}
                        onChange={(event) => {
                          setAdjustmentType(event.target.value);
                          if (event.target.value === 'NOTICE_PAY' && adjustmentPercentage === 75) {
                            setAdjustmentPercentage(100);
                          }
                        }}
                      />
                      {adjustmentType !== 'NONE' ? (
                        <>
                          <Select
                            value={adjustmentPercentage}
                            style={{ width: 88 }}
                            // Backend enum: notice pay has no 75% step.
                            options={(adjustmentType === 'NOTICE_PAY' ? [25, 50, 100] : [25, 50, 75, 100]).map(
                              (value) => ({ value, label: `${value}%` }),
                            )}
                            onChange={setAdjustmentPercentage}
                          />
                          <MoneyCell
                            amount={adjustmentAmount}
                            style={{
                              fontWeight: 600,
                              whiteSpace: 'nowrap',
                              color: adjustmentType === 'NOTICE_PAY' ? token.colorSuccess : token.colorError,
                            }}
                          />
                        </>
                      ) : null}
                    </div>
                  </div>
                  {s.annualLeave ? (
                    <div
                      style={{
                        padding: '12px 20px',
                        borderInlineStart: `1px solid ${token.colorBorderSecondary}`,
                      }}
                    >
                      {sectionLabel('Annual leave encashment')}
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, minHeight: 32 }}>
                        <Checkbox
                          checked={payAnnualLeave}
                          onChange={(event) => setPayAnnualLeave(event.target.checked)}
                        >
                          Pay encashment
                        </Checkbox>
                        <MoneyCell amount={s.annualLeave.encashmentAmount} style={{ fontWeight: 600 }} />
                      </div>
                    </div>
                  ) : null}
                </div>

                {/* Money: additions | deductions | net payable + assets */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))',
                    gap: 12,
                    // Equal-height columns: both ledger totals line up at the
                    // bottom and the assets card fills the right column.
                    alignItems: 'stretch',
                  }}
                >
                  <LedgerCard
                    title="Additions"
                    icon={<TrendingUp size={18} />}
                    tone="addition"
                    rows={additionRows}
                    totalLabel="Total additions"
                    totalAmount={totalAdditions}
                  />
                  <LedgerCard
                    title="Deductions"
                    icon={<TrendingDown size={18} />}
                    tone="deduction"
                    rows={deductionRows}
                    totalLabel="Total deductions"
                    totalAmount={adjustedTotalDeductions}
                  />
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 12, minWidth: 0 }}>
                    <div
                      style={{
                        padding: '18px 20px',
                        borderRadius: token.borderRadiusLG,
                        background: token.colorPrimaryBg,
                        border: `1px solid ${token.colorPrimaryBorder}`,
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                        <Calculator size={18} color={token.colorPrimary} />
                        <Text strong style={{ fontSize: token.fontSizeLG }}>
                          Net payable
                        </Text>
                      </div>
                      <MoneyCell
                        amount={netPayable}
                        showSign
                        coloredSign
                        style={{ fontWeight: 700, fontSize: token.fontSizeHeading2, display: 'block' }}
                      />
                      <div
                        style={{
                          display: 'grid',
                          gridTemplateColumns: 'auto 1fr',
                          gap: '4px 12px',
                          marginTop: 10,
                          fontSize: 13,
                        }}
                      >
                        <Text type="secondary">Additions</Text>
                        <MoneyCell amount={totalAdditions} style={{ textAlign: 'right' }} />
                        <Text type="secondary">Deductions</Text>
                        <MoneyCell amount={adjustedTotalDeductions} style={{ textAlign: 'right' }} />
                        <Text type="secondary">of which tax</Text>
                        <MoneyCell amount={s.tax} style={{ textAlign: 'right' }} />
                      </div>
                    </div>

                    <div style={{ flex: 1 }}>
                      <InfoCard
                        icon={<Package size={16} />}
                        title="Allocated assets"
                        extra={assets.length > 0 ? `${assets.length} to recover` : undefined}
                      >
                        {assetsQuery.isLoading ? <Skeleton active paragraph={{ rows: 2 }} title={false} /> : null}
                        {assetsQuery.isError ? (
                          <Alert
                            type="error"
                            showIcon
                            title="Couldn't load allocated assets"
                            description={getErrorMessage(assetsQuery.error)}
                          />
                        ) : null}
                        {!assetsQuery.isLoading && !assetsQuery.isError && assets.length === 0 ? (
                          <Text type="secondary">No allocated assets</Text>
                        ) : null}
                        {assets.map((asset, i) => (
                          <div
                            key={asset.id}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              gap: 8,
                              padding: '8px 0',
                              borderTop: i > 0 ? `1px solid ${token.colorBorderSecondary}` : undefined,
                            }}
                          >
                            <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                              <Text style={{ fontSize: 13 }} ellipsis>
                                {asset.belongingName ?? '—'}
                              </Text>
                              {asset.assetModel ? (
                                <Text type="secondary" style={{ fontSize: 12 }} ellipsis>
                                  {asset.assetModel}
                                </Text>
                              ) : null}
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                              {asset.activationStatus ? (
                                <Tag style={{ marginInlineEnd: 0 }}>
                                  {asset.activationStatus === 'INACTIVE' ? 'Inactive' : 'Active'}
                                </Tag>
                              ) : null}
                              <Button
                                size="small"
                                icon={<Eye size={14} />}
                                onClick={() => setSelectedAsset(asset)}
                                aria-label="View asset details"
                              />
                            </div>
                          </div>
                        ))}
                      </InfoCard>
                    </div>
                  </div>
                </div>

                {/* Attendance / overtime / leave — always open, side by side */}
                {att || al || mc || s.leaveSummary ? (
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                      gap: 12,
                      alignItems: 'stretch',
                    }}
                  >
                    {att ? (
                      <InfoCard
                        icon={<CalendarClock size={16} />}
                        title="Attendance"
                        extra={`${formatDateShort(att.from)} → ${formatDateShort(att.to)}`}
                      >
                        <KvList
                          minColumnWidth={110}
                          items={[
                            { label: 'Present', value: att.attendance.presentDays },
                            { label: 'Half days', value: att.attendance.halfDays },
                            { label: 'Absent', value: att.attendance.absentDays },
                            { label: 'Leave', value: att.attendance.leaveDays },
                            { label: 'Weekends', value: att.attendance.weekendDays },
                            { label: 'Holidays', value: att.attendance.holidayDays },
                          ]}
                        />
                      </InfoCard>
                    ) : null}
                    {ot ? (
                      <InfoCard icon={<Timer size={16} />} title="Normal overtime">
                        <KvList
                          minColumnWidth={110}
                          items={[
                            { label: 'Approved', value: otValue(ot.approved) },
                            { label: 'Pending', value: otValue(ot.pending) },
                            { label: 'Unapplied', value: otValue(ot.unapplied) },
                            { label: 'Total', value: <strong>{otValue(ot.total)}</strong> },
                          ]}
                        />
                      </InfoCard>
                    ) : null}
                    {al ? (
                      <InfoCard
                        icon={<ClipboardList size={16} />}
                        title={al.leaveName || 'Annual leave'}
                        extra={`${formatDateShort(al.cycleStart)} → ${formatDateShort(al.cycleEnd)}`}
                      >
                        <KvList
                          minColumnWidth={110}
                          items={[
                            { label: 'Allocated', value: al.allocated },
                            { label: 'Earned', value: al.earned },
                            { label: 'Used', value: al.used },
                            { label: 'Pending', value: al.pending },
                            { label: 'Payable days', value: al.payableDays },
                            { label: 'Excessive days', value: s.annualExcessiveLeave?.excessiveDays ?? 0 },
                          ]}
                        />
                      </InfoCard>
                    ) : s.leaveSummary ? (
                      <InfoCard
                        icon={<ClipboardList size={16} />}
                        title={s.leaveSummary.leaveName}
                        extra={`${formatDateShort(s.leaveSummary.cycleStart)} → ${formatDateShort(s.leaveSummary.cycleEnd)}`}
                      >
                        <KvList
                          minColumnWidth={110}
                          items={[
                            { label: 'Allocated', value: s.leaveSummary.allocated },
                            { label: 'Accrued', value: s.leaveSummary.accrued },
                            { label: 'Used', value: s.leaveSummary.used },
                            { label: 'Pending', value: s.leaveSummary.pending },
                            { label: 'Payable days', value: s.leaveSummary.payableDays },
                            { label: 'Recoverable days', value: s.leaveSummary.recoverableDays },
                          ]}
                        />
                      </InfoCard>
                    ) : null}
                    {mc ? (
                      <InfoCard icon={<ClipboardList size={16} />} title="Medical & casual leave">
                        <KvList
                          minColumnWidth={110}
                          items={[
                            { label: 'Allocated', value: mc.allocated },
                            { label: 'Earned', value: mc.earned },
                            { label: 'Used', value: mc.used },
                            { label: 'Pending', value: mc.pending },
                            { label: 'Payable days', value: mc.payableDays },
                            {
                              label: 'Excessive days',
                              value: s.medicalAndCasualExcessiveLeave?.excessiveDays ?? 0,
                            },
                          ]}
                        />
                      </InfoCard>
                    ) : null}
                  </div>
                ) : null}

                {/* Breakdown tables — only when there's something to list */}
                {s.permanentAdvances.advances.length > 0 || s.bonus.events.length > 0 ? (
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(520px, 1fr))',
                      gap: 12,
                      alignItems: 'stretch',
                    }}
                  >
                    {s.permanentAdvances.advances.length > 0 ? (
                      <InfoCard
                        icon={<Landmark size={16} />}
                        title={`Permanent advances (${s.permanentAdvances.advances.length})`}
                        extra={
                          <>
                            Outstanding <MoneyCell amount={s.permanentAdvances.totalOutstanding} compact />
                          </>
                        }
                      >
                        <Table
                          size="small"
                          pagination={false}
                          scroll={{ x: 'max-content' }}
                          rowKey="advanceId"
                          dataSource={s.permanentAdvances.advances}
                          columns={[
                            { title: 'Request', dataIndex: 'requestCode' },
                            {
                              title: 'Received',
                              dataIndex: 'receivingDate',
                              render: (v: string) => formatDateShort(v),
                            },
                            { title: 'Original', dataIndex: 'originalAmount', render: (v: number) => <MoneyCell amount={v} /> },
                            { title: 'Recovered', dataIndex: 'recoveredAmount', render: (v: number) => <MoneyCell amount={v} /> },
                            {
                              title: 'Monthly deduction',
                              dataIndex: 'monthlyDeduction',
                              render: (v: number) => <MoneyCell amount={v} />,
                            },
                            {
                              title: 'Outstanding',
                              dataIndex: 'outstandingAmount',
                              render: (v: number) => <MoneyCell amount={v} />,
                            },
                          ]}
                        />
                      </InfoCard>
                    ) : null}
                    {s.bonus.events.length > 0 ? (
                      <InfoCard icon={<Gift size={16} />} title={`Bonus events (${s.bonus.events.length})`}>
                        <Table
                          size="small"
                          pagination={false}
                          scroll={{ x: 'max-content' }}
                          rowKey="bonusEventId"
                          dataSource={s.bonus.events}
                          columns={[
                            { title: 'Event', dataIndex: 'eventName' },
                            { title: 'Type', dataIndex: 'eventType' },
                            {
                              title: 'Reference date',
                              dataIndex: 'referenceDate',
                              render: (v: string) => formatDateShort(v),
                            },
                            { title: 'Eligible', dataIndex: 'eligibleAmount', render: (v: number) => <MoneyCell amount={v} /> },
                            { title: 'Paid', dataIndex: 'paidAmount', render: (v: number) => <MoneyCell amount={v} /> },
                            {
                              title: 'Outstanding',
                              dataIndex: 'outstandingAmount',
                              render: (v: number) => <MoneyCell amount={v} />,
                            },
                          ]}
                        />
                      </InfoCard>
                    ) : null}
                  </div>
                ) : null}

                <AssetDetailModal
                  asset={selectedAsset}
                  open={!!selectedAsset}
                  onClose={() => setSelectedAsset(null)}
                />
              </div>
            );
          })()
        : null}
    </Drawer>
  );
}

function TerminateModal({
  id,
  employee,
  settlement,
  onClose,
}: {
  id: number;
  employee: EmployeeDto;
  settlement: SettlementSelection;
  onClose: () => void;
}) {
  type TerminateForm = {
    reason: string;
    notes?: string;
    typedConfirmation: string;
  };

  const [form] = Form.useForm<TerminateForm>();
  const { message } = App.useApp();
  const qc = useQueryClient();
  const [errState, setErrState] = useFormErrorState();
  const expectedCode = employee.employeeCode ?? '';

  // Only the final-settlement submit is called here — the backend owns the
  // exit itself, so /employees/{id}/terminate is intentionally not called.
  const mutation = useMutation({
    mutationFn: (body: TerminateRequest) =>
      settlementApi.submit(
        toSettlementRequest(
          id,
          settlement,
          body.notes ? `${body.reason}\n${body.notes}` : body.reason,
        ),
      ),
    onSuccess: () => {
      message.success('Final settlement submitted.');
      invalidateEmployeeReads(qc, id);
      // New Draft must show on Termination Stages without a manual reload.
      void qc.invalidateQueries({ queryKey: ['settlements'] });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setErrState({ topLevel: getErrorMessage(e) });
      }
    },
  });

  return (
    <Modal
      open
      title="Terminate employm"
      okType="danger"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Terminate immediately"
      confirmLoading={mutation.isPending}
      destroyOnHidden
      width={520}
    >
      <Alert
        type="error"
        showIcon
        title="Immediate, irreversible action"
        description="Last working day is set to today. Self-service login is disabled, active sessions are revoked, and attendance balances are closed."
        style={{ marginBottom: 16 }}
      />
      {errState.topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={errState.topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        onFinish={(values) => {
          setErrState({ topLevel: null });
          mutation.mutate({
            reason: values.reason.trim(),
            notes: values.notes?.trim() || undefined,
          });
        }}
      >
        <Form.Item
          name="reason"
          label="Reason"
          rules={[{ required: true, message: 'Reason is required for termination' }]}
        >
          <Input.TextArea
            rows={3}
            placeholder="Misconduct · Performance · Restructuring…"
          />
        </Form.Item>
        <Form.Item name="notes" label="Notes (optional)">
          <Input.TextArea rows={3} />
        </Form.Item>
        <Form.Item
          name="typedConfirmation"
          label={`Type "${expectedCode}" to confirm`}
          rules={[
            { required: true, message: 'Type the employee code to confirm' },
            {
              validator: (_, value) =>
                value === expectedCode
                  ? Promise.resolve()
                  : Promise.reject(new Error('Employee code does not match')),
            },
          ]}
        >
          <Input placeholder={expectedCode} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------- Withdraw exit --------------------- */

function WithdrawExitModal({ id, onClose }: { id: number; onClose: () => void }) {
  const [form] = Form.useForm<{ reason: string }>();
  const { message } = App.useApp();
  const optimistic = useOptimisticDetailUpdate(id);
  const [errState, setErrState] = useFormErrorState();

  const mutation = useMutation({
    mutationFn: (body: { reason: string }) => employeesApi.withdrawExit(id, body),
    onSuccess: (exit: ExitDto) => {
      message.success('Exit withdrawn.');
      optimistic({
        exit,
        employee: { employmentStatus: 'ACTIVE' },
      });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setErrState({ topLevel: getErrorMessage(e) });
      }
    },
  });

  return (
    <Modal
      open
      title="Withdraw exit"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Withdraw exit"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      <Paragraph type="secondary">
        Reactivates the employee. Status returns to <Text strong>Active</Text>.
        Only works while the exit row is still pending.
      </Paragraph>
      {errState.topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={errState.topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        onFinish={(values) => {
          setErrState({ topLevel: null });
          mutation.mutate({ reason: values.reason.trim() });
        }}
      >
        <Form.Item
          name="reason"
          label="Reason"
          rules={[{ required: true, message: 'Reason is required' }]}
        >
          <Input.TextArea rows={3} placeholder="Why is the exit being withdrawn?" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------- Suspend --------------------- */

function SuspendModal({ id, onClose }: { id: number; onClose: () => void }) {
  type SuspendForm = {
    startDate: dayjs.Dayjs;
    endDate?: dayjs.Dayjs;
    reason: string;
    withPay: boolean;
  };

  const [form] = Form.useForm<SuspendForm>();
  const { message } = App.useApp();
  const optimistic = useOptimisticDetailUpdate(id);
  const [errState, setErrState] = useFormErrorState();

  const mutation = useMutation({
    mutationFn: (body: SuspendRequest) => employeesApi.suspend(id, body),
    onSuccess: (suspension: SuspensionDto) => {
      message.success('Employee suspended.');
      optimistic({
        activeSuspension: suspension,
        employee: { employmentStatus: 'SUSPENDED' },
      });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setErrState({ topLevel: getErrorMessage(e) });
      }
    },
  });

  return (
    <Modal
      open
      title="Suspend employee"
      okType="danger"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Suspend"
      confirmLoading={mutation.isPending}
      destroyOnHidden
      width={520}
    >
      <Paragraph type="secondary">
        Status flips to <Text strong>Suspended</Text>. ESS access is disabled
        until the suspension is lifted. Open-ended (no end date) is allowed.
      </Paragraph>
      {errState.topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={errState.topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ withPay: true, startDate: dayjs() }}
        onFinish={(values) => {
          setErrState({ topLevel: null });
          mutation.mutate({
            startDate: dateValueToString(values.startDate) ?? '',
            endDate: dateValueToString(values.endDate),
            reason: values.reason.trim(),
            withPay: values.withPay,
          });
        }}
      >
        <Form.Item
          name="startDate"
          label="Start date"
          rules={[{ required: true, message: 'Start date is required' }]}
        >
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="endDate" label="End date (optional)">
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="reason"
          label="Reason"
          rules={[{ required: true, message: 'Reason is required' }]}
        >
          <Input.TextArea rows={3} placeholder="Pending inquiry · Findings of misconduct…" />
        </Form.Item>
        <Form.Item
          name="withPay"
          label="With pay?"
          valuePropName="checked"
          extra="Without pay counts suspended days as LOP in payroll. With pay keeps the employee paid normally."
        >
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------- Lift suspension --------------------- */

function LiftSuspensionModal({ id, onClose }: { id: number; onClose: () => void }) {
  type LiftForm = { liftedOn?: dayjs.Dayjs; reason: string };
  const [form] = Form.useForm<LiftForm>();
  const { message } = App.useApp();
  const optimistic = useOptimisticDetailUpdate(id);
  const [errState, setErrState] = useFormErrorState();

  const mutation = useMutation({
    mutationFn: (body: { liftedOn?: string; reason: string }) =>
      employeesApi.liftSuspension(id, body),
    onSuccess: () => {
      message.success('Suspension lifted.');
      // Status restoration is server-side (ACTIVE / ON_NOTICE depending on
      // exit row). Clear the active suspension and let the BFF refetch fix
      // the rest.
      optimistic({ activeSuspension: null });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setErrState({ topLevel: getErrorMessage(e) });
      }
    },
  });

  return (
    <Modal
      open
      title="Lift suspension"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Lift suspension"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      <Paragraph type="secondary">
        Restores the employee's prior status. ESS access re-enabled if the
        employee has ESS.
      </Paragraph>
      {errState.topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={errState.topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ liftedOn: dayjs() }}
        onFinish={(values) => {
          setErrState({ topLevel: null });
          mutation.mutate({
            liftedOn: dateValueToString(values.liftedOn),
            reason: values.reason.trim(),
          });
        }}
      >
        <Form.Item name="liftedOn" label="Lifted on (optional)">
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="reason"
          label="Reason"
          rules={[{ required: true, message: 'Reason is required' }]}
        >
          <Input.TextArea rows={3} placeholder="Inquiry concluded · Cleared…" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------- Grant ESS --------------------- */

function GrantEssModal({
  id,
  employee,
  onClose,
}: {
  id: number;
  employee: EmployeeDto;
  onClose: () => void;
}) {
  type GrantForm = { username?: string; email?: string; temporaryPassword: string };
  const [form] = Form.useForm<GrantForm>();
  const { message } = App.useApp();
  const optimistic = useOptimisticDetailUpdate(id);
  const [errState, setErrState] = useFormErrorState();

  const mutation = useMutation({
    mutationFn: (body: GrantForm) =>
      employeesApi.grantEss(id, {
        username: body.username,
        email: body.email,
        temporaryPassword: body.temporaryPassword,
      }),
    onSuccess: (response) => {
      message.success('ESS access granted.');
      optimistic({ employee: response });
      onClose();
    },
    onError: (e) => {
      const { unmapped } = applyApiErrorToForm(form, e);
      if (unmapped.length > 0 || !isApiError(e) || e.violations.length === 0) {
        setErrState({ topLevel: getErrorMessage(e) });
      }
    },
  });

  const suggestedUsername = employee.workEmail?.split('@')[0] ?? '';
  const suggestedEmail = employee.workEmail ?? employee.personalEmail ?? '';

  return (
    <Modal
      open
      title="Grant ESS access"
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Grant access"
      confirmLoading={mutation.isPending}
      destroyOnHidden
      width={500}
    >
      <Paragraph type="secondary">
        Creates an auth-service user, sets a temporary password, and forces a
        change at first login. Username defaults to the work-email local part
        when left blank.
      </Paragraph>
      {errState.topLevel ? (
        <Alert type="error" showIcon style={{ marginBottom: 12 }} title={errState.topLevel} />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ username: suggestedUsername, email: suggestedEmail }}
        onFinish={(values) => {
          setErrState({ topLevel: null });
          mutation.mutate(values);
        }}
      >
        <Form.Item name="username" label="Username">
          <Input placeholder="Defaults to work email local-part" />
        </Form.Item>
        <Form.Item
          name="email"
          label="Email"
          rules={[{ type: 'email', message: 'Looks invalid' }]}
        >
          <Input placeholder="email@forwardsports.com" />
        </Form.Item>
        <Form.Item
          name="temporaryPassword"
          label="Temporary password"
          rules={[
            { required: true, message: 'Required' },
            { min: 10, message: 'Minimum 10 characters' },
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/* --------------------- Revoke ESS --------------------- */

function RevokeEssModal({ id, onClose }: { id: number; onClose: () => void }) {
  const { message } = App.useApp();
  const optimistic = useOptimisticDetailUpdate(id);
  const [topLevel, setTopLevel] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => employeesApi.revokeEss(id),
    onSuccess: (response) => {
      message.success('ESS access revoked.');
      optimistic({ employee: response });
      onClose();
    },
    onError: (e) => {
      setTopLevel(getErrorMessage(e));
    },
  });

  return (
    <Modal
      open
      title="Revoke ESS access"
      okType="danger"
      onCancel={onClose}
      onOk={() => mutation.mutate()}
      okText="Revoke access"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      <Paragraph>
        The user account will be disabled and all sessions revoked. The
        employee record is preserved; ESS can be re-granted later.
      </Paragraph>
      {topLevel ? (
        <Alert type="error" showIcon title={topLevel} />
      ) : null}
    </Modal>
  );
}
