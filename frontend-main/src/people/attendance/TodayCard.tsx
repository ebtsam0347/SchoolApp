import { Card, theme as antdTheme, Typography } from 'antd';
import { Clock } from 'lucide-react';

import { StatusPill, type StatusPillTone } from '@/components';
import {
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_TONE,
  formatClock,
  formatClockOn,
  formatMinutes,
  formatShiftRange,
  type AttendanceRecordDto,
} from '@/attendance';

const { Text } = Typography;
const { useToken } = antdTheme;

export type TodayCardProps = {
  record: AttendanceRecordDto;
};

export function TodayCard({ record }: TodayCardProps) {
  const { token } = useToken();
  const tone = ATTENDANCE_STATUS_TONE[record.status];
  const otTotal =
    record.otNormalMinutes + record.otExtraMinutes + record.otSpecialMinutes;

  return (
    <Card
      styles={{
        body: {
          padding: 18,
          display: 'grid',
          gridTemplateColumns: 'auto 1fr auto',
          gap: 18,
          alignItems: 'center',
        },
      }}
    >
      <span
        aria-hidden
        style={{
          width: 56,
          height: 56,
          borderRadius: 14,
          background: `color-mix(in srgb, ${toneColor(token, tone)} 14%, transparent)`,
          color: toneColor(token, tone),
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        <Clock size={26} strokeWidth={1.75} />
      </span>
      <div
        style={{
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 6,
        }}
      >
        <div
          style={{
            display: 'flex',
            gap: 8,
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <StatusPill tone={tone} size="md">
            {ATTENDANCE_STATUS_LABEL[record.status]}
          </StatusPill>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Shift {formatShiftRange(record.shiftStartTime, record.shiftEndTime)}
          </Text>
        </div>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
            gap: 14,
            fontSize: 12,
          }}
        >
          <KV label="Check-in" value={formatClock(record.checkIn)} />
          <KV label="Check-out" value={formatClockOn(record.checkOut, record.workDate)} />
          <KV label="Worked" value={formatMinutes(record.workedMinutes)} />
          <KV
            label="Late"
            value={
              record.lateMinutes > 0
                ? formatMinutes(record.lateMinutes)
                : 'On time'
            }
            tone={record.lateMinutes > 0 ? 'warning' : 'default'}
          />
          <KV label="OT" value={formatMinutes(otTotal)} />
        </div>
      </div>
    </Card>
  );
}

function KV({
  label,
  value,
  tone = 'default',
}: {
  label: string;
  value: React.ReactNode;
  tone?: 'default' | 'warning';
}) {
  const { token } = useToken();
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <span
        style={{
          fontSize: 10,
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          color: token.colorTextTertiary,
          fontWeight: 600,
        }}
      >
        {label}
      </span>
      <span
        style={{
          fontSize: 13,
          color: tone === 'warning' ? token.colorWarning : token.colorText,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {value}
      </span>
    </div>
  );
}

function toneColor(
  token: ReturnType<typeof useToken>['token'],
  tone: StatusPillTone,
) {
  switch (tone) {
    case 'success':
      return token.colorSuccess;
    case 'error':
      return token.colorError;
    case 'warning':
      return token.colorWarning;
    case 'info':
      return token.colorInfo;
    case 'primary':
      return token.colorPrimary;
    default:
      return token.colorTextTertiary;
  }
}
