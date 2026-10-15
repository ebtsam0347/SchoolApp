// Live punch feed — newest punch at the top, with a brief highlight as fresh
// rows arrive. Fed by the page-owned `punches/with-employee` query (shared with
// RecentActivityTimeline so there's a single round-trip). The highlight is
// purely presentational; the ordering + data are authoritative from the server.

import { useEffect, useRef, useState } from 'react';
import { Card, Table, Typography, type TableProps } from 'antd';

import { EmptyState, Section, StatusPill } from '@/components';
import { Radio } from 'lucide-react';
import { formatClock, PUNCH_SOURCE_LABEL, type AttendancePunchesRow } from '@/attendance';
import { EmployeeSummaryCell } from './EmployeeSummaryCell';
import { NEW_ROW_HIGHLIGHT_MS, PUNCH_SOURCE_TONE } from './dashboard-live';
import './dashboard-live.css';

const { Text } = Typography;

export type LiveAttendanceFeedProps = {
  rows: AttendancePunchesRow[];
  loading: boolean;
  fetching: boolean;
};

// A burst larger than this is treated as a reset (initial load / filter change)
// rather than genuine new punches — so we don't flash the entire table.
const FLASH_BURST_CAP = 6;

export function LiveAttendanceFeed({ rows, loading, fetching }: LiveAttendanceFeedProps) {
  const seenRef = useRef<Set<number>>(new Set());
  const [flashIds, setFlashIds] = useState<Set<number>>(new Set());

  useEffect(() => {
    const currentIds = rows.map((r) => r.punch.id);
    const prev = seenRef.current;
    seenRef.current = new Set(currentIds);
    if (prev.size === 0) return; // first paint — nothing to flash against
    const fresh = currentIds.filter((id) => !prev.has(id));
    if (fresh.length === 0 || fresh.length > FLASH_BURST_CAP) return;
    setFlashIds(new Set(fresh));
    const t = setTimeout(() => setFlashIds(new Set()), NEW_ROW_HIGHLIGHT_MS);
    return () => clearTimeout(t);
  }, [rows]);

  const columns: TableProps<AttendancePunchesRow>['columns'] = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, row) => (
        <EmployeeSummaryCell
          summary={row.employee}
          fallbackEmployeeId={row.punch.employeeId}
        />
      ),
    },
    {
      title: 'Department',
      key: 'department',
      width: 160,
      responsive: ['md'],
      render: (_, row) => (
        <Text style={{ fontSize: 12 }}>{row.employee?.departmentName ?? '—'}</Text>
      ),
    },
    {
      title: 'Punched at',
      key: 'punchedAt',
      width: 100,
      render: (_, row) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {formatClock(row.punch.punchedAt)}
        </span>
      ),
    },
    {
      title: 'Source',
      key: 'source',
      width: 120,
      render: (_, row) => (
        <StatusPill tone={PUNCH_SOURCE_TONE[row.punch.source] ?? 'neutral'} size="sm">
          {PUNCH_SOURCE_LABEL[row.punch.source]}
        </StatusPill>
      ),
    },
  ];

  return (
    <Section
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <Radio size={16} strokeWidth={1.75} />
          Live attendance feed
        </span>
      }
      subtitle="Most recent punches first. New rows highlight as they arrive."
      level={5}
      actions={
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            fontSize: 12,
            opacity: fetching ? 1 : 0.7,
          }}
        >
          <span
            aria-hidden
            style={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              background: '#16a34a',
              boxShadow: '0 0 0 3px color-mix(in srgb, #16a34a 24%, transparent)',
            }}
          />
          <Text type="secondary" style={{ fontSize: 12 }}>
            {fetching ? 'Updating…' : 'Live'}
          </Text>
        </span>
      }
    >
      <Card styles={{ body: { padding: rows.length === 0 ? 18 : 0 } }}>
        {rows.length === 0 && !loading ? (
          <EmptyState
            size="md"
            icon={Radio}
            title="No punches yet"
            description="Punches appear here in real time as employees check in or out."
          />
        ) : (
          <Table<AttendancePunchesRow>
            rowKey={(row) => row.punch.id}
            loading={loading}
            dataSource={rows}
            columns={columns}
            size="small"
            pagination={false}
            scroll={{ y: 360 }}
            rowClassName={(row) =>
              flashIds.has(row.punch.id) ? 'fs-attn-row-new' : ''
            }
          />
        )}
      </Card>
    </Section>
  );
}
