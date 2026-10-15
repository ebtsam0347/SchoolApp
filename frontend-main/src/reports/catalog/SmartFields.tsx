// Smarter parameter widgets that replace the generic int/date inputs in
// `ParameterField` for well-known catalog patterns. Trade-off: a tiny
// amount of name-matching here in exchange for a dramatically nicer
// drawer UX (no more typing "2026" + "5" into two boxes; no more typing
// an employee id from memory).
//
// Patterns recognised — each consumes the listed catalog parameter
// names and renders one Form.Item with a synthetic name (prefixed `__`)
// to avoid colliding with any future raw parameter the backend ships.
// The synthetic values are decoded back into catalog-param keys by
// `decodeSmartValues` inside the drawer's submit handler.
//
//   Period picker         consumes  year + month
//   From-period picker    consumes  fromYear + fromMonth
//   To-period picker      consumes  toYear + toMonth
//   Year picker           consumes  year (when not paired with month)
//   Employee picker       consumes  employeeId (any context)
//                                   id (path) on …/employees/{id}/…
//   Employee filter panel consumes  status, departmentId, sectionId,
//                                   designationId, cardFrom, cardTo, cards
//   Date range            consumes  from + to (both `date`)
//   Month picker          consumes  month (string `yyyy-MM`)
//   Lookup select         consumes  departmentId / sectionId /
//                                   designationId / gradeId (`int`)
//   Checkbox              consumes  any enum whose values are true/false
//
// Anything else falls through to the default <ParameterField>.

import { Checkbox, DatePicker, Form } from 'antd';
import dayjs from 'dayjs';
import type { ReactElement } from 'react';

import { EmployeePicker } from '@/people';

import type { ReportCatalogEntry, ReportParameter } from '../types';
import {
  EMPLOYEE_FILTER_PARAMS,
  LOOKUP_PARAMS,
  encodeEmployeeFilter,
  hasEmployeeFilter,
} from './employee-filter';
import { EmployeeFilterPanel } from './EmployeeFilterPanel';
import { humanizeParameterName } from './labels';
import { LookupSelect } from './LookupSelect';
import { PARAM_PRESETS, REPORT_PRESETS } from './presets';

/** Synthetic form keys — never overlap with backend parameter names. */
const FK = {
  period: '__period',
  fromPeriod: '__fromPeriod',
  toPeriod: '__toPeriod',
  year: '__year',
  range: '__range',
  month: '__month',
  employeeFilter: '__employeeFilter',
} as const;

const FILTER_PARAM_NAMES = new Set<string>(EMPLOYEE_FILTER_PARAMS);

/** `month` as a `yyyy-MM` string (the int form pairs with `year`). */
const isMonthString = (p: ReportParameter) => p.name === 'month' && p.type !== 'int';

/** An enum whose values are exactly `true` / `false` — rendered as a checkbox. */
export function isBooleanEnum(p: ReportParameter): boolean {
  const v = p.values ?? [];
  return (
    p.type === 'enum' &&
    v.length === 2 &&
    v.every((x) => x.toLowerCase() === 'true' || x.toLowerCase() === 'false')
  );
}

interface RenderableField {
  key: string;
  /** Catalog parameter names this field provides values for. */
  consumes: string[];
  node: ReactElement;
}

/**
 * Walk the entry's parameters in declaration order and emit one
 * RenderableField per visible row. Pairs (year+month etc.) are merged
 * into a single composite at the position of the first member.
 */
export function renderEntryFields(
  entry: ReportCatalogEntry,
  renderDefault: (p: ReportParameter) => ReactElement,
): RenderableField[] {
  const params = entry.parameters;
  const has = (name: string) =>
    params.some((p) => p.name === name && p.type === 'int');

  const hasPeriod = has('year') && has('month');
  const hasFromPeriod = has('fromYear') && has('fromMonth');
  const hasToPeriod = has('toYear') && has('toMonth');
  const hasDate = (name: string) =>
    params.some((p) => p.name === name && p.type === 'date');
  const hasRange = hasDate('from') && hasDate('to');
  const hasPanel = hasEmployeeFilter(entry);

  const out: RenderableField[] = [];
  const consumed = new Set<string>();

  for (const p of params) {
    if (consumed.has(p.name)) continue;

    if (hasPeriod && (p.name === 'year' || p.name === 'month')) {
      const required = paramsRequired(params, ['year', 'month']);
      out.push({
        key: FK.period,
        consumes: ['year', 'month'],
        node: (
          <Form.Item
            key={FK.period}
            name={FK.period}
            label="Period"
            rules={requiredRule(required, 'Period')}
            extra="Year and month for the report."
          >
            <DatePicker
              picker="month"
              format="MMM YYYY"
              style={{ width: '100%' }}
            />
          </Form.Item>
        ),
      });
      consumed.add('year');
      consumed.add('month');
      continue;
    }

    if (
      hasFromPeriod &&
      (p.name === 'fromYear' || p.name === 'fromMonth')
    ) {
      const required = paramsRequired(params, ['fromYear', 'fromMonth']);
      out.push({
        key: FK.fromPeriod,
        consumes: ['fromYear', 'fromMonth'],
        node: (
          <Form.Item
            key={FK.fromPeriod}
            name={FK.fromPeriod}
            label="From period"
            rules={requiredRule(required, 'From period')}
          >
            <DatePicker
              picker="month"
              format="MMM YYYY"
              style={{ width: '100%' }}
            />
          </Form.Item>
        ),
      });
      consumed.add('fromYear');
      consumed.add('fromMonth');
      continue;
    }

    if (hasToPeriod && (p.name === 'toYear' || p.name === 'toMonth')) {
      const required = paramsRequired(params, ['toYear', 'toMonth']);
      out.push({
        key: FK.toPeriod,
        consumes: ['toYear', 'toMonth'],
        node: (
          <Form.Item
            key={FK.toPeriod}
            name={FK.toPeriod}
            label="To period"
            rules={requiredRule(required, 'To period')}
          >
            <DatePicker
              picker="month"
              format="MMM YYYY"
              style={{ width: '100%' }}
            />
          </Form.Item>
        ),
      });
      consumed.add('toYear');
      consumed.add('toMonth');
      continue;
    }

    // Lone year — used by leave-balance / leave-carry-forward.
    if (p.name === 'year' && p.type === 'int' && !hasPeriod) {
      out.push({
        key: FK.year,
        consumes: ['year'],
        node: (
          <Form.Item
            key={FK.year}
            name={FK.year}
            label="Year"
            rules={requiredRule(p.required, 'Year')}
            extra={p.help ?? undefined}
          >
            <DatePicker
              picker="year"
              format="YYYY"
              style={{ width: '100%' }}
            />
          </Form.Item>
        ),
      });
      consumed.add('year');
      continue;
    }

    if (hasPanel && FILTER_PARAM_NAMES.has(p.name)) {
      const members = params
        .map((q) => q.name)
        .filter((n) => FILTER_PARAM_NAMES.has(n));
      out.push({
        key: FK.employeeFilter,
        consumes: members,
        node: <EmployeeFilterPanel key={FK.employeeFilter} entry={entry} />,
      });
      for (const n of members) consumed.add(n);
      continue;
    }

    if (hasRange && (p.name === 'from' || p.name === 'to')) {
      const required = params.some(
        (q) => (q.name === 'from' || q.name === 'to') && q.required,
      );
      out.push({
        key: FK.range,
        consumes: ['from', 'to'],
        node: (
          <Form.Item
            key={FK.range}
            name={FK.range}
            label="Date range"
            rules={requiredRule(required, 'Date range')}
            initialValue={REPORT_PRESETS[entry.id]?.defaultRange?.()}
          >
            <DatePicker.RangePicker
              format="DD MMM YYYY"
              allowEmpty={required ? undefined : [true, true]}
              style={{ width: '100%' }}
            />
          </Form.Item>
        ),
      });
      consumed.add('from');
      consumed.add('to');
      continue;
    }

    if (isMonthString(p)) {
      out.push({
        key: FK.month,
        consumes: ['month'],
        node: (
          <Form.Item
            key={FK.month}
            name={FK.month}
            label="Month"
            rules={requiredRule(p.required, 'Month')}
            extra={p.help ?? undefined}
          >
            <DatePicker picker="month" format="MMM YYYY" style={{ width: '100%' }} />
          </Form.Item>
        ),
      });
      consumed.add('month');
      continue;
    }

    if (p.type === 'int' && LOOKUP_PARAMS[p.name]) {
      const label = humanizeParameterName(p.name.replace(/Id$/, ''));
      out.push({
        key: p.name,
        consumes: [p.name],
        node: (
          <Form.Item
            key={p.name}
            name={p.name}
            label={label}
            rules={requiredRule(p.required, label)}
            extra={p.help ?? undefined}
          >
            <LookupSelect
              kind={LOOKUP_PARAMS[p.name]!}
              placeholder={p.required ? `Select ${label.toLowerCase()}` : 'All'}
            />
          </Form.Item>
        ),
      });
      consumed.add(p.name);
      continue;
    }

    if (isBooleanEnum(p)) {
      const preset = PARAM_PRESETS[p.name];
      out.push({
        key: p.name,
        consumes: [p.name],
        node: (
          <Form.Item
            key={p.name}
            name={p.name}
            label={preset?.label ?? humanizeParameterName(p.name)}
            valuePropName="checked"
            initialValue={preset?.initial ?? false}
          >
            <Checkbox>{preset?.checkboxLabel ?? p.help ?? 'Yes'}</Checkbox>
          </Form.Item>
        ),
      });
      consumed.add(p.name);
      continue;
    }

    if (
      p.name === 'employeeId' ||
      (p.name === 'id' && p.location === 'path' && entry.path.includes('/employees/{id}'))
    ) {
      out.push({
        key: p.name,
        consumes: [p.name],
        node: (
          <Form.Item
            key={p.name}
            name={p.name}
            label="Employee"
            rules={requiredRule(p.required, 'Employee')}
            extra={p.help ?? undefined}
          >
            <EmployeePicker placeholder="Search by name or code" />
          </Form.Item>
        ),
      });
      consumed.add(p.name);
      continue;
    }

    consumed.add(p.name);
    out.push({
      key: p.name,
      consumes: [p.name],
      node: renderDefault(p),
    });
  }

  return out;
}

function paramsRequired(params: ReportParameter[], names: string[]): boolean {
  return names.every((n) => params.find((p) => p.name === n)?.required);
}

function requiredRule(required: boolean, label: string) {
  return required ? [{ required: true, message: `${label} is required` }] : undefined;
}

/**
 * Translate raw form values (which may include synthetic `__period`
 * etc. fields) into the flat `{ catalogParamName: value }` map that
 * `buildReportRequest` consumes. Date pickers emit Dayjs; we extract
 * year and month here so the BFF receives ints.
 */
export function decodeSmartValues(
  entry: ReportCatalogEntry,
  raw: Record<string, unknown>,
): Record<string, unknown> {
  const out: Record<string, unknown> = { ...raw };

  // Period composites — split each into the two int parameters.
  const splitPeriod = (formKey: string, yKey: string, mKey: string) => {
    const v = raw[formKey];
    if (dayjs.isDayjs(v)) {
      out[yKey] = v.year();
      out[mKey] = v.month() + 1;
    }
    delete out[formKey];
  };

  splitPeriod(FK.period, 'year', 'month');
  splitPeriod(FK.fromPeriod, 'fromYear', 'fromMonth');
  splitPeriod(FK.toPeriod, 'toYear', 'toMonth');

  const yearOnly = raw[FK.year];
  if (dayjs.isDayjs(yearOnly)) {
    out.year = yearOnly.year();
  }
  delete out[FK.year];

  const range = raw[FK.range];
  if (Array.isArray(range)) {
    const [from, to] = range as unknown[];
    if (dayjs.isDayjs(from)) out.from = from.format('YYYY-MM-DD');
    if (dayjs.isDayjs(to)) out.to = to.format('YYYY-MM-DD');
  }

  const month = raw[FK.month];
  if (dayjs.isDayjs(month)) out.month = month.format('YYYY-MM');

  // Checkbox enums: the catalog wants the literal "true" / "false".
  for (const p of entry.parameters) {
    if (isBooleanEnum(p) && typeof out[p.name] === 'boolean') {
      out[p.name] = String(out[p.name]);
    }
  }

  if (hasEmployeeFilter(entry)) encodeEmployeeFilter(out);

  // Drop any synthetic keys that didn't get explicitly translated above
  // (defensive — keeps downstream `Object.entries` from seeing __ keys).
  for (const k of Object.keys(out)) {
    if (k.startsWith('__')) delete out[k];
  }

  // Strip catalog params not declared in the entry — guards against an
  // older form value lingering when the user picks a different report.
  const declared = new Set(entry.parameters.map((p) => p.name));
  for (const k of Object.keys(out)) {
    if (!declared.has(k)) delete out[k];
  }

  return out;
}
