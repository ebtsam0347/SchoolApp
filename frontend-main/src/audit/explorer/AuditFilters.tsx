import { DatePicker, Input, Select, theme as antdTheme } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Search } from 'lucide-react';
import type { ReactNode } from 'react';

import {
  VISIBILITY_OPTIONS,
  type FiltersState,
  type VisibilityFilter,
} from './types';

const { RangePicker } = DatePicker;
const { useToken } = antdTheme;

export type AuditFiltersProps = {
  filters: FiltersState;
  onChange: (next: Partial<FiltersState>) => void;
  facetsLoading: boolean;
  entityOptions: { value: string; label: string }[];
  actionOptions: { value: string; label: string }[];
  serviceOptions: { value: string; label: string }[];
  /**
   * `row` (default) = horizontal grid that reflows. Used when filters
   * sit above the table.
   * `stack` = vertical stack with labels above each control. Used when
   * filters sit in a left rail / sidebar.
   */
  layout?: 'row' | 'stack';
};

export function AuditFilters({
  filters,
  onChange,
  facetsLoading,
  entityOptions,
  actionOptions,
  serviceOptions,
  layout = 'row',
}: AuditFiltersProps) {
  if (layout === 'stack') {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <Field label="Search">
          <Input
            allowClear
            prefix={<Search size={14} strokeWidth={1.75} />}
            placeholder="Message…"
            value={filters.q}
            onChange={(e) => onChange({ q: e.target.value })}
          />
        </Field>
        <Field label="Entity type">
          <Select
            allowClear
            placeholder={facetsLoading ? 'Loading…' : 'Any entity'}
            value={filters.entityType}
            onChange={(v) => onChange({ entityType: v })}
            options={entityOptions}
            showSearch
            optionFilterProp="label"
            loading={facetsLoading}
            style={{ width: '100%' }}
          />
        </Field>
        <Field label="Action">
          <Select
            allowClear
            placeholder={facetsLoading ? 'Loading…' : 'Any action'}
            value={filters.action}
            onChange={(v) => onChange({ action: v })}
            options={actionOptions}
            showSearch
            optionFilterProp="label"
            loading={facetsLoading}
            style={{ width: '100%' }}
          />
        </Field>
        <Field label="Source service">
          <Select
            allowClear
            placeholder="Any service"
            value={filters.sourceService}
            onChange={(v) => onChange({ sourceService: v })}
            options={serviceOptions}
            showSearch
            optionFilterProp="label"
            loading={facetsLoading}
            style={{ width: '100%' }}
          />
        </Field>
        <Field label="Visibility">
          <Select
            value={filters.visibility}
            onChange={(v) => onChange({ visibility: v as VisibilityFilter })}
            options={VISIBILITY_OPTIONS}
            style={{ width: '100%' }}
          />
        </Field>
        <Field label="Time range">
          <RangePicker
            showTime
            value={filters.range}
            onChange={(v) => onChange({ range: v as [Dayjs, Dayjs] | null })}
            style={{ width: '100%' }}
            presets={[
              { label: 'Last hour', value: [dayjs().subtract(1, 'hour'), dayjs()] },
              { label: 'Last 24 hours', value: [dayjs().subtract(1, 'day'), dayjs()] },
              { label: 'Last 7 days', value: [dayjs().subtract(7, 'day'), dayjs()] },
            ]}
          />
        </Field>
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 8,
      }}
    >
      <Input
        allowClear
        prefix={<Search size={14} strokeWidth={1.75} />}
        placeholder="Search message…"
        value={filters.q}
        onChange={(e) => onChange({ q: e.target.value })}
      />
      <Select
        allowClear
        placeholder={facetsLoading ? 'Loading…' : 'Entity type'}
        value={filters.entityType}
        onChange={(v) => onChange({ entityType: v })}
        options={entityOptions}
        showSearch
        optionFilterProp="label"
        loading={facetsLoading}
      />
      <Select
        allowClear
        placeholder={facetsLoading ? 'Loading…' : 'Action'}
        value={filters.action}
        onChange={(v) => onChange({ action: v })}
        options={actionOptions}
        showSearch
        optionFilterProp="label"
        loading={facetsLoading}
      />
      <Select
        allowClear
        placeholder="Source service"
        value={filters.sourceService}
        onChange={(v) => onChange({ sourceService: v })}
        options={serviceOptions}
        showSearch
        optionFilterProp="label"
        loading={facetsLoading}
      />
      <Select
        value={filters.visibility}
        onChange={(v) => onChange({ visibility: v as VisibilityFilter })}
        options={VISIBILITY_OPTIONS}
      />
      <RangePicker
        showTime
        value={filters.range}
        onChange={(v) => onChange({ range: v as [Dayjs, Dayjs] | null })}
        presets={[
          { label: 'Last hour', value: [dayjs().subtract(1, 'hour'), dayjs()] },
          { label: 'Last 24 hours', value: [dayjs().subtract(1, 'day'), dayjs()] },
          { label: 'Last 7 days', value: [dayjs().subtract(7, 'day'), dayjs()] },
        ]}
      />
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  const { token } = useToken();
  // Plain div — wrapping AntD Select in a real <label> retargets popup
  // clicks to the inner input and the dropdown closes immediately.
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span
        style={{
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: '0.06em',
          textTransform: 'uppercase',
          color: token.colorTextTertiary,
        }}
      >
        {label}
      </span>
      {children}
    </div>
  );
}
