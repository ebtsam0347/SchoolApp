import { Button, Tag, Typography } from 'antd';
import { X } from 'lucide-react';

import {
  EMPLOYMENT_STATUS_LABEL,
  EMPLOYMENT_TYPE_LABEL,
  type DepartmentDto,
  type DesignationDto,
  type GradeDto,
  type SectionDto,
} from '../types';
import type { DirectorySearch } from './types';

const { Text } = Typography;

export type FilterChipsProps = {
  search: DirectorySearch;
  departments: DepartmentDto[] | undefined;
  sections: SectionDto[] | undefined;
  designations: DesignationDto[] | undefined;
  grades: GradeDto[] | undefined;
  onClear: (key: keyof DirectorySearch) => void;
  onClearAll: () => void;
};

export function FilterChips({
  search,
  departments,
  sections,
  designations,
  grades,
  onClear,
  onClearAll,
}: FilterChipsProps) {
  const chips: { key: keyof DirectorySearch; label: string }[] = [];
  if (search.q) chips.push({ key: 'q', label: `“${search.q}”` });
  if (search.status)
    chips.push({
      key: 'status',
      label: EMPLOYMENT_STATUS_LABEL[search.status],
    });
  if (search.employmentType)
    chips.push({
      key: 'employmentType',
      label: EMPLOYMENT_TYPE_LABEL[search.employmentType],
    });
  if (search.departmentId) {
    const dep = departments?.find((d) => d.id === search.departmentId);
    chips.push({
      key: 'departmentId',
      label: dep?.name ?? `Dept #${search.departmentId}`,
    });
  }
  if (search.sectionId) {
    const sec = sections?.find((s) => s.id === search.sectionId);
    chips.push({
      key: 'sectionId',
      label: sec?.name ?? `Section #${search.sectionId}`,
    });
  }
  if (search.designationId) {
    const d = designations?.find((x) => x.id === search.designationId);
    chips.push({
      key: 'designationId',
      label: d?.title ?? `Desig. #${search.designationId}`,
    });
  }
  if (search.gradeId) {
    const g = grades?.find((x) => x.id === search.gradeId);
    chips.push({
      key: 'gradeId',
      label: g?.name ?? `Grade #${search.gradeId}`,
    });
  }
  if (search.hasEss !== undefined)
    chips.push({
      key: 'hasEss',
      label: search.hasEss ? 'ESS granted' : 'ESS not granted',
    });

  if (chips.length === 0) return null;

  return (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        gap: 6,
      }}
    >
      <Text type="secondary" style={{ fontSize: 12 }}>
        Filters:
      </Text>
      {chips.map((chip) => (
        <Tag
          key={chip.key}
          closable
          closeIcon={<X size={12} strokeWidth={2} />}
          onClose={(e) => {
            e.preventDefault();
            onClear(chip.key);
          }}
          style={{ margin: 0 }}
        >
          {chip.label}
        </Tag>
      ))}
      <Button type="link" size="small" onClick={onClearAll}>
        Clear all
      </Button>
    </div>
  );
}
