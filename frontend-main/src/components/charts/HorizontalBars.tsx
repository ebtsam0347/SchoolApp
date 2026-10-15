import { Button, Typography, theme as antdTheme } from 'antd';
import { useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';

import { ChartEmpty } from './ChartEmpty';
import { alphaMix, useChartTheme, useReducedMotion, type ChartTone } from './theme';

const { Text } = Typography;
const { useToken } = antdTheme;

export interface HorizontalBarsRow {
  /** Stable React key + accessible name. */
  id: string | number;
  label: ReactNode;
  /** Numeric metric. Sort + bar width derived from this. */
  value: number;
  /** Optional caption / sub-label rendered under the main label. */
  caption?: ReactNode;
  /** Override the row tone. Falls back to the prop-level `tone`. */
  tone?: ChartTone;
  /** Click target — the whole row becomes a router link. */
  href?: string;
}

export interface HorizontalBarsProps {
  rows: HorizontalBarsRow[];
  /** Default tone applied to rows without their own tone. Default 'primary'. */
  tone?: ChartTone;
  /** Sort order. Default 'desc' (largest first). */
  sort?: 'asc' | 'desc' | 'none';
  /**
   * Cap visible rows. The remainder hides behind a "Show all" toggle.
   * Pass `Infinity` to disable the cap. Default 6.
   */
  topN?: number;
  /** Format the trailing value label. Default `Intl.NumberFormat`. */
  valueFormatter?: (value: number) => string;
  /** Bar height in px. Default 16. */
  barHeight?: number;
  /** Cap the bar scale at this value (max bar = 100% width when value === scaleMax). */
  scaleMax?: number;
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
  className?: string;
  styles?: { root?: CSSProperties };
}

/**
 * Sortable horizontal bar chart. Better than a vertical bar when rows
 * have long labels (department names, employee names) — the label sits
 * outside the bar instead of squashing the axis.
 *
 * Renders as a list of bars with their values on the right, capped at
 * `topN` with an opt-in "show all" toggle. Each row can carry its own
 * tone for status-coloured rankings (e.g. red = leave hotspot, green
 * = healthy department).
 */
export function HorizontalBars({
  rows,
  tone = 'primary',
  sort = 'desc',
  topN = 6,
  valueFormatter,
  barHeight = 16,
  scaleMax,
  emptyTitle,
  emptyDescription,
  className,
  styles: slot,
}: HorizontalBarsProps) {
  const [expanded, setExpanded] = useState(false);

  if (!rows || rows.length === 0) {
    return (
      <div className={className} style={{ flex: 1, display: 'flex', ...slot?.root }}>
        <ChartEmpty title={emptyTitle} description={emptyDescription} />
      </div>
    );
  }

  const sorted =
    sort === 'asc'
      ? [...rows].sort((a, b) => a.value - b.value)
      : sort === 'desc'
        ? [...rows].sort((a, b) => b.value - a.value)
        : rows;

  const visible = expanded ? sorted : sorted.slice(0, topN);
  const overflow = sorted.length - visible.length;

  const max = scaleMax ?? Math.max(...sorted.map((r) => r.value), 1);
  const fmt = valueFormatter ?? defaultFormat;

  return (
    <div
      className={className}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
        width: '100%',
        ...slot?.root,
      }}
    >
      <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        {visible.map((row) => (
          <Row
            key={row.id}
            row={row}
            max={max}
            tone={row.tone ?? tone}
            barHeight={barHeight}
            valueFormatter={fmt}
          />
        ))}
      </ul>
      {overflow > 0 ? (
        <div style={{ display: 'flex', justifyContent: 'flex-end', paddingTop: 4 }}>
          <Button type="link" size="small" onClick={() => setExpanded(true)}>
            Show {overflow} more
          </Button>
        </div>
      ) : expanded && rows.length > topN ? (
        <div style={{ display: 'flex', justifyContent: 'flex-end', paddingTop: 4 }}>
          <Button type="link" size="small" onClick={() => setExpanded(false)}>
            Show top {topN}
          </Button>
        </div>
      ) : null}
    </div>
  );
}

function Row({
  row,
  max,
  tone,
  barHeight,
  valueFormatter,
}: {
  row: HorizontalBarsRow;
  max: number;
  tone: ChartTone;
  barHeight: number;
  valueFormatter: (v: number) => string;
}) {
  const { token } = useToken();
  const t = useChartTheme();
  const reducedMotion = useReducedMotion();
  const color = t.toneColor(tone);
  const pct = max > 0 ? Math.min(100, Math.max(0, (row.value / max) * 100)) : 0;

  const body = (
    <div
      style={{
        padding: '8px 4px',
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr) auto',
        rowGap: 4,
        columnGap: 12,
        alignItems: 'center',
      }}
    >
      <div style={{ minWidth: 0 }}>
        <div
          style={{
            fontSize: 13,
            fontWeight: 500,
            color: token.colorTextHeading,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {row.label}
        </div>
        {row.caption ? (
          <Text type="secondary" style={{ fontSize: 11 }}>
            {row.caption}
          </Text>
        ) : null}
      </div>
      <Text
        style={{
          fontSize: 13,
          fontWeight: 600,
          fontVariantNumeric: 'tabular-nums',
          color: token.colorTextHeading,
          textAlign: 'right',
        }}
      >
        {valueFormatter(row.value)}
      </Text>
      <div
        aria-hidden
        style={{
          gridColumn: '1 / -1',
          height: barHeight,
          background: token.colorFillTertiary,
          borderRadius: barHeight / 2,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            width: `${pct}%`,
            height: '100%',
            background: color,
            borderRadius: 'inherit',
            boxShadow: `0 0 0 1px ${alphaMix(color, 12)} inset`,
            transition: reducedMotion
              ? 'none'
              : 'width 360ms cubic-bezier(0.22, 0.61, 0.36, 1)',
          }}
        />
      </div>
    </div>
  );

  if (row.href) {
    return (
      <li>
        <a
          href={row.href}
          className="fs-focus-ring"
          style={{ color: 'inherit', textDecoration: 'none', display: 'block' }}
        >
          {body}
        </a>
      </li>
    );
  }
  return <li>{body}</li>;
}

function defaultFormat(value: number): string {
  return new Intl.NumberFormat('en-PK').format(value);
}
