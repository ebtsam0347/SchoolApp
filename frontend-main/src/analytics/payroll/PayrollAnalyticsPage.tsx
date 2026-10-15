// /analytics/payroll — cost trend over time + a salary distribution
// summary.
//
//   - Cost trend: stacked bars per month showing gross / EOBI / PESSI /
//     tax / net so the reader can see what's driving the totalCost.
//     A separate small TrendLine sits below for costPerHead so the per-
//     capita signal isn't swallowed by the absolute totals.
//   - Salary distribution: five stat cards (min / p25 / median / p75 /
//     max) plus a count caption. A boxplot would be ideal but recharts
//     doesn't ship one — five cards reads cleaner anyway and matches
//     how HR typically reasons about pay bands.

import { useState, type ReactNode } from 'react';
import { Space } from 'antd';
import { useQuery } from '@tanstack/react-query';

import {
  ChartCard,
  KpiGrid,
  MoneyCell,
  PageHeader,
  RequestState,
  StatCard,
  StackedBars,
  TrendLine,
} from '@/components';
import { forbiddenOr } from '@/lib';

import {
  fetchPayrollCostTrend,
  fetchSalaryDistribution,
} from '../bff-api';
import {
  formatCompactNumber,
  formatCount,
  formatMoneyCompact,
  formatMoneyExact,
} from '../format';
import {
  MonthsRangePicker,
  type AnalyticsMonths,
} from '../MonthsRangePicker';

function safeMoney(value: number | undefined): ReactNode {
  return Number.isFinite(value) ? (
    <MoneyCell amount={value as number} compact />
  ) : (
    '—'
  );
}

export function PayrollAnalyticsPage() {
  const [months, setMonths] = useState<AnalyticsMonths>(12);

  const costQuery = useQuery({
    queryKey: ['analytics', 'payroll-cost-trend', months],
    queryFn: ({ signal }) => fetchPayrollCostTrend(months, { signal }),
    staleTime: 5 * 60 * 1000,
  });

  const distributionQuery = useQuery({
    queryKey: ['analytics', 'salary-distribution'],
    queryFn: ({ signal }) => fetchSalaryDistribution({ signal }),
    staleTime: 10 * 60 * 1000,
  });

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="Analytics"
        title="Payroll"
        description="Cost trend over time and the current salary distribution across the workforce."
        actions={<MonthsRangePicker value={months} onChange={setMonths} />}
      />

      <ChartCard
        title="Cost breakdown"
        subtitle="Net + tax + statutory contributions per month."
      >
        <RequestState
          loading={costQuery.isLoading}
          error={costQuery.error}
          empty={costQuery.data?.series?.length === 0}
          errorTitle={forbiddenOr(
            costQuery.error,
            "You don't have permission to view payroll analytics",
            "Couldn't load cost trend",
          )}
          onRetry={() => costQuery.refetch()}
        >
          {costQuery.data ? (
            <StackedBars
              data={
                costQuery.data.series as unknown as Array<
                  Record<string, unknown>
                >
              }
              xKey="month"
              series={[
                { key: 'net', label: 'Net', tone: 'success' },
                { key: 'tax', label: 'Tax', tone: 'warning' },
                { key: 'eobi', label: 'EOBI', tone: 'info' },
                { key: 'pessi', label: 'PESSI', tone: 'primary' },
              ]}
              valueFormatter={(v) => formatMoneyCompact(v)}
              yTickFormatter={(v) => formatCompactNumber(v)}
              height={280}
            />
          ) : null}
        </RequestState>
      </ChartCard>

      <ChartCard
        title="Cost per head"
        subtitle="Total employer-borne cost divided by employee count."
      >
        <RequestState
          loading={costQuery.isLoading}
          error={costQuery.error}
          empty={costQuery.data?.series?.length === 0}
          errorTitle={forbiddenOr(
            costQuery.error,
            "You don't have permission to view payroll analytics",
            "Couldn't load cost trend",
          )}
          onRetry={() => costQuery.refetch()}
        >
          {costQuery.data ? (
            <TrendLine
              data={
                costQuery.data.series as unknown as Array<
                  Record<string, unknown>
                >
              }
              xKey="month"
              series={[
                { key: 'costPerHead', label: 'Cost / head', tone: 'primary' },
              ]}
              valueFormatter={(v) => formatMoneyExact(v)}
              yTickFormatter={(v) => formatCompactNumber(v)}
              height={240}
            />
          ) : null}
        </RequestState>
      </ChartCard>

      <ChartCard
        title="Salary distribution"
        subtitle={
          distributionQuery.data
            ? `Across ${formatCount(distributionQuery.data.count)} active employees.`
            : 'Across all active employees.'
        }
        height="auto"
      >
        <RequestState
          loading={distributionQuery.isLoading}
          error={distributionQuery.error}
          errorTitle={forbiddenOr(
            distributionQuery.error,
            "You don't have permission to view payroll analytics",
            "Couldn't load salary distribution",
          )}
          onRetry={() => distributionQuery.refetch()}
        >
          {distributionQuery.data ? (
            <KpiGrid minColumnWidth={170}>
              <StatCard
                label="Minimum"
                value={safeMoney(distributionQuery.data.min)}
                tone="neutral"
              />
              <StatCard
                label="25th percentile"
                value={safeMoney(distributionQuery.data.p25)}
                tone="info"
              />
              <StatCard
                label="Median"
                value={safeMoney(distributionQuery.data.median)}
                tone="primary"
                variant="accent-corner"
                accentCorner="tr"
              />
              <StatCard
                label="75th percentile"
                value={safeMoney(distributionQuery.data.p75)}
                tone="warning"
              />
              <StatCard
                label="Maximum"
                value={safeMoney(distributionQuery.data.max)}
                tone="success"
              />
            </KpiGrid>
          ) : null}
        </RequestState>
      </ChartCard>
    </Space>
  );
}
