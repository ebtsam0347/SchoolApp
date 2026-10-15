// Drawer body for a single day on the month grid: status pill,
// holiday/leave tags, the record's KV facts, the day's punch timeline,
// and a "Regularize this day" CTA. Future days have the CTA disabled.

import { useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Tag,
  theme as antdTheme,
  Typography,
} from 'antd';
import { useQuery } from '@tanstack/react-query';
import { FileEdit, LogIn, LogOut } from 'lucide-react';
import {
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_TONE,
  PUNCH_SOURCE_LABEL,
  attendanceApi,
  formatClock,
  formatClockOn,
  formatMinutes,
  formatShiftRange,
  prettyLeaveCode,
} from '@/attendance';
import { KvList, Section, StatusPill, TimelineItem } from '@/components';
import { RegularizeDrawer } from './RegularizeDrawer';
import type { DayCell } from './types';

const { Text } = Typography;
const { useToken } = antdTheme;

export type DayDetailProps = {
  workDate: string;
  day: DayCell | null;
  onClose: () => void;
};

export function DayDetail({ workDate, day, onClose }: DayDetailProps) {
  const { token } = useToken();
  const [showRegularize, setShowRegularize] = useState(false);

  const punchesQuery = useQuery({
    queryKey: ['attendance', 'me', 'punches', workDate],
    queryFn: ({ signal }) => attendanceApi.myPunches(workDate, signal),
  });

  const punches = punchesQuery.data ?? [];
  const sorted = punches.slice().sort((a, b) => a.punchedAt.localeCompare(b.punchedAt));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 12,
        }}
      >
        <StatusPill
          tone={
            day && day.kind !== 'FUTURE' && day.kind !== 'OUT'
              ? ATTENDANCE_STATUS_TONE[day.kind]
              : 'neutral'
          }
          size="md"
        >
          {day && day.kind !== 'FUTURE' && day.kind !== 'OUT'
            ? ATTENDANCE_STATUS_LABEL[day.kind]
            : 'Future date'}
        </StatusPill>
        {day?.holiday ? <Tag color="red">{day.holiday.name}</Tag> : null}
        {day?.leave ? (
          <Tag color="blue">
            {prettyLeaveCode(day.leave.leaveTypeCode)}
            {day.leave.halfDay ? ` · ½ ${day.leave.halfDayPart ?? ''}` : ''}
          </Tag>
        ) : null}
      </header>

      {day?.record ? (
        <Card size="small" styles={{ body: { padding: 14 } }}>
          <KvList
            items={[
              { label: 'Check-in', value: formatClock(day.record.checkIn) },
              { label: 'Check-out', value: formatClockOn(day.record.checkOut, day.record.workDate) },
              { label: 'Worked', value: formatMinutes(day.record.workedMinutes) },
              { label: 'Late', value: formatMinutes(day.record.lateMinutes) },
              {
                label: 'Shift',
                value: formatShiftRange(
                  day.record.shiftStartTime,
                  day.record.shiftEndTime,
                ),
              },
              {
                label: 'OT',
                value: formatMinutes(
                  day.record.otNormalMinutes +
                    day.record.otExtraMinutes +
                    day.record.otSpecialMinutes,
                ),
              },
            ]}
            minColumnWidth={140}
            gap={10}
            size="sm"
          />
        </Card>
      ) : null}

      <Section title="Punches" level={5}>
        {punchesQuery.isLoading ? (
          <Text type="secondary">Loading…</Text>
        ) : sorted.length === 0 ? (
          <Empty description="No punches for this day" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
            {sorted.map((p, i) => (
              <TimelineItem
                key={p.id}
                isLast={i === sorted.length - 1}
                tone={
                  p.source === 'OPERATOR' || p.source === 'REGULARIZED'
                    ? 'warning'
                    : 'success'
                }
                icon={i % 2 === 0 ? LogIn : LogOut}
                title={
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    <strong>{i % 2 === 0 ? 'In' : 'Out'}</strong>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {formatClock(p.punchedAt)}
                    </Text>
                    <Tag style={{ margin: 0, fontSize: 10 }}>
                      {PUNCH_SOURCE_LABEL[p.source]}
                    </Tag>
                  </span>
                }
                description={p.reason ?? undefined}
              />
            ))}
          </div>
        )}
      </Section>

      <div
        style={{
          display: 'flex',
          gap: 8,
          padding: '12px 0',
          borderTop: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <Button
          type="primary"
          icon={<FileEdit size={14} />}
          onClick={() => setShowRegularize(true)}
          disabled={day?.kind === 'FUTURE'}
        >
          Regularize this day
        </Button>
        <Button onClick={onClose}>Close</Button>
      </div>

      <RegularizeDrawer
        open={showRegularize}
        workDate={workDate}
        existingPunches={sorted}
        onClose={() => setShowRegularize(false)}
        onDone={() => {
          setShowRegularize(false);
          punchesQuery.refetch();
        }}
      />
    </div>
  );
}
