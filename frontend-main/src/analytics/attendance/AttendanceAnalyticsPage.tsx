// /analytics/attendance — three monthly trend charts plus a per-employee
// scorecard widget.
//
//   - On-time%: TrendLine with a zero baseline and percent ticks.
//   - Absenteeism%: identical shape, separate card so the reader doesn't
//     have to mentally subtract from 100%.
//   - OT trend: hours per month (StackedBars, single series).
//
// The scorecard tile sits below the trend charts. EmployeePicker drives
// it; Days segmented control flips between 7 / 30 / 90 day windows.
// All four StatCards in the scorecard render "—" when the picker hasn't
// resolved yet so the layout stays stable.

import { useState } from 'react';
import { Segmented, Space } from 'antd';
import { useQuery } from '@tanstack/react-query';

import {
  ChartCard,
  KpiGrid,
  PageHeader,
  RequestState,
  StatCard,
  StackedBars,
  TrendLine,
} from '@/components';
import { forbiddenOr } from '@/lib';
import { EmployeePicker } from '@/people';

import {
  fetchAbsenteeismPct,
  fetchEmployeeScorecard,
  fetchOnTimePct,
  fetchOtTrend,
} from '../bff-api';
import {
  formatCount,
  formatHours,
  formatPct,
} from '../format';
import {
  MonthsRangePicker,
  type AnalyticsMonths,
} from '../MonthsRangePicker';

const SCORECARD_DAYS = [7, 30, 90] as const;
type ScorecardDays = (typeof SCORECARD_DAYS)[number];

export function AttendanceAnalyticsPage() {
  const [months, setMonths] = useState<AnalyticsMonths>(12);
  const [scorecardEmployee, setScorecardEmployee] = useState<number | undefined>(
    undefined,
  );
  const [scorecardDays, setScorecardDays] = useState<ScorecardDays>(30);

  const onTimeQuery = useQuery({
    queryKey: ['analytics', 'on-time-pct', months],
    queryFn: ({ signal }) => fetchOnTimePct(months, { signal }),
    staleTime: 2 * 60 * 1000,
  });

  const absenteeismQuery = useQuery({
    queryKey: ['analytics', 'absenteeism-pct', months],
    queryFn: ({ signal }) => fetchAbsenteeismPct(months, { signal }),
    staleTime: 2 * 60 * 1000,
  });

  const otQuery = useQuery({
    queryKey: ['analytics', 'ot-trend', months],
    queryFn: ({ signal }) => fetchOtTrend(months, { signal }),
    staleTime: 2 * 60 * 1000,
  });

  const scorecardQuery = useQuery({
    queryKey: [
      'analytics',
      'scorecard',
      scorecardEmployee,
      scorecardDays,
    ],
    queryFn: ({ signal }) =>
      fetchEmployeeScorecard(scorecardEmployee!, scorecardDays, { signal }),
    enabled: !!scorecardEmployee,
    staleTime: 60 * 1000,
  });

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="Analytics"
        title="Attendance"
        description="On-time%, absenteeism, and overtime trends across the organisation."
        actions={<MonthsRangePicker value={months} onChange={setMonths} />}
      />

      <ChartCard
        title="On-time %"
        subtitle="Share of PRESENT records that punched in on or before shift start."
      >
        <RequestState
          loading={onTimeQuery.isLoading}
          error={onTimeQuery.error}
          empty={onTimeQuery.data?.series?.length === 0}
          errorTitle={forbiddenOr(
            onTimeQuery.error,
            "You don't have permission to view attendance analytics",
            "Couldn't load on-time%",
          )}
          onRetry={() => onTimeQuery.refetch()}
        >
          {onTimeQuery.data ? (
            <TrendLine
              data={
                onTimeQuery.data.series as unknown as Array<
                  Record<string, unknown>
                >
              }
              xKey="month"
              series={[{ key: 'pct', label: 'On-time %', tone: 'success' }]}
              yTickFormatter={(v) => formatPct(v, 0)}
              valueFormatter={(v) => formatPct(v)}
              height={260}
            />
          ) : null}
        </RequestState>
      </ChartCard>

      <ChartCard
        title="Absenteeism %"
        subtitle="Absent records divided by scheduled work-days."
      >
        <RequestState
          loading={absenteeismQuery.isLoading}
          error={absenteeismQuery.error}
          empty={absenteeismQuery.data?.series?.length === 0}
          errorTitle={forbiddenOr(
            absenteeismQuery.error,
            "You don't have permission to view attendance analytics",
            "Couldn't load absenteeism",
          )}
          onRetry={() => absenteeismQuery.refetch()}
        >
          {absenteeismQuery.data ? (
            <TrendLine
              data={
                absenteeismQuery.data.series as unknown as Array<
                  Record<string, unknown>
                >
              }
              xKey="month"
              series={[{ key: 'pct', label: 'Absenteeism %', tone: 'error' }]}
              yTickFormatter={(v) => formatPct(v, 1)}
              valueFormatter={(v) => formatPct(v)}
              height={260}
            />
          ) : null}
        </RequestState>
      </ChartCard>

      <ChartCard
        title="Overtime"
        subtitle="Hours per month across the organisation."
      >
        <RequestState
          loading={otQuery.isLoading}
          error={otQuery.error}
          empty={otQuery.data?.series?.length === 0}
          errorTitle={forbiddenOr(
            otQuery.error,
            "You don't have permission to view attendance analytics",
            "Couldn't load OT trend",
          )}
          onRetry={() => otQuery.refetch()}
        >
          {otQuery.data ? (
            <StackedBars
              data={
                otQuery.data.series as unknown as Array<
                  Record<string, unknown>
                >
              }
              xKey="month"
              series={[{ key: 'hours', label: 'Hours', tone: 'warning' }]}
              valueFormatter={(v) => formatHours(v)}
              height={260}
            />
          ) : null}
        </RequestState>
      </ChartCard>

      <ChartCard
        title="Employee scorecard"
        subtitle="Pick an employee and a window. Counts are over the rolling N days, computed server-side."
        actions={
          <Space size={8}>
            <div style={{ minWidth: 240 }}>
              <EmployeePicker
                value={scorecardEmployee}
                onChange={setScorecardEmployee}
              />
            </div>
            <Segmented
              value={scorecardDays}
              onChange={(v) => setScorecardDays(v as ScorecardDays)}
              options={SCORECARD_DAYS.map((d) => ({
                label: `${d}d`,
                value: d,
              }))}
              size="small"
            />
          </Space>
        }
        height="auto"
      >
        <RequestState
          loading={scorecardQuery.isLoading && !!scorecardEmployee}
          error={scorecardQuery.error}
          empty={!scorecardEmployee}
          emptyTitle="Pick an employee"
          emptyDescription="Type a name or employee code to load their attendance scorecard."
          errorTitle={forbiddenOr(
            scorecardQuery.error,
            "You don't have permission to view this employee",
            "Couldn't load scorecard",
          )}
          onRetry={() => scorecardQuery.refetch()}
        >
          {scorecardQuery.data ? (
            <KpiGrid minColumnWidth={180}>
              <StatCard
                label="Present"
                value={formatCount(scorecardQuery.data.presentCount)}
                tone="success"
              />
              <StatCard
                label="Late"
                value={formatCount(scorecardQuery.data.lateCount)}
                tone="warning"
              />
              <StatCard
                label="Absent"
                value={formatCount(scorecardQuery.data.absentCount)}
                tone="error"
              />
              <StatCard
                label="On-time %"
                value={formatPct(scorecardQuery.data.onTimePct)}
                tone="primary"
              />
              <StatCard
                label="Overtime"
                value={formatHours(scorecardQuery.data.otHours)}
                tone="info"
              />
            </KpiGrid>
          ) : null}
        </RequestState>
      </ChartCard>
    </Space>
  );
}
