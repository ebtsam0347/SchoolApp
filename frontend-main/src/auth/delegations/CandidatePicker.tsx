// AntD Select wrapper that displays a richer label for each delegation
// candidate (full name + employee code + role tag). The parent owns the
// search query so multiple pickers can share one BFF query.

import { Select, Tag, Typography } from 'antd';
import type { DelegationCandidate } from '@/attendance';

const { Text } = Typography;

export type CandidatePickerProps = {
  value?: number;
  onChange?: (v: number | undefined) => void;
  onSearch: (q: string) => void;
  placeholder?: string;
  candidates: DelegationCandidate[];
  loading: boolean;
};

export function CandidatePicker({
  value,
  onChange,
  onSearch,
  placeholder,
  candidates,
  loading,
}: CandidatePickerProps) {
  const options = candidates.map((c) => ({
    value: c.userId,
    label: (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
        <strong style={{ fontWeight: 500 }}>{c.fullName}</strong>
        {c.employeeCode ? (
          <Text
            type="secondary"
            style={{ fontFamily: 'ui-monospace, monospace', fontSize: 11 }}
          >
            {c.employeeCode}
          </Text>
        ) : null}
        {c.role ? <Tag style={{ margin: 0, fontSize: 10 }}>{c.role}</Tag> : null}
      </span>
    ),
  }));

  return (
    <Select
      showSearch
      placeholder={placeholder}
      value={value}
      filterOption={false}
      onSearch={onSearch}
      onChange={onChange}
      options={options}
      loading={loading}
      notFoundContent={loading ? 'Searching…' : 'No matches'}
      allowClear
    />
  );
}
