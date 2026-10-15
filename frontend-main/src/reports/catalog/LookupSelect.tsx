// Searchable Select over one of the org lookups (departments, sections,
// designations, grades), for report parameters named `departmentId` etc.
// Uses the same `['catalog', …]` queries as the employee forms, so the
// lists are shared with the rest of the app's cache.
//
// Sections follow the form's `departmentId` when one is chosen, and a
// department change clears a section that no longer belongs to it.

import { Form, Select } from 'antd';
import { useQuery } from '@tanstack/react-query';

import { catalogsApi } from '@/people';

import type { LookupKind } from './employee-filter';

const STALE = 5 * 60 * 1000;

type Option = { value: number; label: string };

function useLookupOptions(kind: LookupKind, departmentId: number | undefined) {
  const departments = useQuery({
    queryKey: ['catalog', 'departments'],
    queryFn: ({ signal }) => catalogsApi.departments(signal),
    staleTime: STALE,
    enabled: kind === 'department',
  });
  const sections = useQuery({
    queryKey: ['catalog', 'sections', departmentId],
    queryFn: ({ signal }) => catalogsApi.sections(departmentId, signal),
    staleTime: STALE,
    enabled: kind === 'section',
  });
  const designations = useQuery({
    queryKey: ['catalog', 'designations'],
    queryFn: ({ signal }) => catalogsApi.designations(signal),
    staleTime: STALE,
    enabled: kind === 'designation',
  });
  const grades = useQuery({
    queryKey: ['catalog', 'grades'],
    queryFn: ({ signal }) => catalogsApi.grades(signal),
    staleTime: STALE,
    enabled: kind === 'grade',
  });

  const pick = <T extends { id?: number }>(rows: T[] | undefined, label: (r: T) => string | undefined) =>
    (rows ?? []).flatMap((r): Option[] =>
      r.id == null ? [] : [{ value: r.id, label: label(r) ?? String(r.id) }],
    );

  switch (kind) {
    case 'department':
      return { options: pick(departments.data, (r) => r.name), loading: departments.isLoading };
    case 'section':
      return { options: pick(sections.data, (r) => r.name), loading: sections.isLoading };
    case 'designation':
      return { options: pick(designations.data, (r) => r.title), loading: designations.isLoading };
    case 'grade':
      return { options: pick(grades.data, (r) => r.name), loading: grades.isLoading };
  }
}

export interface LookupSelectProps {
  kind: LookupKind;
  /** Injected by Form.Item — ties the label to the input. */
  id?: string;
  value?: number;
  onChange?: (value: number | undefined) => void;
  placeholder?: string;
}

export function LookupSelect({ kind, id, value, onChange, placeholder }: LookupSelectProps) {
  const form = Form.useFormInstance();
  const departmentId = Form.useWatch('departmentId', form) as number | undefined;
  const { options, loading } = useLookupOptions(
    kind,
    kind === 'section' ? departmentId : undefined,
  );

  return (
    <Select<number>
      id={id}
      showSearch
      optionFilterProp="label"
      allowClear
      loading={loading}
      options={options}
      value={value}
      placeholder={placeholder ?? 'All'}
      onChange={(next) => {
        onChange?.(next);
        // A section picked under the old department no longer applies.
        if (kind === 'department' && form.getFieldValue('sectionId') != null) {
          form.setFieldValue('sectionId', undefined);
        }
      }}
    />
  );
}
