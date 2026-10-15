import { Tooltip, Typography, theme as antdTheme } from 'antd';
import type { CSSProperties, ReactNode } from 'react';

import { parseDateOnly, toIsoDate } from '@/lib/dates';

import { ChartEmpty } from './ChartEmpty';
import { alphaMix, type ChartTone } from './theme';

const { Text } = Typography;
const { useToken } = antdTheme;

export interface LeaveCalendarItem {
  /** Stable key for React. */
  id: string | number;
  personId: string | number;
  personName: string;
  /** ISO date YYYY-MM-DD, inclusive. */
  start: string;
  /** ISO date YYYY-MM-DD, inclusive. */
  end: string;
  tone?: ChartTone;
  /** Short label rendered inside the bar (leave type, hours, etc). */
  label?: string;
  /** Long-form tooltip. Falls back to label + range. */
  tooltip?: ReactNode;
}

export interface LeaveCalendarPerson {
  id: string | number;
  name: string;
  /** Optional photo URL. */
  src?: string;
}

export interface LeaveCalendarStripProps {
  /** Range start (inclusive). ISO date. */
  from: string;
  /** Range end (inclusive). ISO date. */
  to: string;
  items: LeaveCalendarItem[];
  /** Override row order. Default: alphabetised from items + any explicit people. */
  people?: LeaveCalendarPerson[];
  /** Person column width. Default 160. */
  personColumnWidth?: number;
  /** Row height. Default 28. */
  rowHeight?: number;
  /** Show day-of-month tick every N days. Default 5. */
  tickEvery?: number;
  /** Highlight today's column. Default true. */
  highlightToday?: boolean;
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
  className?: string;
  styles?: { root?: CSSProperties };
}

/**
 * Gantt-style leave calendar. Rows = people, columns = days within the
 * range. Each item spans its [start,end] range as a tone-coloured bar.
 *
 * Date math is local-noon (via `parseDateOnly`) so DST never shifts a
 * leave window by a day.
 */
export function LeaveCalendarStrip({
  from,
  to,
  items,
  people,
  personColumnWidth = 160,
  rowHeight = 28,
  tickEvery = 5,
  highlightToday = true,
  emptyTitle,
  emptyDescription,
  className,
  styles: slot,
}: LeaveCalendarStripProps) {
  const { token } = useToken();

  const fromDate = parseDateOnly(from);
  const toDate = parseDateOnly(to);
  const todayIso = toIsoDate(new Date());

  if (!fromDate || !toDate || toDate < fromDate) {
    return (
      <div className={className} style={{ flex: 1, display: 'flex', ...slot?.root }}>
        <ChartEmpty title="Invalid date range" description="`from` must be ≤ `to`." />
      </div>
    );
  }

  const days: { iso: string; date: Date; weekday: number; dayOfMonth: number }[] = [];
  for (let d = new Date(fromDate); d <= toDate; d.setDate(d.getDate() + 1)) {
    days.push({
      iso: toIsoDate(d),
      date: new Date(d),
      weekday: d.getDay(),
      dayOfMonth: d.getDate(),
    });
  }

  // Resolve people order: explicit > unique-from-items, alphabetised.
  const peopleResolved: LeaveCalendarPerson[] = (() => {
    if (people && people.length > 0) return people;
    const seen = new Map<string | number, LeaveCalendarPerson>();
    for (const it of items) {
      if (!seen.has(it.personId)) {
        seen.set(it.personId, { id: it.personId, name: it.personName });
      }
    }
    return Array.from(seen.values()).sort((a, b) => a.name.localeCompare(b.name));
  })();

  if (peopleResolved.length === 0) {
    return (
      <div className={className} style={{ flex: 1, display: 'flex', ...slot?.root }}>
        <ChartEmpty
          title={emptyTitle ?? 'Nothing scheduled'}
          description={
            emptyDescription ?? 'No leave overlaps with this range — quiet stretch ahead.'
          }
        />
      </div>
    );
  }

  const personIndex = new Map<string | number, number>();
  peopleResolved.forEach((p, i) => personIndex.set(p.id, i));

  // For each item, clamp its date range into [from, to], compute startCol/endCol.
  const positionedItems = items
    .map((it) => {
      const s = parseDateOnly(it.start);
      const e = parseDateOnly(it.end);
      if (!s || !e || e < fromDate || s > toDate) return null;
      const clampedStart = s < fromDate ? fromDate : s;
      const clampedEnd = e > toDate ? toDate : e;
      const startIndex = Math.round(
        (clampedStart.getTime() - fromDate.getTime()) / (1000 * 60 * 60 * 24),
      );
      const endIndex = Math.round(
        (clampedEnd.getTime() - fromDate.getTime()) / (1000 * 60 * 60 * 24),
      );
      const personIdx = personIndex.get(it.personId);
      if (personIdx === undefined) return null;
      return {
        ...it,
        startCol: startIndex + 2, // +1 for 1-indexed grid, +1 for the person column
        spanCols: endIndex - startIndex + 1,
        personIdx,
      };
    })
    .filter((x): x is NonNullable<typeof x> => x !== null);

  const toneFor = (tone: ChartTone | undefined): string => {
    switch (tone) {
      case 'success':
        return token.colorSuccess;
      case 'warning':
        return token.colorWarning;
      case 'error':
        return token.colorError;
      case 'info':
        return token.colorInfo;
      case 'neutral':
        return token.colorTextTertiary;
      default:
        return token.colorPrimary;
    }
  };

  const headerHeight = 28;
  const totalRows = peopleResolved.length;

  return (
    <div
      className={className}
      style={{
        position: 'relative',
        width: '100%',
        flex: 1,
        overflowX: 'auto',
        ...slot?.root,
      }}
    >
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `${personColumnWidth}px repeat(${days.length}, minmax(18px, 1fr))`,
          gridTemplateRows: `${headerHeight}px repeat(${totalRows}, ${rowHeight}px)`,
          minWidth: personColumnWidth + days.length * 18,
          rowGap: 4,
        }}
      >
        {/* Top-left corner spacer */}
        <div
          style={{
            gridRow: 1,
            gridColumn: 1,
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
          }}
        />

        {/* Day header cells */}
        {days.map((d, i) => {
          const isWeekend = d.weekday === 0 || d.weekday === 6;
          const showTick = i === 0 || (d.dayOfMonth % tickEvery === 0);
          const isToday = highlightToday && d.iso === todayIso;
          return (
            <div
              key={`hdr-${d.iso}`}
              style={{
                gridRow: 1,
                gridColumn: i + 2,
                fontSize: 10,
                color: isToday ? token.colorPrimary : token.colorTextTertiary,
                fontWeight: isToday ? 600 : 400,
                textAlign: 'center',
                lineHeight: `${headerHeight}px`,
                borderBottom: `1px solid ${token.colorBorderSecondary}`,
                background: isWeekend ? alphaMix(token.colorTextTertiary, 4) : 'transparent',
              }}
            >
              {showTick ? d.dayOfMonth : ''}
            </div>
          );
        })}

        {/* Person rows: name column + day backgrounds */}
        {peopleResolved.map((p, pIdx) => (
          <PersonRowCells
            key={`row-${p.id}`}
            person={p}
            rowIndex={pIdx + 2}
            days={days}
            personColumnWidth={personColumnWidth}
            highlightToday={highlightToday}
            todayIso={todayIso}
            token={token}
          />
        ))}

        {/* Today marker overlay */}
        {highlightToday
          ? days.map((d, i) =>
              d.iso === todayIso ? (
                <div
                  key={`today-${d.iso}`}
                  aria-hidden
                  style={{
                    gridRow: `2 / span ${totalRows}`,
                    gridColumn: i + 2,
                    background: alphaMix(token.colorPrimary, 6),
                    pointerEvents: 'none',
                  }}
                />
              ) : null,
            )
          : null}

        {/* Item bars */}
        {positionedItems.map((it) => {
          const tone = toneFor(it.tone);
          const tipBody = it.tooltip ?? (
            <span>
              {it.label ?? 'Leave'} · {formatRange(it.start, it.end)}
            </span>
          );
          return (
            <Tooltip key={`bar-${it.id}`} title={tipBody} mouseEnterDelay={0.1}>
              <div
                style={{
                  gridRow: it.personIdx + 2,
                  gridColumn: `${it.startCol} / span ${it.spanCols}`,
                  margin: '4px 1px',
                  background: alphaMix(tone, 22),
                  borderLeft: `3px solid ${tone}`,
                  borderRadius: 4,
                  display: 'flex',
                  alignItems: 'center',
                  paddingLeft: 6,
                  fontSize: 11,
                  fontWeight: 500,
                  color: token.colorTextHeading,
                  overflow: 'hidden',
                  whiteSpace: 'nowrap',
                  textOverflow: 'ellipsis',
                  cursor: 'default',
                }}
              >
                {it.label ?? ''}
              </div>
            </Tooltip>
          );
        })}
      </div>
    </div>
  );
}

function PersonRowCells({
  person,
  rowIndex,
  days,
  personColumnWidth,
  highlightToday,
  todayIso,
  token,
}: {
  person: LeaveCalendarPerson;
  rowIndex: number;
  days: { iso: string; weekday: number }[];
  personColumnWidth: number;
  highlightToday: boolean;
  todayIso: string;
  token: ReturnType<typeof antdTheme.useToken>['token'];
}) {
  return (
    <>
      <div
        style={{
          gridRow: rowIndex,
          gridColumn: 1,
          width: personColumnWidth,
          display: 'flex',
          alignItems: 'center',
          paddingRight: 12,
          minWidth: 0,
        }}
      >
        <Text
          style={{
            fontSize: 12,
            fontWeight: 500,
            color: token.colorTextHeading,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {person.name}
        </Text>
      </div>
      {days.map((d, i) => {
        const isWeekend = d.weekday === 0 || d.weekday === 6;
        const isToday = highlightToday && d.iso === todayIso;
        return (
          <div
            key={`bg-${person.id}-${d.iso}`}
            aria-hidden
            style={{
              gridRow: rowIndex,
              gridColumn: i + 2,
              background: isWeekend
                ? alphaMix(token.colorTextTertiary, 5)
                : isToday
                  ? 'transparent'
                  : 'transparent',
              borderRadius: 2,
            }}
          />
        );
      })}
    </>
  );
}

function formatRange(start: string, end: string): string {
  const s = parseDateOnly(start);
  const e = parseDateOnly(end);
  if (!s || !e) return `${start} – ${end}`;
  const fmt = new Intl.DateTimeFormat('en-PK', { day: 'numeric', month: 'short' });
  if (toIsoDate(s) === toIsoDate(e)) return fmt.format(s);
  return `${fmt.format(s)} → ${fmt.format(e)}`;
}
