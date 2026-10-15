// Picker for the "submit on behalf of" target on the self-service apply
// drawers (currently leave). Renders nothing for plain ESS users — only
// surfaces when the caller is a manager (has direct reports) or holds
// `attendance.approval.override` (HR).
//
// Manager mode lists `/employees/me/team` — the caller's direct reports,
// which is exactly who attendance-service lets a manager file for (the
// MANAGER stage is auto-approved). Override mode adds the service-neutral
// employee picker (GET /employees/picker) so HR can search across the whole
// org. The form value is the target employee id, or undefined for "Myself".
//
// (An earlier version also listed `/employees/me/on-behalf-candidates`; that
// endpoint is gone from employee-service.)

import { useQuery } from '@tanstack/react-query';
import { Select, Spin } from 'antd';
import { useState } from 'react';
import type { CSSProperties } from 'react';

import { useAuth } from '@/auth';
import { employeesApi } from '@/people/api';
import { employeePickerApi } from '@/people/employee-picker';

const SELF_VALUE = '__self__';

const HR_OVERRIDE = 'attendance.approval.override';

export type OnBehalfPickerProps = {
  /** Form value: target employee id, or undefined when "Myself". */
  value?: number;
  onChange?: (value: number | undefined) => void;
  disabled?: boolean;
  style?: CSSProperties;
};

/**
 * Returns true when the picker has anything to show — the caller holds the HR
 * override or manages someone. Use this to gate the surrounding `Form.Item`
 * so plain ESS users see no extra field.
 */
export function useCanSubmitOnBehalf(): {
  enabled: boolean;
  hasOverride: boolean;
  hasReports: boolean;
} {
  const { user } = useAuth();
  const hasOverride = !!user?.permissions.includes(HR_OVERRIDE);
  const teamQuery = useQuery({
    queryKey: ['employees', 'me', 'team'],
    queryFn: ({ signal }) => employeesApi.myTeam(signal),
    staleTime: 5 * 60_000,
    enabled: !!user && !hasOverride,
    retry: false,
  });
  const hasReports = (teamQuery.data?.length ?? 0) > 0;
  return { enabled: hasOverride || hasReports, hasOverride, hasReports };
}

export function OnBehalfPicker({
  value,
  onChange,
  disabled,
  style,
}: OnBehalfPickerProps) {
  const { user } = useAuth();
  const hasOverride = !!user?.permissions.includes(HR_OVERRIDE);

  // Direct reports. Override mode still loads them — HR often manages a team
  // of their own, and those rows save a search for the people they file for
  // most.
  const teamQuery = useQuery({
    queryKey: ['employees', 'me', 'team'],
    queryFn: ({ signal }) => employeesApi.myTeam(signal),
    staleTime: 5 * 60_000,
    enabled: !!user,
    retry: false,
  });
  const team = teamQuery.data ?? [];

  // Override mode: org-wide search. Lazy — only fires when the user starts
  // typing. Reuses the service-neutral employee picker (ACTIVE-only).
  const [q, setQ] = useState('');
  const orgQuery = useQuery({
    queryKey: ['employees', 'picker', q, 20],
    queryFn: ({ signal }) =>
      employeePickerApi.search({ q: q.trim() || undefined, limit: 20 }, signal),
    staleTime: 30_000,
    enabled: hasOverride,
  });

  const selfOption = { value: SELF_VALUE, label: 'Myself' };
  const teamOptions = team.map((e) => ({
    value: String(e.id),
    label: e.fullName ?? `#${e.id}`,
    employeeCode: e.employeeCode ?? null,
  }));
  const orgOptions = (orgQuery.data?.items ?? []).map((c) => ({
    value: String(c.id),
    label: c.fullName,
    employeeCode: c.employeeCode ?? null,
  }));

  // Override sees: Self + their own people + org search results.
  // Manager-only sees: Self + their direct reports.
  // mergeUnique matters for override — a direct report can also come back
  // from the org search.
  const options = hasOverride
    ? [selfOption, ...mergeUnique([...teamOptions, ...orgOptions])]
    : [selfOption, ...teamOptions];

  const selectValue = value == null ? SELF_VALUE : String(value);

  return (
    <Select
      // Manager mode filters the already-loaded team locally; override mode
      // defers to the server, hence filterOption off there.
      showSearch
      filterOption={hasOverride ? false : undefined}
      optionFilterProp="label"
      onSearch={hasOverride ? setQ : undefined}
      value={selectValue}
      onChange={(v) => onChange?.(v === SELF_VALUE ? undefined : Number(v))}
      disabled={disabled}
      style={style}
      notFoundContent={
        orgQuery.isFetching ? (
          <Spin size="small" />
        ) : hasOverride && q.trim() ? (
          'No matches'
        ) : (
          'Type to search'
        )
      }
      options={options}
      optionRender={(opt) => {
        if (opt.value === SELF_VALUE) {
          return <span>Myself</span>;
        }
        const data = opt.data as { label: string; employeeCode?: string | null };
        return (
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2 }}>
            <span style={{ fontWeight: 500 }}>{data.label}</span>
            {data.employeeCode ? (
              <span style={{ fontSize: 11, opacity: 0.7 }}>{data.employeeCode}</span>
            ) : null}
          </div>
        );
      }}
    />
  );
}

function mergeUnique<T extends { value: string }>(rows: T[]): T[] {
  const seen = new Set<string>();
  const out: T[] = [];
  for (const r of rows) {
    if (seen.has(r.value)) continue;
    seen.add(r.value);
    out.push(r);
  }
  return out;
}
