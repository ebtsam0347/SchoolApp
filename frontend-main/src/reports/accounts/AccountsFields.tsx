// Filter fields for one Account Report, rendered inside the run drawer.
// Only the params `visibleParams` returns are shown, in PARAM_ORDER.
// Widgets and wording follow the catalog drawer (ParameterField /
// SmartFields / EmployeeFilterPanel): Segmented for short choices, "MMM YYYY"
// months, "DD MMM YYYY" dates, the "From — To" card range and the card tag
// input.
//
// Filters that depend on each other follow the rules in rules.ts: invalid
// choices are disabled, a change that invalidates another filter clears or
// adjusts it, and every rule is also a form rule so a stale value can't be
// submitted.
//
// Lookups that depend on `payroll.read` (fiscal years, banks, bonus
// numbers) fail soft: the field shows an inline note instead of breaking
// the drawer. Fiscal years fall back to the self-serve current year.

import { useEffect, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { DatePicker, Form, Input, Segmented, Select, Space, Typography } from 'antd';
import type { Rule } from 'antd/es/form';
import type { Dayjs } from 'dayjs';

import { forbiddenOr } from '@/lib';
import { banksApi, bonusRunsApi, fiscalYearsApi } from '@/payroll';
import { catalogsApi } from '@/people';

import { LookupSelect } from '../catalog/LookupSelect';
import { SHEET_LAYOUT_HINT, SLIP_LAYOUT_OPTIONS } from '../slip-layout';
import {
  dateRangeMode,
  hasParam,
  hasSlipLayout,
  isDocument,
  isSingleCard,
  visibleParams,
  type FilterFormValues,
} from './filters';
import { PRINT_HINTS, reportKey } from './notes';
import {
  blocksFuture,
  clampMonth,
  dayDisabled,
  departmentFitsType,
  monthDisabled,
  toFiscalYearRange,
  type FiscalYearRange,
} from './rules';
import type { AccountsReportDefinition, AccountsReportParamDef, EmployeeType, PaymentMode } from './types';

const STALE = 5 * 60 * 1000;

const PAYMENT_MODES = [
  { label: 'All', value: 'ALL' },
  { label: 'Cash', value: 'CASH' },
  { label: 'Bank transfer', value: 'BANK_TRANSFER' },
];
const EMPLOYEE_TYPES = [
  { label: 'All', value: 'ALL' },
  { label: 'Production', value: 'PRODUCTION' },
  { label: 'Non-production', value: 'NON_PRODUCTION' },
];
const EMP_STATUSES = [
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Left', value: 'NON_ACTIVE' },
  { label: 'All', value: 'ALL' },
];
const GRADE_TYPES = [
  { label: 'All', value: '' },
  { label: 'Management', value: 'MANAGEMENT' },
  { label: 'Supervisor', value: 'SUPERVISOR' },
  { label: 'Labour', value: 'LABOUR' },
];
const BALANCES = [
  { label: 'Balance > 0', value: 'GREATER_THAN_ZERO' },
  { label: 'Balance = 0', value: 'EQUAL_ZERO' },
];
const LANGUAGES = [
  { label: 'English', value: 'en' },
  { label: 'اردو', value: 'ur' },
];

const requiredRule = (required: boolean, label: string): Rule[] =>
  required ? [{ required: true, message: `${label} is required` }] : [];

/** Soft-failure note for a lookup the caller may not be allowed to read. */
const lookupNote = (err: unknown, what: string) => (
  <Typography.Text type="warning" style={{ fontSize: 12 }}>
    {forbiddenOr(err, `You don't have access to the ${what} list.`, `Couldn't load ${what}.`)}
  </Typography.Text>
);

const hasCards = (v: unknown) => Array.isArray(v) && v.length > 0;
const hasText = (v: unknown) => typeof v === 'string' && v.trim() !== '';

export function AccountsFields({ def }: { def: AccountsReportDefinition }) {
  const document = isDocument(def);
  const format = Form.useWatch('format') as FilterFormValues['format'];
  const layout = Form.useWatch('layout') as FilterFormValues['layout'];

  return (
    <>
      <Form.Item
        name="format"
        label="Format"
        extra={format === 'pdf' ? PRINT_HINTS[reportKey(def.id)] : undefined}
      >
        <Segmented
          options={[
            ...(document ? [{ label: 'PDF', value: 'pdf' }] : []),
            { label: 'Excel', value: 'xlsx' },
            { label: 'CSV', value: 'csv' },
          ]}
        />
      </Form.Item>
      {visibleParams(def).map((p) =>
        // The slip language only applies to the printed PDF.
        p.param === 'SLIP_LANGUAGE' && format !== 'pdf' ? null : (
          <ParamField key={p.param} def={def} p={p} />
        ),
      )}
      {/* Page layout only applies to A1's printed slips. */}
      {hasSlipLayout(def) && format === 'pdf' ? (
        <Form.Item
          name="layout"
          label="Layout"
          extra={layout === 'sheet' ? SHEET_LAYOUT_HINT : undefined}
        >
          <Segmented options={SLIP_LAYOUT_OPTIONS} />
        </Form.Item>
      ) : null}
    </>
  );
}

function ParamField({ def, p }: { def: AccountsReportDefinition; p: AccountsReportParamDef }) {
  const { required } = p;

  switch (p.param) {
    case 'FISCAL_YEAR':
      return <FiscalYearField required={required} />;
    case 'MONTH':
      return <MonthField def={def} required={required} />;
    case 'DATE_RANGE':
      return <DateRangeField def={def} required={required} />;
    case 'AS_ON_DATE':
      return (
        <AsOnField def={def} name="asOnDate" label="As on date" required={required} />
      );
    case 'BONUS_NO':
      return <BonusNoField required={required} />;
    case 'EMP_STATUS':
      return (
        <Form.Item name="empStatus" label="Employees" rules={requiredRule(required, 'Employees')}>
          <Segmented options={EMP_STATUSES} />
        </Form.Item>
      );
    case 'PAYMENT_MODE':
      return <PaymentModeField def={def} required={required} />;
    case 'EMPLOYEE_TYPE':
      return (
        <Form.Item name="employeeType" label="Employee type" rules={requiredRule(required, 'Employee type')}>
          <Segmented options={EMPLOYEE_TYPES} />
        </Form.Item>
      );
    case 'GRADE_TYPE':
      return (
        <Form.Item name="gradeType" label="Grade type" rules={requiredRule(required, 'Grade type')}>
          <Segmented options={GRADE_TYPES} />
        </Form.Item>
      );
    case 'BANK':
      return <BankField def={def} required={required} />;
    case 'BALANCE':
      return (
        <Form.Item name="balance" label="Balance" rules={requiredRule(required, 'Balance')}>
          <Segmented options={BALANCES} />
        </Form.Item>
      );
    case 'CARD_RANGE':
      return <CardRangeField def={def} required={required} />;
    case 'CARD_LIST':
      return isSingleCard(def) ? (
        <Form.Item
          name="cardList"
          label="Card no"
          rules={[
            ...requiredRule(required, 'Card no'),
            {
              validator: (_, v: string[] | undefined) =>
                v?.[0] && !/^\d+$/.test(v[0])
                  ? Promise.reject(new Error('Card numbers are digits only'))
                  : Promise.resolve(),
            },
          ]}
          getValueFromEvent={(e: React.ChangeEvent<HTMLInputElement>) => {
            const card = e.target.value.trim();
            return card ? [card] : [];
          }}
          getValueProps={(v: string[] | undefined) => ({ value: v?.[0] ?? '' })}
        >
          <Input allowClear placeholder="Enter a card no" />
        </Form.Item>
      ) : (
        <CardListField def={def} required={required} />
      );
    case 'DEPARTMENT':
      return <DepartmentField def={def} required={required} />;
    case 'SECTION':
      return (
        <Form.Item name="sectionId" label="Section" rules={requiredRule(required, 'Section')}>
          <LookupSelect kind="section" />
        </Form.Item>
      );
    case 'DESIGNATION':
      return (
        <Form.Item name="designationId" label="Designation" rules={requiredRule(required, 'Designation')}>
          <LookupSelect kind="designation" />
        </Form.Item>
      );
    case 'SLIP_LANGUAGE':
      return (
        <Form.Item name="language" label="Slip language">
          <Segmented options={LANGUAGES} />
        </Form.Item>
      );
    case 'SHIFT':
      return null;
  }
}

/* --------------------------- Fiscal year ------------------------------ */

function useFiscalYears() {
  const list = useQuery({
    queryKey: ['payroll', 'fiscal-years'],
    queryFn: ({ signal }) => fiscalYearsApi.list(signal),
    staleTime: STALE,
    retry: false,
  });
  // `/fiscal-years/current` is self-serve — keeps the report usable for
  // callers who can't read the full list.
  const current = useQuery({
    queryKey: ['payroll', 'fiscal-years', 'current'],
    queryFn: ({ signal }) => fiscalYearsApi.current(signal),
    staleTime: STALE,
    enabled: list.isError,
    retry: false,
  });
  const years = useMemo(
    () => list.data ?? (current.data ? [current.data] : []),
    [list.data, current.data],
  );
  return { years, list, current };
}

/** The chosen fiscal year's date range, when the report has one and it's set. */
function useChosenFiscalYear(def: AccountsReportDefinition): FiscalYearRange | null {
  const code = Form.useWatch('fiscalYearCode') as string | undefined;
  const { years } = useFiscalYears();
  const withYear = hasParam(def, 'FISCAL_YEAR');
  return useMemo(() => {
    if (!withYear || !code) return null;
    const fy = years.find((y) => y.code === code);
    return fy ? toFiscalYearRange(fy) : null;
  }, [withYear, code, years]);
}

function FiscalYearField({ required }: { required: boolean }) {
  const form = Form.useFormInstance<FilterFormValues>();
  const { years, list, current } = useFiscalYears();
  const currentCode = years.find((y) => y.status === 'CURRENT')?.code;

  // Default to the CURRENT year once the lookup lands — only where it's
  // required; an optional year left blank means "any year".
  useEffect(() => {
    if (required && currentCode && !form.getFieldValue('fiscalYearCode')) {
      form.setFieldValue('fiscalYearCode', currentCode);
    }
  }, [required, currentCode, form]);

  return (
    <Form.Item
      name="fiscalYearCode"
      label="Fiscal year"
      rules={requiredRule(required, 'Fiscal year')}
      extra={
        !list.isError ? undefined : current.data ? (
          <Typography.Text type="warning" style={{ fontSize: 12 }}>
            Full list unavailable — showing the current year only.
          </Typography.Text>
        ) : (
          lookupNote(list.error, 'fiscal year')
        )
      }
    >
      <Select
        showSearch
        allowClear={!required}
        loading={list.isLoading || current.isLoading}
        placeholder={required ? 'Select fiscal year' : 'All'}
        optionFilterProp="label"
        options={years.map((y) => ({
          value: y.code,
          label: y.status === 'CURRENT' ? `${y.code} (current)` : y.code,
        }))}
      />
    </Form.Item>
  );
}

/* ------------------------------- Periods ------------------------------ */

function MonthField({ def, required }: { def: AccountsReportDefinition; required: boolean }) {
  const form = Form.useFormInstance<FilterFormValues>();
  const fy = useChosenFiscalYear(def);
  const noFuture = blocksFuture(def);

  // A new fiscal year pulls the month inside it.
  useEffect(() => {
    if (!fy) return;
    const cur = form.getFieldValue('month') as Dayjs | null | undefined;
    const next = clampMonth(cur, fy, noFuture);
    if (!cur || !next.isSame(cur, 'month')) {
      form.setFieldValue('month', next);
      void form.validateFields(['month']).catch(() => undefined);
    }
  }, [fy, noFuture, form]);

  return (
    <Form.Item
      name="month"
      label="Month"
      dependencies={['fiscalYearCode']}
      extra={fy ? `Fiscal year ${fy.code}: ${fy.start.format('MMM YYYY')} – ${fy.end.format('MMM YYYY')}` : undefined}
      rules={[
        ...requiredRule(required, 'Month'),
        {
          validator: (_, v: Dayjs | null | undefined) => {
            if (!v) return Promise.resolve();
            if (fy && monthDisabled(v, fy, false)) {
              return Promise.reject(new Error(`Month must be within fiscal year ${fy.code}`));
            }
            if (monthDisabled(v, null, noFuture)) {
              return Promise.reject(new Error("Month can't be in the future"));
            }
            return Promise.resolve();
          },
        },
      ]}
    >
      <DatePicker
        picker="month"
        format="MMM YYYY"
        style={{ width: '100%' }}
        disabledDate={(d) => monthDisabled(d, fy, noFuture)}
      />
    </Form.Item>
  );
}

function AsOnField({
  def,
  name,
  label,
  required,
}: {
  def: AccountsReportDefinition;
  name: 'asOnDate' | 'dateTo';
  label: string;
  required: boolean;
}) {
  const noFuture = blocksFuture(def);
  return (
    <Form.Item
      name={name}
      label={label}
      rules={[
        ...requiredRule(required, label),
        {
          validator: (_, v: Dayjs | null | undefined) =>
            v && dayDisabled(v, noFuture)
              ? Promise.reject(new Error(`${label} can't be in the future`))
              : Promise.resolve(),
        },
      ]}
    >
      <DatePicker
        format="DD MMM YYYY"
        style={{ width: '100%' }}
        disabledDate={(d) => dayDisabled(d, noFuture)}
      />
    </Form.Item>
  );
}

function DateRangeField({ def, required }: { def: AccountsReportDefinition; required: boolean }) {
  const mode = dateRangeMode(def);
  if (mode === 'AS_ON') {
    return <AsOnField def={def} name="dateTo" label="Balance as on" required={required} />;
  }
  const months = mode === 'MONTHS';
  const noFuture = blocksFuture(def);
  const label = months ? 'Advance months' : 'Date range';
  return (
    <Form.Item
      name="dateRange"
      label={label}
      rules={[
        ...requiredRule(required, label),
        {
          validator: (_, v: [Dayjs, Dayjs] | null | undefined) =>
            v && v[0] && v[1] && v[1].isBefore(v[0], months ? 'month' : 'day')
              ? Promise.reject(new Error('The end must be on or after the start'))
              : Promise.resolve(),
        },
      ]}
    >
      <DatePicker.RangePicker
        picker={months ? 'month' : 'date'}
        format={months ? 'MMM YYYY' : 'DD MMM YYYY'}
        style={{ width: '100%' }}
        disabledDate={(d) =>
          months ? monthDisabled(d, null, noFuture) : dayDisabled(d, noFuture)
        }
      />
    </Form.Item>
  );
}

/* ------------------------------- Cards -------------------------------- */

const cardRule: Rule = {
  validator: (_, v: string | undefined) =>
    !hasText(v)
      ? Promise.resolve()
      : !/^\d+$/.test(v!.trim())
        ? Promise.reject(new Error('Card numbers are digits only'))
        : Number(v) <= 0
          ? Promise.reject(new Error('Card numbers start at 1'))
          : Promise.resolve(),
};

/** Card range and specific cards are either / or (see rules.ts). */
function CardRangeField({ def, required }: { def: AccountsReportDefinition; required: boolean }) {
  const cardList = Form.useWatch('cardList') as string[] | undefined;
  const locked = hasParam(def, 'CARD_LIST') && hasCards(cardList);

  return (
    <Form.Item
      label="Card range"
      required={required}
      extra={locked ? 'Clear the card numbers below to use a range.' : undefined}
    >
      <Space.Compact style={{ width: '100%' }}>
        <Form.Item name="cardFrom" noStyle rules={[...requiredRule(required, 'Card from'), cardRule]}>
          <Input allowClear disabled={locked} placeholder="From" />
        </Form.Item>
        <Input
          disabled
          value="—"
          style={{ width: 40, textAlign: 'center', pointerEvents: 'none' }}
        />
        <Form.Item
          name="cardTo"
          noStyle
          dependencies={['cardFrom']}
          rules={[
            ...requiredRule(required, 'Card to'),
            cardRule,
            ({ getFieldValue }) => ({
              validator: (_, to: string | undefined) => {
                const from = getFieldValue('cardFrom') as string | undefined;
                return hasText(to) && hasText(from) && Number(to) < Number(from)
                  ? Promise.reject(new Error('Card to must be ≥ Card from'))
                  : Promise.resolve();
              },
            }),
          ]}
        >
          <Input allowClear disabled={locked} placeholder="To" />
        </Form.Item>
      </Space.Compact>
    </Form.Item>
  );
}

function CardListField({ def, required }: { def: AccountsReportDefinition; required: boolean }) {
  const cardFrom = Form.useWatch('cardFrom') as string | undefined;
  const cardTo = Form.useWatch('cardTo') as string | undefined;
  const locked = hasParam(def, 'CARD_RANGE') && (hasText(cardFrom) || hasText(cardTo));

  return (
    <Form.Item
      name="cardList"
      label="Card numbers"
      extra={locked ? 'Clear the card range above to pick specific cards.' : undefined}
      rules={[
        ...requiredRule(required, 'Card numbers'),
        {
          validator: (_, v: string[] | undefined) =>
            (v ?? []).some((c) => !/^\d+$/.test(String(c).trim()))
              ? Promise.reject(new Error('Card numbers are digits only'))
              : Promise.resolve(),
        },
      ]}
    >
      <Select
        mode="tags"
        open={false}
        suffixIcon={null}
        disabled={locked}
        tokenSeparators={[',', ' ']}
        placeholder="Type a card no and press Enter"
      />
    </Form.Item>
  );
}

/* ---------------------- Payment mode ⟂ bank --------------------------- */

function PaymentModeField({ def, required }: { def: AccountsReportDefinition; required: boolean }) {
  const bankId = Form.useWatch('bankId') as number | undefined;
  // A bank only matches bank-transfer employees, so Cash can't go with one.
  const bankChosen = hasParam(def, 'BANK') && bankId != null;
  return (
    <Form.Item
      name="paymentMode"
      label="Payment mode"
      rules={requiredRule(required, 'Payment mode')}
      extra={bankChosen ? 'Cash is unavailable while a bank is selected.' : undefined}
    >
      <Segmented
        options={PAYMENT_MODES.map((o) => ({ ...o, disabled: bankChosen && o.value === 'CASH' }))}
      />
    </Form.Item>
  );
}

function BankField({ def, required }: { def: AccountsReportDefinition; required: boolean }) {
  const form = Form.useFormInstance<FilterFormValues>();
  const paymentMode = Form.useWatch('paymentMode', form) as PaymentMode | undefined;
  const cash = hasParam(def, 'PAYMENT_MODE') && paymentMode === 'CASH';
  const q = useQuery({
    queryKey: ['banks', 'catalog'],
    queryFn: ({ signal }) => banksApi.list({}, signal),
    staleTime: STALE,
    retry: false,
  });

  // Switching to Cash drops a bank chosen earlier.
  useEffect(() => {
    if (cash && form.getFieldValue('bankId') != null) form.setFieldValue('bankId', undefined);
  }, [cash, form]);

  return (
    <Form.Item
      name="bankId"
      label="Bank"
      rules={requiredRule(required, 'Bank')}
      extra={
        q.isError
          ? lookupNote(q.error, 'bank')
          : cash
            ? 'Cash payments have no bank.'
            : undefined
      }
    >
      <Select
        showSearch
        allowClear={!required}
        disabled={cash}
        loading={q.isLoading}
        placeholder={required ? 'Select bank' : 'All'}
        optionFilterProp="label"
        options={(q.data ?? []).map((b) => ({ value: b.id, label: b.name }))}
      />
    </Form.Item>
  );
}

/* --------------------- Employee type → department --------------------- */

function DepartmentField({ def, required }: { def: AccountsReportDefinition; required: boolean }) {
  const form = Form.useFormInstance<FilterFormValues>();
  const typed = hasParam(def, 'EMPLOYEE_TYPE');
  const employeeType = Form.useWatch('employeeType', form) as EmployeeType | undefined;
  const type = typed ? employeeType : undefined;
  const q = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: STALE,
  });

  const fits = useMemo(
    () =>
      (q.data ?? []).filter(
        (d) => d.id != null && departmentFitsType(d.isProduction ?? undefined, type),
      ),
    [q.data, type],
  );

  // A department that no longer fits the employee type is dropped, with
  // the section chosen under it.
  useEffect(() => {
    const cur = form.getFieldValue('departmentId') as number | undefined;
    if (cur == null || !q.data) return;
    if (!fits.some((d) => d.id === cur)) {
      form.setFieldsValue({ departmentId: undefined, sectionId: undefined });
    }
  }, [fits, q.data, form]);

  return (
    <Form.Item
      name="departmentId"
      label="Department"
      rules={requiredRule(required, 'Department')}
      extra={
        type && type !== 'ALL'
          ? `Showing ${type === 'PRODUCTION' ? 'production' : 'non-production'} departments.`
          : undefined
      }
    >
      <Select<number>
        showSearch
        allowClear
        optionFilterProp="label"
        loading={q.isLoading}
        placeholder="All"
        options={fits.map((d) => ({ value: d.id!, label: d.name ?? String(d.id) }))}
        onChange={() => {
          // A section picked under the old department no longer applies.
          if (form.getFieldValue('sectionId') != null) form.setFieldValue('sectionId', undefined);
        }}
      />
    </Form.Item>
  );
}

/* ------------------------------ Bonus --------------------------------- */

function BonusNoField({ required }: { required: boolean }) {
  const form = Form.useFormInstance<FilterFormValues>();
  const fiscalYear = Form.useWatch('fiscalYearCode', form);
  const q = useQuery({
    queryKey: ['payroll', 'bonuses', 'no-settings', fiscalYear],
    queryFn: ({ signal }) => bonusRunsApi.list({ fiscalYear }, signal),
    enabled: !!fiscalYear,
    staleTime: STALE,
    retry: false,
  });
  const numbers = useMemo(
    () => [...new Set((q.data ?? []).map((b) => b.bonusNo))].sort((a, b) => a - b),
    [q.data],
  );
  const highest = numbers.at(-1);
  const loadedFor = q.data ? fiscalYear : undefined;

  // Reload per fiscal year: a bonus no that doesn't exist in the newly
  // chosen year is replaced by that year's highest (required) or cleared.
  // No fiscal year → no bonus.
  useEffect(() => {
    const cur = form.getFieldValue('bonusNo') as number | undefined;
    if (!fiscalYear) {
      if (cur != null) form.setFieldValue('bonusNo', undefined);
      return;
    }
    if (!loadedFor) return;
    if (cur != null && numbers.includes(cur)) return;
    const next = required ? highest : undefined;
    if (next !== cur) form.setFieldValue('bonusNo', next);
  }, [fiscalYear, loadedFor, numbers, required, highest, form]);

  return (
    <Form.Item
      name="bonusNo"
      label="Bonus"
      rules={requiredRule(required, 'Bonus')}
      extra={
        q.isError
          ? lookupNote(q.error, 'bonus')
          : fiscalYear && q.data && numbers.length === 0
            ? 'No bonus has been set up for this fiscal year.'
            : !fiscalYear
              ? 'Pick a fiscal year first.'
              : undefined
      }
    >
      <Select
        allowClear={!required}
        disabled={!fiscalYear}
        loading={q.isFetching}
        placeholder={required ? 'Select bonus' : 'All'}
        options={numbers.map((n) => ({ value: n, label: `Bonus ${n}` }))}
      />
    </Form.Item>
  );
}
