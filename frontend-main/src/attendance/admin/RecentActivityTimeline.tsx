// Compact "09:03 · Ahmed Ali · marked attendance" activity strip. Shares the
// page-owned punch query with LiveAttendanceFeed — this is a pure view over the
// first few rows, no extra fetch. Auto-updates whenever the punch query does.

import { Card, Skeleton, Typography } from 'antd';
import { LogIn } from 'lucide-react';

import { EmptyState, Section, TimelineItem } from '@/components';
import { formatClock, PUNCH_SOURCE_LABEL, type AttendancePunchesRow } from '@/attendance';
import { FlipNumber } from './FlipNumber';

const { Text } = Typography;

export type RecentActivityTimelineProps = {
  rows: AttendancePunchesRow[];
  loading: boolean;
  /** How many recent events to show. Default 8. */
  limit?: number;
  /**
   * Live running tally shown as a flip-counter in the header — the number of
   * attendance events so far today. Rolls up as fresh punches arrive. Omit to
   * hide the counter.
   */
  count?: number;
  /** Caption under/beside the counter. Default "marked today". */
  countLabel?: string;
};

export function RecentActivityTimeline({
  rows,
  loading,
  limit = 8,
  count,
  countLabel = 'marked today',
}: RecentActivityTimelineProps) {
  const items = rows.slice(0, limit);

  return (
    <Section
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <LogIn size={16} strokeWidth={1.75} />
          Recent activity
        </span>
      }
      subtitle="Latest attendance events as they happen."
      level={5}
      actions={
        count !== undefined ? (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
            <FlipNumber value={count} size={24} />
            <Text
              type="secondary"
              style={{
                fontSize: 11,
                lineHeight: 1.2,
                textTransform: 'uppercase',
                letterSpacing: '0.06em',
                maxWidth: 72,
              }}
            >
              {countLabel}
            </Text>
          </span>
        ) : undefined
      }
    >
      <Card styles={{ body: { padding: 18 } }} style={{ height: '100%' }}>
        {loading && rows.length === 0 ? (
          <Skeleton active paragraph={{ rows: 5 }} />
        ) : items.length === 0 ? (
          <EmptyState
            size="sm"
            icon={LogIn}
            title="No activity yet"
            description="Check-ins will stream in here through the day."
          />
        ) : (
          <div>
            {items.map((row, i) => (
              <TimelineItem
                key={row.punch.id}
                tone="success"
                icon={LogIn}
                title={row.employee?.fullName ?? `#${row.punch.employeeId}`}
                timestamp={formatClock(row.punch.punchedAt)}
                description={
                  <span style={{ fontSize: 12 }}>
                    Marked attendance · {PUNCH_SOURCE_LABEL[row.punch.source]}
                    {row.employee?.departmentName
                      ? ` · ${row.employee.departmentName}`
                      : ''}
                  </span>
                }
                isLast={i === items.length - 1}
              />
            ))}
          </div>
        )}
      </Card>
    </Section>
  );
}
