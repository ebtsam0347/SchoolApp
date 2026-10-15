// Dropdown that lets the user show / hide table columns. Pairs with
// `useTableColumns(storageKey, allColumns)` from @/lib — pass the hook's
// `options`, `toggle`, and `reset` straight through.
//
// Why a custom Popover instead of Dropdown.menu: AntD's Menu closes on
// every item click, which makes toggling multiple columns clunky. We keep
// the panel open until the user clicks outside.

import { Button, Checkbox, Popover, Typography, theme as antdTheme } from 'antd';
import { Columns3 } from 'lucide-react';
import type { ColumnPrefsOption } from '@/lib';

const { Text } = Typography;
const { useToken } = antdTheme;

export type ColumnsToggleProps = {
  options: ColumnPrefsOption[];
  onToggle: (key: string) => void;
  onReset: () => void;
  /** Compact button label. Defaults to "Columns". */
  buttonLabel?: string;
  size?: 'small' | 'middle' | 'large';
};

export function ColumnsToggle({
  options,
  onToggle,
  onReset,
  buttonLabel = 'Columns',
  size = 'middle',
}: ColumnsToggleProps) {
  const { token } = useToken();
  const hiddenCount = options.filter((o) => o.hidden).length;

  const content = (
    <div style={{ minWidth: 200, maxWidth: 280, display: 'flex', flexDirection: 'column', gap: 4 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '4px 4px 8px',
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <Text type="secondary" style={{ fontSize: 12 }}>
          Show columns
        </Text>
        <Button
          type="link"
          size="small"
          onClick={onReset}
          disabled={hiddenCount === 0}
          style={{ padding: 0, fontSize: 12 }}
        >
          Reset
        </Button>
      </div>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          paddingTop: 4,
          maxHeight: 320,
          overflowY: 'auto',
        }}
      >
        {options.map((o) => (
          <label
            key={o.key}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '6px 8px',
              borderRadius: token.borderRadius,
              cursor: 'pointer',
              fontSize: 13,
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = token.colorFillTertiary;
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'transparent';
            }}
          >
            <Checkbox checked={!o.hidden} onChange={() => onToggle(o.key)} />
            <span style={{ flex: 1, color: token.colorText }}>{o.label}</span>
          </label>
        ))}
      </div>
    </div>
  );

  return (
    <Popover content={content} trigger="click" placement="bottomRight" arrow={false}>
      <Button size={size} icon={<Columns3 size={14} strokeWidth={1.75} />}>
        {buttonLabel}
        {hiddenCount > 0 ? (
          <Text type="secondary" style={{ marginLeft: 4, fontSize: 12 }}>
            · {options.length - hiddenCount}/{options.length}
          </Text>
        ) : null}
      </Button>
    </Popover>
  );
}
