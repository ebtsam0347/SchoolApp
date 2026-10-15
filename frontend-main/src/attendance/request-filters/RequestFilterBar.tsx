// Employee picker, date range and status for a request list's section
// header. `dateLabel` names what the range applies to (leave date vs. filed
// date) — the endpoints differ. Without `employeeOptions` (my own list)
// there's no employee picker.

import { Button, DatePicker, Select, Space } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { FilterX } from 'lucide-react';

import { isFiltered, type EmployeeOption, type RequestListFilters } from './filters';

export type RequestFilterBarProps<S extends string> = {
  value: RequestListFilters<S>;
  onChange: (next: RequestListFilters<S>) => void;
  statusOptions: { value: S; label: string }[];
  employeeOptions?: EmployeeOption[];
  employeesLoading?: boolean;
  dateLabel: string;
  /** Leave dates may lie ahead; filed dates can't. */
  allowFuture?: boolean;
};

export function RequestFilterBar<S extends string>({
  value,
  onChange,
  statusOptions,
  employeeOptions,
  employeesLoading,
  dateLabel,
  allowFuture,
}: RequestFilterBarProps<S>) {
  const { from, to } = value;
  return (
    <Space size={8} wrap>
      {employeeOptions ? (
        <Select
          size="small"
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="All employees"
          loading={employeesLoading}
          value={value.employeeId}
          options={employeeOptions}
          onChange={(v?: number) => onChange({ ...value, employeeId: v ?? undefined })}
          style={{ width: 220 }}
        />
      ) : null}
      <DatePicker.RangePicker
        size="small"
        allowClear
        format="DD MMM YYYY"
        placeholder={[`${dateLabel} from`, `${dateLabel} to`]}
        value={from || to ? [from ? dayjs(from) : null, to ? dayjs(to) : null] : null}
        allowEmpty={[true, true]}
        disabledDate={allowFuture ? undefined : (d: Dayjs) => d.isAfter(dayjs(), 'day')}
        presets={[
          { label: 'Today', value: [dayjs(), dayjs()] },
          { label: 'Last 7 days', value: [dayjs().subtract(6, 'day'), dayjs()] },
          { label: 'This month', value: [dayjs().startOf('month'), dayjs()] },
          {
            label: 'Last month',
            value: [
              dayjs().subtract(1, 'month').startOf('month'),
              dayjs().subtract(1, 'month').endOf('month'),
            ],
          },
          { label: 'Last 3 months', value: [dayjs().subtract(3, 'month'), dayjs()] },
          { label: 'This year', value: [dayjs().startOf('year'), dayjs()] },
        ]}
        onChange={(range) =>
          onChange({
            ...value,
            from: range?.[0]?.format('YYYY-MM-DD'),
            to: range?.[1]?.format('YYYY-MM-DD'),
          })
        }
      />
      <Select<S>
        size="small"
        allowClear
        placeholder="All statuses"
        value={value.status}
        options={statusOptions}
        onChange={(v?: S) => onChange({ ...value, status: v ?? undefined })}
        style={{ width: 160 }}
      />
      {isFiltered(value) ? (
        <Button size="small" type="text" icon={<FilterX size={14} />} onClick={() => onChange({})}>
          Clear
        </Button>
      ) : null}
    </Space>
  );
}
