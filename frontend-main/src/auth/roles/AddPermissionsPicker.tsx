// Popover content used by RoleCard's "Add permission" affordance. Server
// `permission.group` drives the option groups so the dropdown reads as
// "Identity / Compliance / …" rather than a flat alphabetical list.

import { Button, Select, Space } from 'antd';
import type { PermissionDto } from '@/auth';

export type AddPermissionsPickerProps = {
  available: PermissionDto[];
  value: string[];
  onChange: (next: string[]) => void;
  onCancel: () => void;
  onApply: () => void;
  loading: boolean;
};

export function AddPermissionsPicker({
  available,
  value,
  onChange,
  onCancel,
  onApply,
  loading,
}: AddPermissionsPickerProps) {
  const options = (() => {
    const groups = new Map<string, PermissionDto[]>();
    for (const p of available) {
      const g = p.group || 'Other';
      if (!groups.has(g)) groups.set(g, []);
      groups.get(g)!.push(p);
    }
    return [...groups.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([label, items]) => ({
        label,
        options: items.map((p) => ({
          value: p.code,
          label: `${p.code} — ${p.name}`,
        })),
      }));
  })();

  return (
    <div style={{ width: 360, display: 'flex', flexDirection: 'column', gap: 8 }}>
      <Select
        mode="multiple"
        showSearch
        autoFocus
        open
        placeholder="Search permissions…"
        value={value}
        onChange={onChange}
        options={options}
        optionFilterProp="label"
        maxTagCount="responsive"
        style={{ width: '100%' }}
        styles={{ popup: { root: { maxHeight: 260, overflowY: 'auto' } } }}
      />
      <Space style={{ justifyContent: 'flex-end', marginTop: 24 }}>
        <Button size="small" onClick={onCancel} disabled={loading}>
          Cancel
        </Button>
        <Button
          size="small"
          type="primary"
          onClick={onApply}
          loading={loading}
          disabled={value.length === 0}
        >
          Add {value.length || ''}
        </Button>
      </Space>
    </div>
  );
}
