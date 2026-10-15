// Factory functions that build common AntD <Table> column definitions.
//
// Why factories instead of components: AntD's `columns` array is data, not
// JSX. A column object goes through Table's internal pipeline (sorters,
// fixed columns, virtualization). Wrapping it in a React component would
// break that. Factories return the same plain objects you'd hand-write.
//
// Each factory accepts a `dataIndex` (or row→value selector) plus the
// shared options. They cover the patterns we found duplicated 4+ times:
// status pills, money, date ranges, and person cells.
//
// Usage:
//   const cols: TableProps<Row>['columns'] = [
//     personColumn<Row>({ title: 'Employee', dataIndex: 'employee' }),
//     statusColumn<Row>({
//       title: 'Status',
//       dataIndex: 'status',
//       toneMap: STATUS_TONE,
//       labelMap: STATUS_LABEL,
//     }),
//     moneyColumn<Row>({ title: 'Amount', dataIndex: 'amount' }),
//     dateRangeColumn<Row>({ title: 'Range', from: 'fromDate', to: 'toDate' }),
//   ];

import type { TableColumnType } from 'antd';
import type { ReactNode } from 'react';
import { MoneyCell } from './MoneyCell';
import { PersonCell, type PersonCellPerson } from './PersonCell';
import { StatusPill, type StatusPillTone } from './StatusPill';

type DataIndex<Row> = keyof Row & (string | number);
type ValueGetter<Row, Value> = DataIndex<Row> | ((row: Row) => Value);

function get<Row, Value>(row: Row, getter: ValueGetter<Row, Value>): Value {
  return typeof getter === 'function'
    ? (getter as (r: Row) => Value)(row)
    : (row[getter as keyof Row] as unknown as Value);
}

/* --------------------------- Status column --------------------------- */

export type StatusColumnOptions<Row, Status extends string = string> = {
  title?: ReactNode;
  key?: string;
  width?: number;
  dataIndex: ValueGetter<Row, Status>;
  /** Maps the raw status value to a StatusPill tone. */
  toneMap: Record<Status, StatusPillTone>;
  /** Maps the raw status value to a human-readable label. */
  labelMap?: Record<Status, ReactNode>;
  /** Pill size override. */
  size?: 'sm' | 'md';
  /** Render solid pills instead of soft. */
  variant?: 'soft' | 'solid';
};

export function statusColumn<Row, Status extends string = string>(
  opts: StatusColumnOptions<Row, Status>,
): TableColumnType<Row> {
  return {
    title: opts.title ?? 'Status',
    key:
      opts.key ??
      (typeof opts.dataIndex === 'function' ? 'status' : String(opts.dataIndex)),
    width: opts.width,
    render: (_, row) => {
      const status = get(row, opts.dataIndex);
      const label = opts.labelMap?.[status] ?? String(status);
      const tone = opts.toneMap[status] ?? 'neutral';
      return (
        <StatusPill tone={tone} size={opts.size} variant={opts.variant}>
          {label}
        </StatusPill>
      );
    },
  };
}

/* ---------------------------- Money column --------------------------- */

export type MoneyColumnOptions<Row> = {
  title?: ReactNode;
  key?: string;
  width?: number;
  dataIndex: ValueGetter<Row, number | null | undefined>;
  /** Use compact notation (Rs 14.2M). */
  compact?: boolean;
  /** Show + on positive amounts. */
  showSign?: boolean;
  /** Color positive green / negative red. */
  coloredSign?: boolean;
  /** Replacement for null/undefined values. Defaults to '—'. */
  emptyValue?: ReactNode;
  /** Right-align (recommended for numeric columns). Defaults to true. */
  align?: 'left' | 'right';
};

export function moneyColumn<Row>(
  opts: MoneyColumnOptions<Row>,
): TableColumnType<Row> {
  const align = opts.align ?? 'right';
  return {
    title: opts.title ?? 'Amount',
    key:
      opts.key ??
      (typeof opts.dataIndex === 'function' ? 'amount' : String(opts.dataIndex)),
    width: opts.width,
    align,
    render: (_, row) => {
      const v = get(row, opts.dataIndex);
      if (v == null) return opts.emptyValue ?? '—';
      return (
        <MoneyCell
          amount={v}
          compact={opts.compact}
          showSign={opts.showSign}
          coloredSign={opts.coloredSign}
        />
      );
    },
  };
}

/* ------------------------ Date-range column -------------------------- */

const SHORT_DATE = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
});

function fmtShort(value: string | Date | null | undefined): string {
  if (!value) return '—';
  const d = typeof value === 'string' ? new Date(value) : value;
  if (Number.isNaN(d.getTime())) return '—';
  return SHORT_DATE.format(d);
}

export type DateRangeColumnOptions<Row> = {
  title?: ReactNode;
  key?: string;
  width?: number;
  /** Field name (or selector) for the start date. */
  from: ValueGetter<Row, string | Date | null | undefined>;
  /** Field name (or selector) for the end date. */
  to: ValueGetter<Row, string | Date | null | undefined>;
  /** Render an extra trailing badge (e.g. "· 3d"). */
  trailing?: (row: Row) => ReactNode;
};

export function dateRangeColumn<Row>(
  opts: DateRangeColumnOptions<Row>,
): TableColumnType<Row> {
  return {
    title: opts.title ?? 'Range',
    key: opts.key ?? 'range',
    width: opts.width,
    render: (_, row) => {
      const from = get(row, opts.from);
      const to = get(row, opts.to);
      const fromText = fmtShort(from);
      const sameDay =
        from && to && new Date(from).toDateString() === new Date(to).toDateString();
      return (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {fromText}
          {!sameDay && to ? ` → ${fmtShort(to)}` : ''}
          {opts.trailing ? (
            <span style={{ marginLeft: 8, opacity: 0.7 }}>{opts.trailing(row)}</span>
          ) : null}
        </span>
      );
    },
  };
}

/* --------------------------- Person column --------------------------- */

export type PersonColumnOptions<Row> = {
  title?: ReactNode;
  key?: string;
  width?: number;
  /** Selector returning the PersonCell-shaped person. */
  dataIndex: ValueGetter<Row, PersonCellPerson | null | undefined>;
  /** Build a router link target (e.g. row → `/people/${row.employee.id}`). */
  to?: (row: Row) => string | undefined;
  size?: 'compact' | 'default';
  hideSubtitle?: boolean;
};

export function personColumn<Row>(
  opts: PersonColumnOptions<Row>,
): TableColumnType<Row> {
  return {
    title: opts.title ?? 'Person',
    key: opts.key ?? 'person',
    width: opts.width,
    render: (_, row) => {
      const person = get(row, opts.dataIndex);
      if (!person) return '—';
      return (
        <PersonCell
          size={opts.size ?? 'compact'}
          to={opts.to?.(row)}
          hideSubtitle={opts.hideSubtitle}
          person={person}
        />
      );
    },
  };
}

/* -------------------------- Employee column -------------------------- */

/**
 * Specialization of personColumn for the BFF employee-summary shape.
 * When `designationTitle` is provided, PersonCell renders a clean
 * `Designation · CODE` subtitle. Without it we fall back to `code · dept`
 * (or `#id` if no code) — the original 4+ tables shared this pattern.
 */
export type EmployeeRef = {
  id?: number;
  fullName: string;
  employeeCode?: string | null;
  designationTitle?: string | null;
  departmentName?: string | null;
  profilePictureUrl?: string | null;
};

export type EmployeeColumnOptions<Row> = {
  title?: ReactNode;
  key?: string;
  width?: number;
  /** Selector returning the employee object on the row. */
  employee: ValueGetter<Row, EmployeeRef | null | undefined>;
  /** Link the cell to a route (typically `/people/${id}`). */
  to?: (row: Row) => string | undefined;
  size?: 'compact' | 'default';
  /** Hide the secondary line (code · dept). */
  hideSubtitle?: boolean;
  /** Override the subtitle (default: `code · dept`). */
  subtitle?: (row: Row, employee: EmployeeRef) => ReactNode;
};

export function employeeColumn<Row>(
  opts: EmployeeColumnOptions<Row>,
): TableColumnType<Row> {
  return {
    title: opts.title ?? 'Employee',
    key: opts.key ?? 'employee',
    width: opts.width,
    render: (_, row) => {
      const e = get(row, opts.employee);
      if (!e) return '—';
      // Caller-supplied subtitle wins. Otherwise prefer the structured
      // designation+code rendering inside PersonCell; fall back to
      // dept · code only when no designation is on the row.
      const customSubtitle = opts.subtitle?.(row, e);
      const fallbackSubtitle =
        !e.designationTitle && e.departmentName
          ? `${e.employeeCode ?? (e.id != null ? `#${e.id}` : '')}${
              e.employeeCode || e.id != null ? ' · ' : ''
            }${e.departmentName}`
          : undefined;
      return (
        <PersonCell
          size={opts.size ?? 'compact'}
          to={opts.to?.(row)}
          hideSubtitle={opts.hideSubtitle}
          person={{
            fullName: e.fullName,
            employeeCode: e.employeeCode,
            designation: e.designationTitle,
            profilePictureUrl: e.profilePictureUrl,
            subtitle: customSubtitle ?? fallbackSubtitle,
          }}
        />
      );
    },
  };
}
