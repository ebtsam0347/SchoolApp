import { Select } from 'antd';

import { FilterBar } from '@/components';
import {
  EMPLOYMENT_STATUS_LABEL,
  EMPLOYMENT_TYPE_LABEL,
  type DepartmentDto,
  type DesignationDto,
  type EmploymentStatus,
  type EmploymentType,
  type GradeDto,
  type SectionDto,
} from '../types';
import {
  STATUSES,
  TYPES,
  type DirectorySearch,
} from './types';

export type DirectoryFiltersProps = {
  searchInput: string;
  onSearchInputChange: (next: string) => void;
  search: DirectorySearch;
  onUpdate: (patch: Partial<DirectorySearch>) => void;
  departments: DepartmentDto[] | undefined;
  sections: SectionDto[] | undefined;
  designations: DesignationDto[] | undefined;
  grades: GradeDto[] | undefined;
  loading: {
    departments: boolean;
    sections: boolean;
    designations: boolean;
    grades: boolean;
  };
};

export function DirectoryFilters({
  searchInput,
  onSearchInputChange,
  search,
  onUpdate,
  departments,
  sections,
  designations,
  grades,
  loading,
}: DirectoryFiltersProps) {
  const isDirty =
    searchInput.length > 0 ||
    search.status !== undefined ||
    search.employmentType !== undefined ||
    search.departmentId !== undefined ||
    search.sectionId !== undefined ||
    search.designationId !== undefined ||
    search.gradeId !== undefined ||
    search.hasEss !== undefined;

  const handleReset = () => {
    onSearchInputChange('');
    onUpdate({
      q: undefined,
      status: undefined,
      employmentType: undefined,
      departmentId: undefined,
      sectionId: undefined,
      designationId: undefined,
      gradeId: undefined,
      hasEss: undefined,
    });
  };

  return (
    <FilterBar
      search={{
        value: searchInput,
        onChange: onSearchInputChange,
        placeholder: 'Search name, employee code, email, phone, CNIC…',
        width: 360,
      }}
      onReset={isDirty ? handleReset : undefined}
      resetLabel="Clear all"
      filters={
        <>
      <Select<EmploymentStatus | 'ALL'>
        value={search.status ?? 'ALL'}
        onChange={(v) => onUpdate({ status: v === 'ALL' ? undefined : v })}
        style={{ width: 150 }}
        options={[
          { value: 'ALL', label: 'Any status' },
          ...STATUSES.map((s) => ({
            value: s,
            label: EMPLOYMENT_STATUS_LABEL[s],
          })),
        ]}
      />
      <Select<EmploymentType | 'ALL'>
        value={search.employmentType ?? 'ALL'}
        onChange={(v) =>
          onUpdate({ employmentType: v === 'ALL' ? undefined : v })
        }
        style={{ width: 150 }}
        options={[
          { value: 'ALL', label: 'Any type' },
          ...TYPES.map((t) => ({
            value: t,
            label: EMPLOYMENT_TYPE_LABEL[t],
          })),
        ]}
      />
      <Select<number | 'ALL'>
        value={search.departmentId ?? 'ALL'}
        onChange={(v) =>
          onUpdate({
            departmentId: v === 'ALL' ? undefined : v,
            sectionId: undefined,
          })
        }
        loading={loading.departments}
        style={{ width: 200 }}
        placeholder="Department"
        showSearch
        optionFilterProp="label"
        options={[
          { value: 'ALL', label: 'Any department' },
          ...(departments ?? [])
            .filter((d) => d.active !== false)
            .map((d) => ({
              value: d.id!,
              label: d.name ?? d.code ?? `#${d.id}`,
            })),
        ]}
      />
      <Select<number | 'ALL'>
        value={search.sectionId ?? 'ALL'}
        onChange={(v) =>
          onUpdate({ sectionId: v === 'ALL' ? undefined : v })
        }
        loading={loading.sections}
        disabled={!search.departmentId}
        style={{ width: 200 }}
        placeholder="Section"
        showSearch
        optionFilterProp="label"
        options={[
          { value: 'ALL', label: 'Any section' },
          ...(sections ?? [])
            .filter(
              (s) =>
                !search.departmentId || s.departmentId === search.departmentId,
            )
            .filter((s) => s.active !== false)
            .map((s) => ({
              value: s.id!,
              label: s.name ?? s.code ?? `#${s.id}`,
            })),
        ]}
      />
      <Select<number | 'ALL'>
        value={search.designationId ?? 'ALL'}
        onChange={(v) =>
          onUpdate({ designationId: v === 'ALL' ? undefined : v })
        }
        loading={loading.designations}
        style={{ width: 200 }}
        placeholder="Designation"
        showSearch
        optionFilterProp="label"
        options={[
          { value: 'ALL', label: 'Any designation' },
          ...(designations ?? [])
            .filter((d) => d.active !== false)
            .map((d) => ({
              value: d.id!,
              label: d.title ?? d.code ?? `#${d.id}`,
            })),
        ]}
      />
      <Select<number | 'ALL'>
        value={search.gradeId ?? 'ALL'}
        onChange={(v) =>
          onUpdate({ gradeId: v === 'ALL' ? undefined : v })
        }
        loading={loading.grades}
        style={{ width: 130 }}
        placeholder="Grade"
        showSearch
        optionFilterProp="label"
        options={[
          { value: 'ALL', label: 'Any grade' },
          ...(grades ?? [])
            .filter((g) => g.active !== false)
            .sort((a, b) => (a.level ?? 0) - (b.level ?? 0))
            .map((g) => ({
              value: g.id!,
              label: g.name ?? g.code ?? `#${g.id}`,
            })),
        ]}
      />
      <Select<'ALL' | 'YES' | 'NO'>
        value={
          search.hasEss === undefined ? 'ALL' : search.hasEss ? 'YES' : 'NO'
        }
        onChange={(v) =>
          onUpdate({
            hasEss: v === 'ALL' ? undefined : v === 'YES',
          })
        }
        style={{ width: 130 }}
        options={[
          { value: 'ALL', label: 'Any ESS' },
          { value: 'YES', label: 'ESS granted' },
          { value: 'NO', label: 'No ESS' },
        ]}
      />
        </>
      }
    />
  );
}
