import { Tooltip, theme as antdTheme, Typography } from 'antd';
import type { CSSProperties, ReactNode } from 'react';

import { toIsoDate } from '@/lib/dates';

import { alphaMix, useReducedMotion, type ChartTone } from './theme';

const { Text } = Typography;
const { useToken } = antdTheme;

export type AttendanceHeatmapStatus =
  | 'PRESENT'
  | 'LATE'
  | 'ABSENT'
  | 'ON_LEAVE'
  | 'OFF'
  | 'HOLIDAY';

export interface AttendanceHeatmapDay {
  /** ISO date YYYY-MM-DD. */
  date: string;
  /** Categorical state for the cell. Drives colour when no `value`. */
  status?: AttendanceHeatmapStatus;
  /**
   * Continuous metric — minutes of variance vs shift start. Positive =
   * late, negative = early. Drives the diverging scale colour.
   */
  varianceMin?: number | null;
  /** Check-in time, surfaced in the tooltip. */
  checkInAt?: string | null;
  /** Free-form note for the tooltip (e.g. "Work from home"). */
  note?: ReactNode;
}

export interface AttendanceHeatmapProps {
  /** Month to render in YYYY-MM. */
  month: string;
  /** Per-day data; missing days render with no fill. */
  data: AttendanceHeatmapDay[];
  /**
   * Colour scale strategy.
   * - 'diverging' (default): cool for early, warm for late, neutral on time.
   * - 'sequential': single tone, intensity = absolute lateness.
   * - 'status': tone purely from `status` field.
   */
  scale?: 'diverging' | 'sequential' | 'status';
  /** Week start. Default 1 (Mon). */
  weekStartsOn?: 0 | 1;
  /** Cell size in px. Default 36. */
  cellSize?: number;
  /** Cell gap in px. Default 4. */
  cellGap?: number;
  /** Highlight today. Default true. */
  highlightToday?: boolean;
  /** Render the legend below the grid. Default true. */
  showLegend?: boolean;
  /** Custom tooltip renderer. Falls back to a default summary line. */
  tooltip?: (day: AttendanceHeatmapDay) => ReactNode;
  className?: string;
  styles?: { root?: CSSProperties };
}

const WEEK_LABELS_MON = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const WEEK_LABELS_SUN = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/**
 * Calendar-month attendance heatmap. 7-column grid, 4–6 rows of weeks,
 * cells coloured by punch-in variance. Surfaces "drifting later" as a
 * gradient that's obvious at a glance.
 */
export function AttendanceHeatmap({
  month,
  data,
  scale = 'diverging',
  weekStartsOn = 1,
  cellSize = 36,
  cellGap = 4,
  highlightToday = true,
  showLegend = true,
  tooltip,
  className,
  styles: slot,
}: AttendanceHeatmapProps) {
  const { token } = useToken();
  const reducedMotion = useReducedMotion();

  // Build the grid of day cells for `month`. We pad before the 1st with
  // empty cells so the calendar columns line up to the chosen week-start.
  const monthMatch = /^(\d{4})-(\d{2})$/.exec(month);
  if (!monthMatch) return null;
  const [, ys, ms] = monthMatch;
  const year = Number(ys);
  const monthIdx = Number(ms) - 1;
  const firstOfMonth = new Date(year, monthIdx, 1, 12, 0, 0);
  const daysInMonth = new Date(year, monthIdx + 1, 0).getDate();

  const firstWeekday = firstOfMonth.getDay(); // 0=Sun,…6=Sat
  const offset = (firstWeekday - weekStartsOn + 7) % 7;

  const dataByIso = new Map<string, AttendanceHeatmapDay>();
  for (const d of data) dataByIso.set(d.date, d);

  type Cell =
    | { kind: 'pad'; key: string }
    | {
        kind: 'day';
        key: string;
        date: Date;
        iso: string;
        dayOfMonth: number;
        weekday: number;
        day: AttendanceHeatmapDay | undefined;
      };

  const cells: Cell[] = [];
  for (let i = 0; i < offset; i += 1) {
    cells.push({ kind: 'pad', key: `pad-pre-${i}` });
  }
  for (let d = 1; d <= daysInMonth; d += 1) {
    const date = new Date(year, monthIdx, d, 12, 0, 0);
    const iso = toIsoDate(date);
    cells.push({
      kind: 'day',
      key: iso,
      date,
      iso,
      dayOfMonth: d,
      weekday: date.getDay(),
      day: dataByIso.get(iso),
    });
  }
  // Pad to a multiple of 7 so grid rows close cleanly.
  while (cells.length % 7 !== 0) {
    cells.push({ kind: 'pad', key: `pad-post-${cells.length}` });
  }

  const todayIso = toIsoDate(new Date());

  const weekLabels = weekStartsOn === 1 ? WEEK_LABELS_MON : WEEK_LABELS_SUN;

  const fillFor = (cell: Extract<Cell, { kind: 'day' }>): {
    bg: string;
    border: string;
    fg: string;
    label: string;
  } => {
    const { day, weekday, iso } = cell;
    const isFuture = iso > todayIso;
    const isWeekend = weekday === 0 || weekday === 6;

    if (isFuture) {
      return {
        bg: 'transparent',
        border: token.colorBorderSecondary,
        fg: token.colorTextTertiary,
        label: 'Upcoming',
      };
    }

    if (!day || day.status === 'OFF') {
      return {
        bg: isWeekend ? alphaMix(token.colorTextTertiary, 6) : 'transparent',
        border: token.colorBorderSecondary,
        fg: token.colorTextTertiary,
        label: isWeekend ? 'Off · weekend' : 'No data',
      };
    }

    if (scale === 'status' || day.status === 'ABSENT' || day.status === 'ON_LEAVE' || day.status === 'HOLIDAY') {
      const tone: ChartTone =
        day.status === 'ABSENT'
          ? 'error'
          : day.status === 'ON_LEAVE'
            ? 'info'
            : day.status === 'HOLIDAY'
              ? 'neutral'
              : day.status === 'LATE'
                ? 'warning'
                : day.status === 'PRESENT'
                  ? 'success'
                  : 'neutral';
      const color = toneColor(tone, token);
      return {
        bg: alphaMix(color, 22),
        border: alphaMix(color, 36),
        fg: token.colorTextHeading,
        label: day.status ? statusLabel(day.status) : 'No data',
      };
    }

    // Continuous: choose tone + intensity by variance.
    const v = day.varianceMin ?? 0;
    const { tone, intensity, label } = bucketise(v, scale);
    const color = toneColor(tone, token);
    return {
      bg: alphaMix(color, intensity),
      border: alphaMix(color, intensity + 14),
      fg: token.colorTextHeading,
      label,
    };
  };

  return (
    <div
      className={className}
      style={{
        width: '100%',
        ...slot?.root,
      }}
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(7, minmax(${cellSize}px, 1fr))`,
          gap: cellGap,
          marginBottom: cellGap,
        }}
      >
        {weekLabels.map((label) => (
          <div
            key={label}
            style={{
              fontSize: 11,
              fontWeight: 500,
              letterSpacing: '0.06em',
              textTransform: 'uppercase',
              color: token.colorTextTertiary,
              textAlign: 'center',
              paddingBottom: 2,
            }}
          >
            {label}
          </div>
        ))}
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(7, minmax(${cellSize}px, 1fr))`,
          gap: cellGap,
        }}
      >
        {cells.map((cell) => {
          if (cell.kind === 'pad') {
            return <div key={cell.key} aria-hidden style={{ height: cellSize }} />;
          }
          const { bg, border, fg, label } = fillFor(cell);
          const isToday = highlightToday && cell.iso === todayIso;
          const tipBody =
            tooltip && cell.day
              ? tooltip(cell.day)
              : (
                  <span style={{ display: 'flex', flexDirection: 'column' }}>
                    <span style={{ fontWeight: 600 }}>
                      {cell.date.toLocaleDateString('en-PK', {
                        weekday: 'short',
                        day: 'numeric',
                        month: 'short',
                      })}
                    </span>
                    <span>{label}</span>
                    {cell.day?.checkInAt ? (
                      <span style={{ opacity: 0.85 }}>
                        Checked in {cell.day.checkInAt}
                      </span>
                    ) : null}
                    {cell.day?.note ? (
                      <span style={{ opacity: 0.85 }}>{cell.day.note}</span>
                    ) : null}
                  </span>
                );

          return (
            <Tooltip key={cell.key} title={tipBody} mouseEnterDelay={0.1}>
              <div
                style={{
                  height: cellSize,
                  borderRadius: 6,
                  background: bg,
                  border: `1px solid ${border}`,
                  position: 'relative',
                  display: 'flex',
                  alignItems: 'flex-end',
                  justifyContent: 'flex-start',
                  padding: '4px 6px',
                  fontSize: 11,
                  fontWeight: 500,
                  color: fg,
                  cursor: 'default',
                  boxShadow: isToday
                    ? `0 0 0 2px ${token.colorPrimary}`
                    : undefined,
                  transition: reducedMotion ? 'none' : 'transform 120ms ease',
                }}
                onMouseEnter={
                  reducedMotion
                    ? undefined
                    : (e) => {
                        e.currentTarget.style.transform = 'scale(1.04)';
                      }
                }
                onMouseLeave={
                  reducedMotion
                    ? undefined
                    : (e) => {
                        e.currentTarget.style.transform = 'scale(1)';
                      }
                }
                aria-label={`${cell.iso} · ${label}`}
              >
                {cell.dayOfMonth}
              </div>
            </Tooltip>
          );
        })}
      </div>

      {showLegend ? (
        <div
          style={{
            marginTop: 12,
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <Text type="secondary" style={{ fontSize: 11 }}>
            {scale === 'status'
              ? 'Status'
              : scale === 'sequential'
                ? 'Lateness'
                : 'Variance vs shift start'}
          </Text>
          <LegendSwatches scale={scale} token={token} />
        </div>
      ) : null}
    </div>
  );
}

function LegendSwatches({
  scale,
  token,
}: {
  scale: 'diverging' | 'sequential' | 'status';
  token: ReturnType<typeof antdTheme.useToken>['token'];
}) {
  if (scale === 'status') {
    const items: { tone: ChartTone; label: string }[] = [
      { tone: 'success', label: 'Present' },
      { tone: 'warning', label: 'Late' },
      { tone: 'error', label: 'Absent' },
      { tone: 'info', label: 'On leave' },
      { tone: 'neutral', label: 'Holiday' },
    ];
    return (
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        {items.map((s) => (
          <Swatch key={s.label} color={toneColor(s.tone, token)} label={s.label} />
        ))}
      </div>
    );
  }
  if (scale === 'sequential') {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <Text type="secondary" style={{ fontSize: 11 }}>
          on time
        </Text>
        <Gradient from={alphaMix(token.colorWarning, 12)} to={alphaMix(token.colorError, 50)} />
        <Text type="secondary" style={{ fontSize: 11 }}>
          30+ min late
        </Text>
      </div>
    );
  }
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <Text type="secondary" style={{ fontSize: 11 }}>
        early
      </Text>
      <Gradient from={alphaMix(token.colorSuccess, 50)} to={alphaMix(token.colorTextTertiary, 14)} />
      <Text type="secondary" style={{ fontSize: 11 }}>
        on time
      </Text>
      <Gradient from={alphaMix(token.colorTextTertiary, 14)} to={alphaMix(token.colorError, 50)} />
      <Text type="secondary" style={{ fontSize: 11 }}>
        late
      </Text>
    </div>
  );
}

function Swatch({ color, label }: { color: string; label: string }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
      <span
        aria-hidden
        style={{
          width: 12,
          height: 12,
          borderRadius: 3,
          background: alphaMix(color, 22),
          border: `1px solid ${alphaMix(color, 36)}`,
        }}
      />
      <Text type="secondary" style={{ fontSize: 11 }}>
        {label}
      </Text>
    </span>
  );
}

function Gradient({ from, to }: { from: string; to: string }) {
  return (
    <span
      aria-hidden
      style={{
        display: 'inline-block',
        width: 64,
        height: 8,
        borderRadius: 4,
        background: `linear-gradient(to right, ${from}, ${to})`,
      }}
    />
  );
}

function bucketise(
  v: number,
  scale: 'diverging' | 'sequential',
): { tone: ChartTone; intensity: number; label: string } {
  if (scale === 'sequential') {
    if (v <= 0) return { tone: 'neutral', intensity: 8, label: 'On time' };
    if (v < 5) return { tone: 'warning', intensity: 12, label: `Late · +${v}m` };
    if (v < 15) return { tone: 'warning', intensity: 28, label: `Late · +${v}m` };
    if (v < 30) return { tone: 'warning', intensity: 44, label: `Late · +${v}m` };
    return { tone: 'error', intensity: 50, label: `Late · +${v}m` };
  }
  // Diverging
  if (v <= -15) return { tone: 'success', intensity: 38, label: `Early · ${v}m` };
  if (v < 0) return { tone: 'success', intensity: 20, label: `Early · ${v}m` };
  if (v === 0) return { tone: 'neutral', intensity: 10, label: 'On time' };
  if (v < 5) return { tone: 'warning', intensity: 16, label: `Late · +${v}m` };
  if (v < 15) return { tone: 'warning', intensity: 30, label: `Late · +${v}m` };
  if (v < 30) return { tone: 'warning', intensity: 44, label: `Late · +${v}m` };
  return { tone: 'error', intensity: 50, label: `Late · +${v}m` };
}

function toneColor(
  tone: ChartTone,
  token: ReturnType<typeof antdTheme.useToken>['token'],
): string {
  switch (tone) {
    case 'primary':
      return token.colorPrimary;
    case 'success':
      return token.colorSuccess;
    case 'warning':
      return token.colorWarning;
    case 'error':
      return token.colorError;
    case 'info':
      return token.colorInfo;
    case 'neutral':
    default:
      return token.colorTextTertiary;
  }
}

function statusLabel(s: AttendanceHeatmapStatus): string {
  switch (s) {
    case 'PRESENT':
      return 'Present';
    case 'LATE':
      return 'Late';
    case 'ABSENT':
      return 'Absent';
    case 'ON_LEAVE':
      return 'On leave';
    case 'OFF':
      return 'Off';
    case 'HOLIDAY':
      return 'Holiday';
  }
}
