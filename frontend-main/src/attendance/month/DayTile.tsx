// One cell in the month grid. Shows the day number, status dot, and a
// brief copy line (holiday name / leave type / punch range / "Absent").
// Non-month padding cells are non-interactive and dimmed.

import { Typography, theme as antdTheme } from 'antd';
import {
  ATTENDANCE_STATUS_TONE,
  formatClock,
  formatClockOn,
  formatMinutes,
  parseDateOnly,
  prettyLeaveCode,
  toIsoDate,
} from '@/attendance';
import type { DayCell } from './types';

const { Text } = Typography;
const { useToken } = antdTheme;

export function DayTile({ day, onClick }: { day: DayCell; onClick: () => void }) {
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
  const otTotal = day.record
    ? day.record.otNormalMinutes +
      day.record.otExtraMinutes +
      day.record.otSpecialMinutes
    : 0;

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={!isInteractive}
      aria-disabled={!isInteractive}
      style={{
        all: 'unset',
        cursor: isInteractive ? 'pointer' : 'default',
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        padding: 10,
        borderRadius: 'var(--app-radius-md)',
        background: day.inMonth
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
          ? `0 0 0 2px color-mix(in srgb, ${token.colorPrimary} 18%, transparent)`
          : undefined,
        opacity: !day.inMonth ? 0.3 : day.kind === 'FUTURE' ? 0.45 : 1,
        minHeight: 88,
        transition: 'transform var(--app-transition-fast), box-shadow var(--app-transition-fast)',
      }}
      onMouseEnter={(e) => {
        if (isInteractive) e.currentTarget.style.transform = 'translateY(-1px)';
      }}
      onMouseLeave={(e) => {
        if (isInteractive) e.currentTarget.style.transform = 'translateY(0)';
      }}
    >
      <header
        style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
      >
        <span
          style={{
            fontSize: 14,
            fontWeight: 600,
            color: isToday ? token.colorPrimary : token.colorText,
          }}
        >
          {dayNumber}
        </span>
        <span
          aria-hidden
          style={{
            width: 6,
            height: 6,
            borderRadius: 'var(--app-radius-full)',
            background:
              day.kind === 'FUTURE' || day.kind === 'OUT' ? 'transparent' : toneColor,
          }}
        />
      </header>
      <div
        style={{
          flex: 1,
          minHeight: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 3,
        }}
      >
        {day.holiday ? (
          <Text style={{ fontSize: 11, color: token.colorPrimary, fontWeight: 500 }}>
            {day.holiday.name}
          </Text>
        ) : day.kind === 'WEEKEND' ? (
          <Text style={{ fontSize: 11, color: token.colorTextTertiary }}>Weekly off</Text>
        ) : day.leave ? (
          <Text style={{ fontSize: 11, color: token.colorInfo, fontWeight: 500 }}>
            {prettyLeaveCode(day.leave.leaveTypeCode)}
            {day.leave.halfDay ? ` (½ ${day.leave.halfDayPart ?? ''})` : ''}
          </Text>
        ) : day.kind === 'LEAVE' ? (
          <Text style={{ fontSize: 11, color: token.colorInfo, fontWeight: 500 }}>
            On leave
          </Text>
        ) : day.record?.status === 'PRESENT' ? (
          <Text style={{ fontSize: 11, color: token.colorTextSecondary }}>
            {formatClock(day.record.checkIn)} – {formatClockOn(day.record.checkOut, day.record.workDate)}
          </Text>
        ) : day.record?.status === 'ABSENT' || day.kind === 'ABSENT' ? (
          <Text style={{ fontSize: 11, color: token.colorError, fontWeight: 500 }}>
            Absent
          </Text>
        ) : day.kind === 'FUTURE' ? (
          <Text style={{ fontSize: 11, color: token.colorTextTertiary }}>—</Text>
        ) : null}

        {otTotal > 0 ? (
          <Text style={{ fontSize: 10, color: token.colorInfo, fontWeight: 500 }}>
            +{formatMinutes(otTotal)} OT
          </Text>
        ) : null}
        {day.record && day.record.lateMinutes > 0 ? (
          <Text style={{ fontSize: 10, color: token.colorWarning, fontWeight: 500 }}>
            {formatMinutes(day.record.lateMinutes)} late
          </Text>
        ) : null}
      </div>
    </button>
  );
}
