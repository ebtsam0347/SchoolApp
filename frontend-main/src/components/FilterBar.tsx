// Toolbar for list pages: search input on the left, slot for select / range
// dropdowns in the middle, slot for primary actions on the right, and an
// optional Reset button.
//
// We intentionally do NOT own the filter state here — every page already
// has its own useState shape (debounced search, status enum, role filter,
// etc.) and pulling that into a shared hook would be over-abstraction. The
// FilterBar just provides the layout primitive + the reset affordance.
//
// Usage:
//   <FilterBar
//     search={{ value: searchInput, onChange: setSearchInput, placeholder: 'Search users…' }}
//     filters={
//       <>
//         <Select value={status} onChange={setStatus} options={STATUS_OPTIONS} />
//         <RangePicker value={range} onChange={setRange} />
//       </>
//     }
//     actions={<Button type="primary" onClick={() => setCreateOpen(true)}>New user</Button>}
//     onReset={hasActiveFilters ? () => { setStatus('ALL'); setRange(null); ... } : undefined}
//   />

import { Button, Input, Space } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import { Search, X } from 'lucide-react';

export type FilterBarProps = {
  /** Search-input config. Omit to hide the search box. */
  search?: {
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    /** Width override (default 280). */
    width?: number;
  };
  /** Filter chips / selects rendered between search and actions. */
  filters?: ReactNode;
  /** Right-side action buttons (typically a primary CTA). */
  actions?: ReactNode;
  /**
   * Reset callback. Render only when filters are dirty so the button is
   * meaningful. Pass `undefined` to hide it.
   */
  onReset?: () => void;
  /** Reset-button label. Defaults to "Reset". */
  resetLabel?: ReactNode;
  className?: string;
  style?: CSSProperties;
};

export function FilterBar({
  search,
  filters,
  actions,
  onReset,
  resetLabel = 'Reset',
  className,
  style,
}: FilterBarProps) {
  return (
    <div
      className={className}
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        gap: 'var(--app-space-sm)',
        ...style,
      }}
    >
      {search ? (
        <Input
          allowClear
          prefix={<Search size={14} strokeWidth={1.75} />}
          placeholder={search.placeholder}
          value={search.value}
          onChange={(e) => search.onChange(e.target.value)}
          style={{ width: search.width ?? 280, maxWidth: '100%' }}
        />
      ) : null}
      {filters ? <Space size={8} wrap>{filters}</Space> : null}
      {onReset ? (
        <Button
          type="text"
          size="small"
          icon={<X size={12} strokeWidth={1.75} />}
          onClick={onReset}
        >
          {resetLabel}
        </Button>
      ) : null}
      {actions ? (
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 'var(--app-space-sm)' }}>
          {actions}
        </div>
      ) : null}
    </div>
  );
}
