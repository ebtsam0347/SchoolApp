// Sticky bar that appears at the bottom of a list page when rows are
// selected. Shows the count, a Clear button, and a slot for bulk-action
// buttons (e.g. Confirm N, Release N, Export).
//
// Pairs with AntD Table's `rowSelection` API. Wire selection state in
// the parent (`useState<Key[]>`), pass it through `rowSelection`, and
// render <SelectionToolbar/> below the table — it self-hides when count
// is 0.
//
// Usage:
//   <Table rowSelection={{ selectedRowKeys, onChange: setSelected }} … />
//   <SelectionToolbar
//     selectedCount={selectedRowKeys.length}
//     totalCount={rows.length}
//     onClear={() => setSelected([])}
//     actions={
//       <Button type="primary" onClick={onBulkConfirm}>
//         Confirm {selectedRowKeys.length}
//       </Button>
//     }
//   />

import { Button, theme as antdTheme } from 'antd';
import { X } from 'lucide-react';
import type { CSSProperties, ReactNode } from 'react';

const { useToken } = antdTheme;

export type SelectionToolbarProps = {
  selectedCount: number;
  /** Optional total — when provided, render "N of T selected". */
  totalCount?: number;
  /** Bulk action buttons. */
  actions: ReactNode;
  /** Clear-selection callback. Hides the X button when omitted. */
  onClear?: () => void;
  /**
   * Render mode.
   *  - `sticky` (default): position: sticky inside scroll container.
   *    Pinned to the bottom of the viewport area.
   *  - `inline`: just renders inline at its place in the tree.
   */
  mode?: 'sticky' | 'inline';
  /** Custom label for the count (default: "selected"). */
  label?: string;
  /** Force-show even when count is 0 (e.g. to render a header bar). */
  alwaysVisible?: boolean;

  className?: string;
  style?: CSSProperties;
};

export function SelectionToolbar({
  selectedCount,
  totalCount,
  actions,
  onClear,
  mode = 'sticky',
  label = 'selected',
  alwaysVisible = false,
  className,
  style,
}: SelectionToolbarProps) {
  const { token } = useToken();
  const visible = alwaysVisible || selectedCount > 0;
  if (!visible) return null;

  return (
    <div
      role="toolbar"
      aria-label="Bulk actions"
      className={className}
      style={{
        position: mode === 'sticky' ? 'sticky' : undefined,
        bottom: mode === 'sticky' ? 0 : undefined,
        zIndex: mode === 'sticky' ? 5 : undefined,
        marginTop: 'var(--app-space-md)',
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--app-space-md)',
        padding: '10px 14px',
        borderRadius: token.borderRadiusLG,
        background: token.colorBgElevated,
        border: `1px solid ${token.colorBorderSecondary}`,
        boxShadow: 'var(--app-shadow-md)',
        flexWrap: 'wrap',
        ...style,
      }}
    >
      <span style={{ fontSize: 13, color: token.colorTextHeading, fontWeight: 500 }}>
        <strong style={{ fontVariantNumeric: 'tabular-nums' }}>{selectedCount}</strong>
        {totalCount != null ? (
          <span style={{ color: token.colorTextSecondary, fontWeight: 400 }}>
            {' '}
            of {totalCount}
          </span>
        ) : null}{' '}
        <span style={{ color: token.colorTextSecondary, fontWeight: 400 }}>{label}</span>
      </span>
      <div style={{ display: 'flex', gap: 'var(--app-space-sm)', marginLeft: 'auto', flexWrap: 'wrap' }}>
        {actions}
      </div>
      {onClear ? (
        <Button
          type="text"
          size="small"
          icon={<X size={12} strokeWidth={1.75} />}
          onClick={onClear}
          aria-label="Clear selection"
        >
          Clear
        </Button>
      ) : null}
    </div>
  );
}
