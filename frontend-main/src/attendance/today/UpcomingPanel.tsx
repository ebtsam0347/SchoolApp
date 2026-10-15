// "Upcoming" panel — next holiday + next approved leave + a ⌘K hint.
// Zero state on either row gracefully shows "No holidays scheduled" /
// "No upcoming leave" so the panel layout never collapses.

import { Card, theme as antdTheme, Typography } from 'antd';
import { CalendarCheck, Plane, type LucideIcon } from 'lucide-react';
import { KbdHint, Section, StatusPill } from '@/components';
import {
  formatDateShort,
  prettyLeaveCode,
  type HolidayDto,
  type TodayShortcut,
} from '@/attendance';
import { daysUntil, prettyHolidayType } from './format';

const { Text } = Typography;
const { useToken } = antdTheme;

export type UpcomingPanelProps = {
  upcoming:
    | { nextHoliday: HolidayDto | null; nextApprovedLeave: TodayShortcut | null }
    | undefined;
};

export function UpcomingPanel({ upcoming }: UpcomingPanelProps) {
  const { token } = useToken();
  const nextHoliday = upcoming?.nextHoliday ?? null;
  const nextLeave = upcoming?.nextApprovedLeave ?? null;

  return (
    <Section title="Upcoming" subtitle="What's on your calendar next.">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <UpcomingRow
          icon={CalendarCheck}
          tone="primary"
          label="Next holiday"
          value={nextHoliday?.name ?? 'No holidays scheduled'}
          meta={
            nextHoliday
              ? `${formatDateShort(nextHoliday.holidayDate)} · ${prettyHolidayType(nextHoliday.type)}`
              : undefined
          }
          countdown={nextHoliday ? daysUntil(nextHoliday.holidayDate) : null}
        />
        <UpcomingRow
          icon={Plane}
          tone="info"
          label="Next approved leave"
          value={nextLeave ? prettyLeaveCode(nextLeave.leaveTypeCode) : 'No upcoming leave'}
          meta={
            nextLeave
              ? `${formatDateShort(nextLeave.fromDate)} → ${formatDateShort(nextLeave.toDate)}`
              : undefined
          }
          countdown={nextLeave ? daysUntil(nextLeave.fromDate) : null}
        />
        <Card
          size="small"
          styles={{ body: { padding: 14, display: 'flex', alignItems: 'center', gap: 12 } }}
          style={{ background: token.colorFillQuaternary, borderStyle: 'dashed' }}
        >
          <KbdHint keys={['⌘', 'K']} />
          <Text type="secondary" style={{ fontSize: 12, lineHeight: 1.5 }}>
            Quickly jump anywhere — search "leave", "overtime", or a date.
          </Text>
        </Card>
      </div>
    </Section>
  );
}

function UpcomingRow({
  icon: Icon,
  tone,
  label,
  value,
  meta,
  countdown,
}: {
  icon: LucideIcon;
  tone: 'primary' | 'info';
  label: string;
  value: string;
  meta?: string;
  countdown: number | null;
}) {
  const { token } = useToken();
  const toneColor = tone === 'primary' ? token.colorPrimary : token.colorInfo;
  return (
    <Card size="small" styles={{ body: { padding: 14 } }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <span
          aria-hidden
          style={{
            width: 36,
            height: 36,
            borderRadius: 10,
            background: `color-mix(in srgb, ${toneColor} 14%, transparent)`,
            color: toneColor,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <Icon size={16} strokeWidth={1.75} />
        </span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              fontSize: 11,
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
              color: token.colorTextTertiary,
              fontWeight: 600,
            }}
          >
            {label}
          </div>
          <div style={{ fontSize: 14, fontWeight: 600, color: token.colorText }}>
            {value}
          </div>
          {meta ? (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {meta}
            </Text>
          ) : null}
        </div>
        {countdown != null ? (
          <StatusPill tone={countdown <= 7 ? 'warning' : 'neutral'}>
            {countdown === 0
              ? 'Today'
              : countdown === 1
                ? 'Tomorrow'
                : `in ${countdown}d`}
          </StatusPill>
        ) : null}
      </div>
    </Card>
  );
}
