// Heatmap-style alternate to MonthGrid. Each day tile is filled with a
// translucent tint of its status color, so the month scans visually
// (greens = present, reds = absent, blues = leave) without requiring
// the eye to read text on every tile. The day number stays prominent
// for navigation. Hover/click open the same DayDetail drawer.
//
// Pairs with MonthGrid via a `view` toggle on /time/attendance — same
// underlying DayCell[], just a denser visual.

import { Tooltip, theme as antdTheme } from 'antd';
import {
  ATTENDANCE_STATUS_TONE,
  parseDateOnly,
  prettyLeaveCode,
  toIsoDate,
} from '@/attendance';
import type { DayCell } from './types';

const { useToken } = antdTheme;

const WEEKDAY_HEADER = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

export type MonthHeatmapProps = {
  days: DayCell[];
  onSelect: (workDate: string) => void;
  loading: boolean;
};

export function MonthHeatmap({ days, onSelect, loading }: MonthHeatmapProps) {
  const { token } = useToken();

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        opacity: loading ? 0.6 : 1,
        transition: 'opacity var(--app-transition-normal)',
      }}
    >
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 6 }}>
        {WEEKDAY_HEADER.map((d) => (
          <div
            key={d}
            style={{
              fontSize: 11,
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
              color: token.colorTextTertiary,
              fontWeight: 600,
              textAlign: 'center',
              padding: '0 2px',
            }}
          >
            {d}
          </div>
        ))}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 6 }}>
        {days.map((d) => (
          <HeatCell key={d.workDate} day={d} onClick={() => onSelect(d.workDate)} />
        ))}
      </div>
      <Legend />
    </div>
  );
}

function HeatCell({ day, onClick }: { day: DayCell; onClick: () => void }) {
  const { token } = useToken();
  const today = toIsoDate(new Date());
  const isToday = day.workDate === today;
  const isInteractive = day.inMonth && day.kind !== 'FUTURE';

  const tone =
    day.kind === 'FUTURE' || day.kind === 'OUT'
      ? 'neutral'
      : ATTENDANCE_STATUS_TONE[day.kind];

  const toneColor =
    tone === 'success'
      ? token.colorSuccess
      : tone === 'error'
        ? token.colorError
        : tone === 'warning'
          ? token.colorWarning
          : tone === 'info'
            ? token.colorInfo
            : tone === 'primary'
              ? token.colorPrimary
              : token.colorTextTertiary;

  const dayNumber = parseDateOnly(day.workDate)!.getDate();
  const filled =
    day.kind !== 'FUTURE' && day.kind !== 'OUT' && day.kind !== 'WEEKEND';

  const tooltipBody = day.holiday
    ? day.holiday.name
    : day.kind === 'WEEKEND'
      ? 'Weekly off'
      : day.leave
        ? `${prettyLeaveCode(day.leave.leaveTypeCode)}${day.leave.halfDay ? ` (½ ${day.leave.halfDayPart ?? ''})` : ''}`
        : day.kind === 'ABSENT'
          ? 'Absent'
          : day.kind === 'PRESENT'
            ? 'Present'
            : day.kind === 'HALF_DAY'
              ? 'Half day'
              : day.kind === 'FUTURE'
                ? 'Upcoming'
                : null;

  const cell = (
    <button
      type="button"
      onClick={onClick}
      disabled={!isInteractive}
      aria-disabled={!isInteractive}
      aria-label={`${day.workDate}${tooltipBody ? ` — ${tooltipBody}` : ''}`}
      style={{
        all: 'unset',
        cursor: isInteractive ? 'pointer' : 'default',
        position: 'relative',
        aspectRatio: '1 / 1',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: 'var(--app-radius-md)',
        background: filled
          ? `color-mix(in srgb, ${toneColor} 22%, ${token.colorBgContainer})`
          : day.inMonth
            ? day.kind === 'FUTURE'
              ? `color-mix(in srgb, ${token.colorBgContainer} 60%, transparent)`
              : token.colorBgContainer
            : 'transparent',
        border: `1px solid ${
          isToday
            ? token.colorPrimary
            : day.inMonth
              ? token.colorBorderSecondary
              : 'transparent'
        }`,
        boxShadow: isToday
          ? `0 0 0 2px color-mix(in srgb, ${token.colorPrimary} 22%, transparent)`
          : undefined,
        opacity: !day.inMonth ? 0.3 : day.kind === 'FUTURE' ? 0.45 : 1,
        transition: 'transform var(--app-transition-fast), filter var(--app-transition-fast)',
        color: filled ? token.colorTextHeading : token.colorText,
      }}
      onMouseEnter={(e) => {
        if (isInteractive) {
          e.currentTarget.style.transform = 'scale(1.04)';
          e.currentTarget.style.filter = 'brightness(1.05)';
        }
      }}
      onMouseLeave={(e) => {
        if (isInteractive) {
          e.currentTarget.style.transform = '';
          e.currentTarget.style.filter = '';
        }
      }}
    >
      <span style={{ fontSize: 13, fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
        {dayNumber}
      </span>
      {day.record && day.record.lateMinutes > 0 ? (
        <span
          aria-hidden
          style={{
            position: 'absolute',
            bottom: 4,
            right: 4,
            width: 5,
            height: 5,
            borderRadius: '50%',
            background: token.colorWarning,
          }}
        />
      ) : null}
    </button>
  );

  return tooltipBody && isInteractive ? (
    <Tooltip title={tooltipBody} mouseEnterDelay={0.2} placement="top">
      {cell}
    </Tooltip>
  ) : (
    cell
  );
}

function Legend() {
  const { token } = useToken();
  const items: Array<{ tone: string; color: string; label: string }> = [
    { tone: 'success', color: token.colorSuccess, label: 'Present' },
    { tone: 'error', color: token.colorError, label: 'Absent' },
    { tone: 'info', color: token.colorInfo, label: 'Leave' },
    { tone: 'primary', color: token.colorPrimary, label: 'Holiday' },
    { tone: 'warning', color: token.colorWarning, label: 'Late dot' },
  ];
  return (
    <div
      style={{
        display: 'flex',
        gap: 14,
        flexWrap: 'wrap',
        alignItems: 'center',
        marginTop: 4,
        paddingTop: 4,
      }}
    >
      {items.map((it) => (
        <span
          key={it.tone}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            fontSize: 11,
            color: token.colorTextSecondary,
          }}
        >
          <span
            style={{
              width: 10,
              height: 10,
              borderRadius: 2,
              background:
                it.tone === 'warning'
                  ? it.color
                  : `color-mix(in srgb, ${it.color} 22%, ${token.colorBgContainer})`,
              border: `1px solid color-mix(in srgb, ${it.color} 35%, ${token.colorBorderSecondary})`,
            }}
          />
          {it.label}
        </span>
      ))}
    </div>
  );
}
