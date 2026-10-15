// /analytics/people — three charts driven by /analytics/employees/*.
//
//   - Headcount trend: TrendLine with joiners + leavers + endHeadcount,
//     three series in one chart so the reader can see joiners/leavers
//     against the overall body that's growing or shrinking.
//   - Attrition: split into two ChartCards. Bar chart of leavers per
//     month (raw count) + a TrendLine of ratePct so the slope is
//     readable independently of headcount. Rolling-12m headline lives
//     in the section subtitle so it doesn't get lost.
//   - Tenure bands: HorizontalBars with a Segmented to flip splitBy
//     between "All", "By department", "By grade".

import { useState } from 'react';
import { Segmented, Space } from 'antd';
import { useQuery } from '@tanstack/react-query';

import {
  ChartCard,
  HorizontalBars,
  PageHeader,
  RequestState,
  StackedBars,
  TrendLine,
} from '@/components';
import { forbiddenOr } from '@/lib';

import {
  fetchAttrition,
  fetchHeadcountTrend,
  fetchTenureBands,
} from '../bff-api';
import { formatPct } from '../format';
import {
  MonthsRangePicker,
  type AnalyticsMonths,
} from '../MonthsRangePicker';
import type { TenureBandsSplit } from '../types';

const SPLIT_OPTIONS: { label: string; value: TenureBandsSplit }[] = [
  { label: 'All', value: 'none' },
  { label: 'By department', value: 'dept' },
  { label: 'By grade', value: 'grade' },
];

export function PeopleAnalyticsPage() {
  const [months, setMonths] = useState<AnalyticsMonths>(12);
  const [splitBy, setSplitBy] = useState<TenureBandsSplit>('none');

  const headcountQuery = useQuery({
    queryKey: ['analytics', 'headcount-trend', months],
    queryFn: ({ signal }) => fetchHeadcountTrend(months, { signal }),
    staleTime: 2 * 60 * 1000,
  });

  const attritionQuery = useQuery({
    queryKey: ['analytics', 'attrition', months],
    queryFn: ({ signal }) => fetchAttrition(months, { signal }),
    staleTime: 2 * 60 * 1000,
  });

  const tenureQuery = useQuery({
    queryKey: ['analytics', 'tenure-bands', splitBy],
    queryFn: ({ signal }) => fetchTenureBands(splitBy, { signal }),
    staleTime: 5 * 60 * 1000,
  });

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="Analytics"
        title="People"
        description="Headcount trend, attrition, and tenure breakdown across the organisation."
        actions={
          <MonthsRangePicker value={months} onChange={setMonths} />
        }
      />

      <ChartCard
        title="Headcount trend"
        subtitle={`Joiners, leavers, and end-of-month headcount over the last ${months} months.`}
      >
        <RequestState
          loading={headcountQuery.isLoading}
          error={headcountQuery.error}
          empty={headcountQuery.data?.series?.length === 0}
          errorTitle={forbiddenOr(
            headcountQuery.error,
            "You don't have permission to view people analytics",
            "Couldn't load the headcount trend",
          )}
          onRetry={() => headcountQuery.refetch()}
        >
          {headcountQuery.data ? (
            <TrendLine
              data={
                headcountQuery.data.series as unknown as Array<
                  Record<string, unknown>
                >
              }
              xKey="month"
              series={[
                { key: 'endHeadcount', label: 'Headcount', tone: 'primary' },
                { key: 'joiners', label: 'Joiners', tone: 'success' },
                { key: 'leavers', label: 'Leavers', tone: 'error' },
              ]}
              height={300}
            />
          ) : null}
        </RequestState>
      </ChartCard>

      <ChartCard
        title="Leavers per month"
        subtitle={
          attritionQuery.data
            ? `Rolling 12-month attrition rate · ${formatPct(attritionQuery.data.rollingTwelveMonthPct)}`
            : 'Raw count of departures per month.'
        }
      >
        <RequestState
          loading={attritionQuery.isLoading}
          error={attritionQuery.error}
          empty={attritionQuery.data?.series?.length === 0}
          errorTitle={forbiddenOr(
            attritionQuery.error,
            "You don't have permission to view people analytics",
            "Couldn't load attrition",
          )}
          onRetry={() => attritionQuery.refetch()}
        >
          {attritionQuery.data ? (
            <StackedBars
              data={
                attritionQuery.data.series as unknown as Array<
                  Record<string, unknown>
                >
              }
              xKey="month"
              series={[{ key: 'leavers', label: 'Leavers', tone: 'error' }]}
              height={260}
            />
          ) : null}
        </RequestState>
      </ChartCard>

      <ChartCard
        title="Attrition rate"
        subtitle="Leavers as a percentage of period average headcount."
      >
        <RequestState
          loading={attritionQuery.isLoading}
          error={attritionQuery.error}
          empty={attritionQuery.data?.series?.length === 0}
          errorTitle={forbiddenOr(
            attritionQuery.error,
            "You don't have permission to view people analytics",
            "Couldn't load attrition",
          )}
          onRetry={() => attritionQuery.refetch()}
        >
          {attritionQuery.data ? (
            <TrendLine
              data={
                attritionQuery.data.series as unknown as Array<
                  Record<string, unknown>
                >
              }
              xKey="month"
              series={[{ key: 'ratePct', label: 'Rate %', tone: 'warning' }]}
              yTickFormatter={(v) => formatPct(v, 1)}
              valueFormatter={(v) => formatPct(v)}
              height={260}
            />
          ) : null}
        </RequestState>
      </ChartCard>

      <ChartCard
        title="Tenure bands"
        subtitle={
          splitBy === 'none'
            ? 'Active employees grouped by tenure range.'
            : `Active employees grouped by tenure range, broken down by ${splitBy === 'dept' ? 'department' : 'grade'}.`
        }
        actions={
          <Segmented
            options={SPLIT_OPTIONS}
            value={splitBy}
            onChange={(v) => setSplitBy(v as TenureBandsSplit)}
            size="small"
          />
        }
      >
        <RequestState
          loading={tenureQuery.isLoading}
          error={tenureQuery.error}
          empty={tenureQuery.data?.bands?.length === 0}
          errorTitle={forbiddenOr(
            tenureQuery.error,
            "You don't have permission to view people analytics",
            "Couldn't load tenure bands",
          )}
          onRetry={() => tenureQuery.refetch()}
        >
          {tenureQuery.data ? (
            <HorizontalBars
              rows={(tenureQuery.data.bands ?? []).map((band) => {
                const splits = Array.isArray(band?.splits) ? band.splits : [];
                return {
                  id: band.label,
                  label: band.label,
                  value: band.total ?? 0,
                  caption:
                    splits.length > 0
                      ? splits
                          .slice(0, 4)
                          .map((s) => `${s.key} · ${s.count}`)
                          .join(' · ')
                      : undefined,
                };
              })}
              tone="primary"
              sort="none"
              topN={6}
            />
          ) : null}
        </RequestState>
      </ChartCard>
    </Space>
  );
}
