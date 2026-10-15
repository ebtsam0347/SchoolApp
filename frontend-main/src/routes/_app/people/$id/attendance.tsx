// /people/$id/attendance — manager / HR view of an employee's attendance.
//
// Three blocks:
//   1. Today snapshot (always 200; `state === 'NOT_PUNCHED'` → no record yet).
//   2. Recent month range (last 30 days, summarised + listed).
//   3. Shift assignments (the row covering today is active; others are
//      collapsed under "History").
//
// Read-only. HR actions (assign shift, drop a shift override, add an
// operator-punch) live on dedicated admin screens.

import { createFileRoute, useParams } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Card, Empty, Typography } from 'antd';
import { Calendar, Hourglass, Sparkles, Timer } from 'lucide-react';

import { KpiGrid, RequestState, Section, StatCard } from '@/components';
import {
  attendanceApi,
  formatDateShort,
  formatMinutes,
  formatDateMonthDay,
  shiftsApi,
  toIsoDate,
} from '@/attendance';
import {
  RangeTable,
  ShiftAssignments,
  TodayCard,
  computeRangeSummary,
  lastNDays,
} from '@/people/attendance';

const { Text } = Typography;

export const Route = createFileRoute('/_app/people/$id/attendance')({
  component: AttendanceTab,
});

const RANGE_DAYS = 30;

function AttendanceTab() {
  const { id } = useParams({ from: '/_app/people/$id/attendance' });
  const employeeId = Number(id);

  const { from, to } = lastNDays(RANGE_DAYS);

  const todayQuery = useQuery({
    queryKey: ['attendance', 'employee', employeeId, 'today'],
    queryFn: ({ signal }) =>
      attendanceApi.todayForEmployee(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  const rangeQuery = useQuery({
    queryKey: ['attendance', 'employee', employeeId, 'range', from, to],
    queryFn: ({ signal }) =>
      attendanceApi.forEmployee(employeeId, { from, to }, signal),
    enabled: Number.isFinite(employeeId),
  });

  const shiftsQuery = useQuery({
    queryKey: ['shifts', 'employee', employeeId],
    queryFn: ({ signal }) => shiftsApi.forEmployee(employeeId, signal),
    enabled: Number.isFinite(employeeId),
    staleTime: 60_000,
  });

  const todayProjection = todayQuery.data ?? null;
  const todayRecord = todayProjection?.record ?? null;
  const todayMissing = todayProjection?.state === 'NOT_PUNCHED';
  const records = rangeQuery.data ?? [];
  const summary = computeRangeSummary(records);

  const shifts = shiftsQuery.data ?? [];
  const isoToday = toIsoDate(new Date());
  const activeShift =
    shifts.find(
      (s) =>
        s.effectiveFrom <= isoToday &&
        (s.effectiveTo == null || s.effectiveTo >= isoToday),
    ) ?? null;
  const history = shifts.filter((s) => s !== activeShift);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <Section title="Today" subtitle={formatDateShort(isoToday)}>
        {todayQuery.isLoading ? (
          <Card styles={{ body: { padding: 18 } }}>
            <Text type="secondary">Loading…</Text>
          </Card>
        ) : todayMissing || !todayRecord ? (
          <Card styles={{ body: { padding: 18 } }}>
            <Empty
              description={
                <Text type="secondary">
                  No attendance record for today yet. The row appears once the
                  first punch is recorded.
                </Text>
              }
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          </Card>
        ) : (
          <RequestState
            loading={false}
            error={todayQuery.isError ? todayQuery.error : undefined}
            errorTitle="Couldn't load today"
          >
            <TodayCard record={todayRecord} />
          </RequestState>
        )}
      </Section>

      <Section
        title={`Last ${RANGE_DAYS} days`}
        subtitle={`${formatDateMonthDay(from)} → ${formatDateMonthDay(to)}`}
      >
        <KpiGrid minColumnWidth={200}>
          <StatCard
            variant="accent-radial"
            tone="success"
            accentCorner="tr"
            icon={Timer}
            label="Worked"
            value={formatMinutes(summary.totalWorked)}
            delta={`${summary.presentDays} present day${summary.presentDays === 1 ? '' : 's'}`}
          />
          <StatCard
            variant="plain"
            tone="info"
            icon={Sparkles}
            label="Overtime"
            value={formatMinutes(summary.totalOt)}
          />
          <StatCard
            variant="plain"
            tone={summary.totalLate > 0 ? 'warning' : 'neutral'}
            icon={Hourglass}
            label="Late"
            value={formatMinutes(summary.totalLate)}
            delta={
              summary.lateDays === 0
                ? 'On time every day'
                : `${summary.lateDays} day${summary.lateDays === 1 ? '' : 's'}`
            }
          />
          <StatCard
            variant="plain"
            tone={summary.absentDays > 0 ? 'error' : 'neutral'}
            icon={Calendar}
            label="Absent"
            value={`${summary.absentDays}`}
          />
        </KpiGrid>

        <RequestState
          loading={false}
          error={rangeQuery.isError ? rangeQuery.error : undefined}
          errorTitle="Couldn't load attendance range"
        >
          <Card styles={{ body: { padding: 0 } }} style={{ marginTop: 16 }}>
            <RangeTable records={records} loading={rangeQuery.isLoading} />
          </Card>
        </RequestState>
      </Section>

      <Section title="Shift assignments" subtitle="Newest first.">
        <RequestState
          loading={false}
          error={shiftsQuery.isError ? shiftsQuery.error : undefined}
          errorTitle="Couldn't load shift assignments"
        >
          {shifts.length === 0 ? (
            <Card styles={{ body: { padding: 18 } }}>
              <Empty
                description={
                  <Text type="secondary">
                    No shift assignments on file. HR can attach one from the
                    Shifts admin.
                  </Text>
                }
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            </Card>
          ) : (
            <ShiftAssignments
              active={activeShift}
              history={history}
              isoToday={isoToday}
            />
          )}
        </RequestState>
      </Section>
    </div>
  );
}
