// Label/value pairs for read-only detail panels (audit drawers, request
// summaries, person headers). Replaces ad-hoc `<KV>` helpers that were
// inlined in inbox.tsx, salary.tsx, delegations.tsx, users/$id.tsx — each
// repeating the same uppercase-tracked label + body styling.
//
// Two layouts:
//   - 'grid' (default) — auto-fit minmax columns, use for 2–N pairs
//   - 'inline' — wrapping flex row, use for compact metadata strips
//
// Variants live as `<FieldRow label value />` standalone for cases that
// only need one pair (e.g. a single-row sidebar fact).

import { theme as antdTheme, Typography } from 'antd';
import type { CSSProperties, ReactNode } from 'react';

const { Text } = Typography;
const { useToken } = antdTheme;

export type KvItem = {
  label: ReactNode;
  value: ReactNode;
  /** Optional second-line caption under value. */
  hint?: ReactNode;
  /** Span more than one column in grid mode. */
  span?: number;
};

export type KvListProps = {
  items: readonly KvItem[];
  /** 'grid' (default) for column layout; 'inline' for a wrapping strip. */
  layout?: 'grid' | 'inline';
  /** Min column width in grid mode. Defaults to 160px. */
  minColumnWidth?: number;
  /** Gap between items. Defaults to var(--app-space-md). */
  gap?: number | string;
  /** Body text size. Defaults to 13. */
  size?: 'sm' | 'md';
  className?: string;
  style?: CSSProperties;
  styles?: {
    label?: CSSProperties;
    value?: CSSProperties;
    hint?: CSSProperties;
  };
};

export function KvList({
  items,
  layout = 'grid',
  minColumnWidth = 160,
  gap,
  size = 'md',
  className,
  style,
  styles: slot,
}: KvListProps) {
  const valueSize = size === 'sm' ? 12 : 13;

  const containerStyle: CSSProperties =
    layout === 'grid'
      ? {
          display: 'grid',
          gridTemplateColumns: `repeat(auto-fit, minmax(${minColumnWidth}px, 1fr))`,
          gap: gap ?? 'var(--app-space-md)',
          ...style,
        }
      : {
          display: 'flex',
          flexWrap: 'wrap',
          gap: gap ?? 'var(--app-space-lg)',
          rowGap: 'var(--app-space-sm)',
          ...style,
        };

  return (
    <div className={className} style={containerStyle}>
      {items.map((item, idx) => (
        <FieldRow
          key={idx}
          label={item.label}
          value={item.value}
          hint={item.hint}
          size={size}
          styles={slot}
          style={item.span ? { gridColumn: `span ${item.span}` } : undefined}
          valueSize={valueSize}
        />
      ))}
    </div>
  );
}

export type FieldRowProps = {
  label: ReactNode;
  value: ReactNode;
  hint?: ReactNode;
  size?: 'sm' | 'md';
  className?: string;
  style?: CSSProperties;
  styles?: {
    label?: CSSProperties;
    value?: CSSProperties;
    hint?: CSSProperties;
  };
  /** Internal — pre-resolved value font size (so KvList can pass it once). */
  valueSize?: number;
};

export function FieldRow({
  label,
  value,
  hint,
  size = 'md',
  className,
  style,
  styles: slot,
  valueSize,
}: FieldRowProps) {
  const { token } = useToken();
  const resolvedValueSize = valueSize ?? (size === 'sm' ? 12 : 13);
  return (
    <div
      className={className}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-xxs)',
        minWidth: 0,
        ...style,
      }}
    >
      <span
        style={{
          fontSize: 10,
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          color: token.colorTextTertiary,
          fontWeight: 600,
          ...slot?.label,
        }}
      >
        {label}
      </span>
      <span
        style={{
          fontSize: resolvedValueSize,
          color: token.colorText,
          minWidth: 0,
          wordBreak: 'break-word',
          ...slot?.value,
        }}
      >
        {value}
      </span>
      {hint ? (
        <Text
          type="secondary"
          style={{ fontSize: 11, ...slot?.hint }}
        >
          {hint}
        </Text>
      ) : null}
    </div>
  );
}
