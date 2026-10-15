// Searchable employee picker — the single typeahead surface every form that
// targets an ACTIVE employee by name/code should use (payroll off-cycle /
// increment / bonus, attendance operator flows, notification recipients, …).
// Backed by GET /employees/picker (employee service) via `employeePickerApi`.
//
// Mirrors AntD's controlled Select contract so it slots into Form.Item just
// like InputNumber. The form value is the employee id (single mode) or an id
// array (multi mode). Selected employees keep their chip labels even when
// the search query changes — labels are cached locally so chips don't reduce
// to bare numbers when results refresh.

import { useQuery } from '@tanstack/react-query';
import { Select, Spin } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import {
  employeePickerApi,
  type EmployeePickerCandidate,
} from './employee-picker';

type CommonProps = {
  placeholder?: string;
  disabled?: boolean;
  /** Search-result cap. Server clamps too — keep it tight. */
  limit?: number;
  style?: React.CSSProperties;
  allowClear?: boolean;
  /** Client-side narrowing on top of the server search (e.g. same department). */
  filterCandidate?: (candidate: EmployeePickerCandidate) => boolean;
  /**
   * Extra candidates shown before the caller types anything — e.g. a
   * pre-fetched department roster, so the dropdown isn't empty until a
   * search happens to surface someone from that scope in its first page.
   * Ignored once the caller starts typing (the live server search takes
   * over); still passed through `filterCandidate`.
   */
  staticCandidates?: EmployeePickerCandidate[];
};

type SingleProps = CommonProps & {
  multiple?: false;
  value?: number;
  onChange?: (value: number | undefined) => void;
  /** The picked row itself (undefined on clear) — for a details card
   *  beside the picker without a second lookup. */
  onSelectCandidate?: (candidate: EmployeePickerCandidate | undefined) => void;
};

type MultiProps = CommonProps & {
  multiple: true;
  value?: number[];
  onChange?: (value: number[]) => void;
  /** AntD chip-overflow control. Defaults to 'responsive'. */
  maxTagCount?: number | 'responsive';
};

export type EmployeePickerProps = SingleProps | MultiProps;

export function EmployeePicker(props: EmployeePickerProps) {
  const {
    placeholder = 'Search by name or code',
    disabled,
    limit = 20,
    style,
    allowClear = true,
    filterCandidate,
    staticCandidates,
  } = props;
  const [q, setQ] = useState<string>('');
  const [labelCache, setLabelCache] = useState<
    Record<number, EmployeePickerCandidate>
  >({});

  const candidatesQuery = useQuery({
    queryKey: ['employees', 'picker', q, limit],
    queryFn: ({ signal }) =>
      employeePickerApi.search({ q: q.trim() || undefined, limit }, signal),
    staleTime: 30_000,
  });

  const candidates = useMemo(() => {
    let items = candidatesQuery.data?.items ?? [];
    // Nothing typed yet — pad with the static pool (e.g. department roster)
    // so the dropdown has options before a search happens to surface one.
    if (!q.trim() && staticCandidates?.length) {
      const seenIds = new Set(items.map((c) => c.id));
      items = [...items, ...staticCandidates.filter((c) => !seenIds.has(c.id))];
    }
    return filterCandidate ? items.filter(filterCandidate) : items;
  }, [candidatesQuery.data, filterCandidate, staticCandidates, q]);

  // Remember every candidate we've seen so that chips for already-selected
  // employees keep their label/code/department after the search box changes.
  useEffect(() => {
    if (candidates.length === 0) return;
    setLabelCache((prev) => {
      let next = prev;
      for (const c of candidates) {
        if (next[c.id] !== c) {
          if (next === prev) next = { ...prev };
          next[c.id] = c;
        }
      }
      return next;
    });
  }, [candidates]);

  const selectedIds: number[] = props.multiple
    ? (props.value ?? [])
    : props.value !== undefined
      ? [props.value]
      : [];

  const seen = new Map<number, EmployeePickerCandidate>();
  for (const c of candidates) seen.set(c.id, c);
  for (const id of selectedIds) {
    if (!seen.has(id) && labelCache[id]) seen.set(id, labelCache[id]);
  }
  const options = [...seen.values()].map((c) => ({
    value: c.id,
    label: c.fullName,
    data: c,
  }));

  const notFoundContent = candidatesQuery.isFetching ? (
    <Spin size="small" />
  ) : q.trim() ? (
    'No matches'
  ) : (
    'Type to search'
  );

  const renderOption = (opt: { data: unknown }) => {
    const c = (opt.data as { data: EmployeePickerCandidate }).data;
    const subtitleParts = [
      c.employeeCode ?? `#${c.id}`,
      c.designationName,
      c.departmentName,
    ].filter(Boolean);
    return (
      <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2 }}>
        <span style={{ fontWeight: 500 }}>{c.fullName}</span>
        <span style={{ fontSize: 11, opacity: 0.7 }}>
          {subtitleParts.join(' · ')}
        </span>
      </div>
    );
  };

  if (props.multiple) {
    return (
      <Select<number[]>
        mode="multiple"
        showSearch
        filterOption={false}
        value={props.value}
        onChange={(v) => props.onChange?.(v ?? [])}
        onSearch={setQ}
        placeholder={placeholder}
        disabled={disabled}
        style={style}
        allowClear={allowClear}
        maxTagCount={props.maxTagCount ?? 'responsive'}
        notFoundContent={notFoundContent}
        options={options}
        optionRender={renderOption}
      />
    );
  }

  return (
    <Select<number>
      showSearch
      filterOption={false}
      value={props.value}
      onChange={(v) => {
        props.onChange?.(v ?? undefined);
        props.onSelectCandidate?.(v == null ? undefined : seen.get(v));
      }}
      onSearch={setQ}
      placeholder={placeholder}
      disabled={disabled}
      style={style}
      allowClear={allowClear}
      notFoundContent={notFoundContent}
      options={options}
      optionRender={renderOption}
    />
  );
}
