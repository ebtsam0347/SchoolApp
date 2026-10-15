// Long-term advance request.
//
// Two modes:
//   - self (default): the target is the caller, resolved from /employees/me.
//   - targeted (`onBehalfOf` prop): Team requests opens the drawer from a
//     department roster row; the target is fixed and shown read-only.
//     NB: the live payroll contract still documents an `employeeId` other
//     than the caller's as HR-with-`payroll.write` only — payroll-service
//     must also accept `team.request.generation.longTermAdvance` for this
//     mode to submit.
// Backend enforces collateral checks (gratuity
// snapshot ≥ exposure) — surfaced inline via handleFormError.
//
// Financial period scopes the whole request (required on every create, not
// just the bonus leg) and defaults to the active fiscal year.
//
// Recovery plan mirrors the legacy Forward Sports screen: the monthly
// installment always applies, and on top of it the requester can offset part
// of the principal against the Eid bonuses (#1, #2, or both — of the selected
// period) and / or against annual-leave encashment in a chosen month. Those
// two are independent toggles; either, both, or neither. On the wire the bonus
// pair is `bonusOffset` / `bonusOffset2` + `financialYearCode`, and the AL
// pair is `annualLeaveOffset` + `annualLeaveMonth`.

import {
  Alert,
  App as AntdApp,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  Typography,
  theme as antdTheme,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useEffect } from 'react';
import { Info } from 'lucide-react';

import { DrawerForm } from '@/components';
import { handleFormError } from '@/lib';
import { employeesApi } from '@/people/api';
import { fiscalYearsApi, permanentAdvancesApi } from '../api';
import type { CreatePermanentAdvanceRequest } from '../types';

export type PermanentAdvanceOnBehalfTarget = {
  id: number;
  fullName: string;
  employeeCode?: string | null;
};

export type ApplyPermanentAdvanceDrawerProps = {
  open: boolean;
  onClose: () => void;
  /** File for this colleague instead of the caller. */
  onBehalfOf?: PermanentAdvanceOnBehalfTarget | null;
};

/** Which of the fiscal year's two Eid bonuses the offset comes out of. Not a
 *  wire field — it just decides whether `bonusOffset`, `bonusOffset2`, or
 *  both are sent. */
type BonusPick = '1' | '2' | '1+2';

const BONUS_PICK_OPTIONS: { value: BonusPick; label: string }[] = [
  { value: '1', label: 'Bonus # 1' },
  { value: '2', label: 'Bonus # 2' },
  { value: '1+2', label: 'Bonus # 1 + 2' },
];

type Values = {
  amount: number;
  monthlyDeduction: number;
  bonusEnabled?: boolean;
  financialYearCode?: string;
  bonusPick?: BonusPick;
  bonusOffset?: number;
  bonusOffset2?: number;
  otherEnabled?: boolean;
  annualLeaveMonth?: dayjs.Dayjs;
  annualLeaveOffset?: number;
  receivingDate?: dayjs.Dayjs;
  description?: string;
};

/** Sub-fields wiped when a toggle goes off, so a half-filled section can't
 *  leak into the payload or leave stale validation errors behind. Financial
 *  period isn't here — it's a top-level field, required either way. */
const BONUS_FIELDS = ['bonusPick', 'bonusOffset', 'bonusOffset2'] as const;
const OTHER_FIELDS = ['annualLeaveMonth', 'annualLeaveOffset'] as const;

export function ApplyPermanentAdvanceDrawer({
  open,
  onClose,
  onBehalfOf,
}: ApplyPermanentAdvanceDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const { token } = antdTheme.useToken();

  // Resolve own employee id only when this drawer is open and the caller
  // didn't pick someone else. Backend rejects create with no employeeId —
  // we surface a friendlier error if /employees/me 404s (rare: ESS without
  // an employee link).
  const meQuery = useQuery({
    queryKey: ['employees', 'me'],
    queryFn: ({ signal }) => employeesApi.me(signal),
    staleTime: 5 * 60_000,
    enabled: open && !onBehalfOf,
    retry: false,
  });

  const amount = Form.useWatch('amount', form);
  const monthly = Form.useWatch('monthlyDeduction', form);
  const bonusEnabled = Form.useWatch('bonusEnabled', form) === true;
  const otherEnabled = Form.useWatch('otherEnabled', form) === true;
  const bonusPick = Form.useWatch('bonusPick', form) ?? '1';
  const bonusOffset = Form.useWatch('bonusOffset', form);
  const bonusOffset2 = Form.useWatch('bonusOffset2', form);
  const annualLeaveOffset = Form.useWatch('annualLeaveOffset', form);

  const wantsBonus1 = bonusEnabled && bonusPick !== '2';
  const wantsBonus2 = bonusEnabled && bonusPick !== '1';

  // `financialYearCode` is required on every create (@NotNull), bonus leg or
  // not, so the active period loads with the drawer. Only the active period
  // is offered — an advance is always raised against the running fiscal
  // year. `/fiscal-years/current` is self-serve (no `payroll.read` needed),
  // unlike the full `/fiscal-years` list.
  const fyQuery = useQuery({
    queryKey: ['payroll', 'fiscal-years', 'current'],
    queryFn: ({ signal }) => fiscalYearsApi.current(signal),
    staleTime: 60_000,
    enabled: open,
    retry: false,
  });
  const currentFy = fyQuery.data ?? null;
  const currentFyCode = currentFy?.code ?? null;

  // Preselect the active period. Only fills a blank field, never overrides a
  // pick the requester already made.
  useEffect(() => {
    if (!currentFyCode) return;
    if (form.getFieldValue('financialYearCode')) return;
    form.setFieldValue('financialYearCode', currentFyCode);
  }, [currentFyCode, form]);

  const offsetTotal =
    (wantsBonus1 ? (bonusOffset ?? 0) : 0) +
    (wantsBonus2 ? (bonusOffset2 ?? 0) : 0) +
    (otherEnabled ? (annualLeaveOffset ?? 0) : 0);

  // Rough installments hint — purely informational; backend doesn't compute
  // a schedule on create. Offsets come off the principal first, so only the
  // remainder is recovered month by month.
  const viaMonthly =
    typeof amount === 'number' ? Math.max(amount - offsetTotal, 0) : null;
  const months =
    viaMonthly != null &&
    typeof monthly === 'number' &&
    viaMonthly > 0 &&
    monthly > 0
      ? Math.ceil(viaMonthly / monthly)
      : null;

  useEffect(() => {
    if (!open) form.resetFields();
  }, [open, form]);

  const mutation = useMutation({
    mutationFn: (body: CreatePermanentAdvanceRequest) =>
      permanentAdvancesApi.create(body),
    onSuccess: () => {
      message.success(
        onBehalfOf
          ? `Advance request submitted for ${onBehalfOf.fullName}`
          : 'Advance request submitted',
      );
      queryClient.invalidateQueries({ queryKey: ['payroll', 'permanent-advances'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const hint = (text: string) => (
    <Typography.Text style={{ fontSize: 12, color: token.colorTextSecondary }}>
      {text}
    </Typography.Text>
  );

  return (
    <DrawerForm<Values>
      title={onBehalfOf ? 'Request salary advance for a colleague' : 'Request salary advance'}
      open={open}
      onClose={onClose}
      form={form}
      size={560}
      submitText="Submit request"
      submitting={mutation.isPending}
      initialValues={{ bonusPick: '1' }}
      onFinish={(v) => {
        const employeeId = onBehalfOf?.id ?? meQuery.data?.id;
        if (!employeeId) {
          message.error("Couldn't resolve your employee record");
          return;
        }

        const bonusOn = v.bonusEnabled === true;
        const otherOn = v.otherEnabled === true;
        const pick = v.bonusPick ?? '1';
        const b1 = bonusOn && pick !== '2' ? v.bonusOffset || undefined : undefined;
        const b2 = bonusOn && pick !== '1' ? v.bonusOffset2 || undefined : undefined;
        const al = otherOn ? v.annualLeaveOffset || undefined : undefined;

        // Can't recover more than the principal.
        if ((b1 ?? 0) + (b2 ?? 0) + (al ?? 0) > v.amount) {
          message.error(
            'Bonus and other deductions together exceed the requested amount.',
          );
          return;
        }

        // Required on every request. The field is required + preselected, so
        // the fallback only covers a period list that never loaded.
        const fyCode = v.financialYearCode ?? currentFyCode;
        if (!fyCode) {
          message.error("Couldn't resolve the financial period");
          return;
        }

        // annualLeaveMonth is documented as nullable but the service does
        // `req.annualLeaveMonth().withDayOfMonth(1)` on the else branch of its
        // offset check — a null month NPEs into a 500. So always send one;
        // with no AL offset it's inert (backend normalises to day 1).
        const alMonth = (al != null ? v.annualLeaveMonth : undefined) ?? dayjs();

        mutation.mutate({
          employeeId,
          amount: v.amount,
          monthlyDeduction: v.monthlyDeduction,
          financialYearCode: fyCode,
          bonusOffset: b1,
          bonusOffset2: b2,
          annualLeaveOffset: al,
          annualLeaveMonth: alMonth.startOf('month').format('YYYY-MM-DD'),
          receivingDate: v.receivingDate?.format('YYYY-MM-DD'),
          description: v.description?.trim() || undefined,
        });
      }}
    >
      {onBehalfOf ? (
        <Alert
          type="info"
          showIcon
          title={`Applying for ${onBehalfOf.fullName}${
            onBehalfOf.employeeCode ? ` · ${onBehalfOf.employeeCode}` : ''
          }`}
          description="Close this drawer and pick another row to file for someone else."
          style={{ marginBottom: 12 }}
        />
      ) : null}
      <Alert
        type="info"
        showIcon
        icon={<Info size={14} />}
        title="Two-step approval"
        description={`HR reviews first, then Accounts. Once approved, the monthly amount is deducted from ${
          onBehalfOf ? 'their' : 'your'
        } payslip until the principal is cleared.`}
        style={{ marginBottom: 16 }}
      />

      {/* Scopes the whole request, bonus leg or not — the create endpoint
          rejects a missing period. Defaults to the active one. */}
      <Form.Item
        label="Financial period"
        name="financialYearCode"
        tooltip="Fiscal year this advance belongs to. Bonus offsets are taken from this period's bonuses."
        rules={[{ required: true, message: 'Pick the financial period' }]}
      >
        <Select
          loading={fyQuery.isLoading}
          placeholder="Select financial period"
          options={currentFy ? [{ value: currentFy.code, label: currentFy.code }] : []}
        />
      </Form.Item>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        <Form.Item
          label="Amount requested"
          name="amount"
          rules={[
            { required: true, message: 'Enter the principal amount' },
            { type: 'number', min: 1, message: 'Must be greater than zero' },
          ]}
        >
          <InputNumber
            style={{ width: '100%' }}
            min={0}
            step={1000}
            addonBefore="Rs"
            placeholder="100000"
          />
        </Form.Item>

        <Form.Item
          label="Monthly deduction"
          name="monthlyDeduction"
          rules={[
            { required: true, message: 'Enter the installment amount' },
            { type: 'number', min: 1, message: 'Must be greater than zero' },
          ]}
        >
          <InputNumber
            style={{ width: '100%' }}
            min={0}
            step={500}
            addonBefore="Rs"
            placeholder="10000"
          />
        </Form.Item>
      </div>

      {/* Optional recovery legs — bonus and / or AL encashment. Independent
          toggles, same as the legacy screen. */}
      <div
        style={{
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: token.borderRadiusLG,
          padding: '12px 14px 0',
          marginBottom: 16,
        }}
      >
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Form.Item
            label="Deduct against bonus"
            name="bonusEnabled"
            valuePropName="checked"
            tooltip="Recover part of the principal from an Eid bonus instead of monthly salary."
            style={{ marginBottom: 12 }}
          >
            <Switch
              onChange={(on) => {
                if (!on) form.resetFields([...BONUS_FIELDS]);
              }}
            />
          </Form.Item>
          <Form.Item
            label="Other deductions"
            name="otherEnabled"
            valuePropName="checked"
            tooltip="Recover a chunk from annual-leave encashment in a specific month."
            style={{ marginBottom: 12 }}
          >
            <Switch
              onChange={(on) => {
                if (!on) form.resetFields([...OTHER_FIELDS]);
              }}
            />
          </Form.Item>
        </div>

        {bonusEnabled ? (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Form.Item
                label="Bonus #"
                name="bonusPick"
                tooltip="Which of the financial period's bonuses the offset comes out of."
              >
                <Select
                  options={BONUS_PICK_OPTIONS}
                  onChange={(v: BonusPick) => {
                    // Drop the amount for the bonus that's no longer targeted.
                    if (v === '1') form.resetFields(['bonusOffset2']);
                    if (v === '2') form.resetFields(['bonusOffset']);
                  }}
                />
              </Form.Item>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              {wantsBonus1 ? (
                <Form.Item
                  label="Bonus Offset 1"
                  name="bonusOffset"
                  tooltip="Optional — leave blank to recover nothing from bonus # 1."
                  rules={[
                    { type: 'number', min: 1, message: 'Must be greater than zero' },
                  ]}
                >
                  <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
                </Form.Item>
              ) : null}
              {wantsBonus2 ? (
                <Form.Item
                  label="Bonus Offset 2"
                  name="bonusOffset2"
                  tooltip="Optional — leave blank to recover nothing from bonus # 2."
                  rules={[
                    { type: 'number', min: 1, message: 'Must be greater than zero' },
                  ]}
                >
                  <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
                </Form.Item>
              ) : null}
            </div>
          </>
        ) : null}

        {otherEnabled ? (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Form.Item
              label="Annual leave month"
              name="annualLeaveMonth"
              tooltip="Month the annual-leave encashment deduction lands in."
              // Only needed once an amount is entered — the leg is optional.
              dependencies={['annualLeaveOffset']}
              rules={[
                {
                  validator: (_r, v: dayjs.Dayjs | undefined) =>
                    v || !form.getFieldValue('annualLeaveOffset')
                      ? Promise.resolve()
                      : Promise.reject(new Error('Pick the month')),
                },
              ]}
            >
              <DatePicker
                picker="month"
                style={{ width: '100%' }}
                format="MMM YYYY"
              />
            </Form.Item>
            <Form.Item
              label="Annual leave deduction"
              name="annualLeaveOffset"
              tooltip="Optional — amount recovered from annual-leave encashment that month."
              rules={[
                { type: 'number', min: 1, message: 'Must be greater than zero' },
              ]}
            >
              <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
            </Form.Item>
          </div>
        ) : null}
      </div>

      {months != null ? (
        <div style={{ marginBottom: 16 }}>
          {hint(
            offsetTotal > 0
              ? `Rs ${offsetTotal.toLocaleString('en-PK')} comes off up front — the rest clears in ≈ ${months} ${months === 1 ? 'month' : 'months'} at Rs ${monthly.toLocaleString('en-PK')} / month.`
              : `≈ ${months} ${months === 1 ? 'month' : 'months'} to clear at Rs ${monthly.toLocaleString('en-PK')} / month.`,
          )}
        </div>
      ) : null}

      <Form.Item
        label="Needed by"
        name="receivingDate"
        tooltip="When the employee expects to receive the money. Optional."
      >
        <DatePicker
          style={{ width: '100%' }}
          format="DD MMM YYYY"
          disabledDate={(d) => d && d.isBefore(dayjs().startOf('day'))}
        />
      </Form.Item>

      <Form.Item label="Reason" name="description">
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="Medical / education / housing — anything that helps HR understand the request."
          maxLength={500}
          showCount
        />
      </Form.Item>
    </DrawerForm>
  );
}
