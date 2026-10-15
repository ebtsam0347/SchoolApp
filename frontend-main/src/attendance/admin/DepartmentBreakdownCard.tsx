// Department-wise attendance for the selected day. There's no server-side
// per-department aggregate yet, so we pull `records/with-employee` (capped at
// 500 rows) and fold them client-side. When the org exceeds the cap we render
// what we have and flag the sample as partial — the documented fix is a
// backend aggregate (`GET /attendance/operator/department-breakdown?date=`).
//
// Honors the shared dashboard filters (department / section / status / employee)
// so the breakdown narrows in lockstep with the live feed below it.

import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Segmented, Skeleton, Typography } from 'antd';
import { Building2 } from 'lucide-react';

import { EmptyState, Section } from '@/components';
import { HorizontalBars } from '@/components/charts';
import { forbiddenOr } from '@/lib';
import { attendanceBff, type AttendanceStatus } from '@/attendance';
import {
  aggregateDepartmentBreakdown,
  DEPARTMENT_METRIC_TONE,
  DEPARTMENT_REFETCH_MS,
  DEPARTMENT_SAMPLE_SIZE,
  type DepartmentMetric,
} from './dashboard-live';

const { Text } = Typography;

export type DepartmentBreakdownFilters = {
  date: string;
  departmentId?: number;
  sectionId?: number;
  status?: AttendanceStatus;
  employeeId?: number;
};

export type DepartmentBreakdownCardProps = {
  filters: DepartmentBreakdownFilters;
  metric: DepartmentMetric;
  onMetricChange: (metric: DepartmentMetric) => void;
  /** Pause polling when the tab isn't the live "today" view. */
  live: boolean;
};

const METRIC_LABEL: Record<DepartmentMetric, string> = {
  present: 'Present',
  absent: 'Absent',
  leave: 'On leave',
};

export function DepartmentBreakdownCard({
  filters,
  metric,
  onMetricChange,
  live,
}: DepartmentBreakdownCardProps) {
  const query = useQuery({
    queryKey: ['attendance', 'department-breakdown', filters],
    queryFn: ({ signal }) =>
      attendanceBff.records(
        {
          date: filters.date,
          departmentId: filters.departmentId,
          sectionId: filters.sectionId,
          status: filters.status,
          employeeId: filters.employeeId,
          size: DEPARTMENT_SAMPLE_SIZE,
          sort: 'attendanceDate,desc',
        },
        signal,
      ),
    placeholderData: (prev) => prev,
    refetchInterval: live ? DEPARTMENT_REFETCH_MS : false,
  });

  const rows = useMemo(
    () => aggregateDepartmentBreakdown(query.data?.content ?? []),
    [query.data],
  );

  const loaded = query.data?.content.length ?? 0;
  const totalElements = query.data?.totalElements ?? 0;
  const partial = totalElements > loaded;

  const barRows = rows.map((d) => ({
    id: d.departmentId ?? d.departmentName,
    label: d.departmentName,
    value: d[metric],
    caption: `P ${d.present} · A ${d.absent} · L ${d.leave}`,
    tone: DEPARTMENT_METRIC_TONE[metric],
    href: d.departmentId != null ? `/attendance/records?departmentId=${d.departmentId}` : undefined,
  }));

  return (
    <Section
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <Building2 size={16} strokeWidth={1.75} />
          Department-wise attendance
        </span>
      }
      subtitle="Present / absent / leave split per department for the selected day."
      level={5}
      actions={
        <Segmented<DepartmentMetric>
          size="small"
          value={metric}
          onChange={onMetricChange}
          options={(Object.keys(METRIC_LABEL) as DepartmentMetric[]).map((m) => ({
            value: m,
            label: METRIC_LABEL[m],
          }))}
        />
      }
    >
      <Card styles={{ body: { padding: barRows.length === 0 ? 18 : 16 } }}>
        {query.isError ? (
          <EmptyState
            size="md"
            icon={Building2}
            title={forbiddenOr(
              query.error,
              "You don't have permission to view department attendance.",
              "Couldn't load department attendance",
            )}
            description="Try again in a moment."
          />
        ) : query.isLoading && !query.data ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : barRows.length === 0 ? (
          <EmptyState
            size="md"
            icon={Building2}
            title="No attendance yet"
            description="Records will group by department as employees are marked for the day."
          />
        ) : (
          <>
            {partial ? (
              <Alert
                type="info"
                showIcon
                style={{ marginBottom: 12 }}
                title={`Aggregated from the first ${loaded.toLocaleString('en-PK')} of ${totalElements.toLocaleString('en-PK')} records. Narrow the filters for an exact split.`}
              />
            ) : null}
            <HorizontalBars
              rows={barRows}
              tone={DEPARTMENT_METRIC_TONE[metric]}
              topN={8}
              valueFormatter={(v) => v.toLocaleString('en-PK')}
            />
            <Text type="secondary" style={{ display: 'block', marginTop: 10, fontSize: 12 }}>
              Ranking by {METRIC_LABEL[metric].toLowerCase()} · caption shows Present · Absent · Leave.
            </Text>
          </>
        )}
      </Card>
    </Section>
  );
}
