// /analytics/leaves — utilization (current year, by leave type) + a
// monthly seasonality chart so HR can see when load spikes.
//
//   - Utilization: HorizontalBars sorted by used%, with allocated/used/
//     pending in the caption. Tone goes red when used% ≥ 90.
//   - Seasonality: stacked bars per month broken down by leave type, so
//     "August holiday cluster" or "December annual-leave run" pops.
//
// Year picker drives utilization; months range drives seasonality.
// Default year is current calendar year (server-side default would
// also work; we send it explicitly so the picker matches the data).

import { useMemo, useState } from 'react';
import { DatePicker, Space } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';

import {
  ChartCard,
  HorizontalBars,
  PageHeader,
  RequestState,
  StackedBars,
} from '@/components';
import type { ChartTone } from '@/components';
import { forbiddenOr } from '@/lib';

import {
  fetchLeaveSeasonality,
  fetchLeaveUtilization,
} from '../bff-api';
import { formatPct } from '../format';
import {
  MonthsRangePicker,
  type AnalyticsMonths,
} from '../MonthsRangePicker';

/** Tone palette for the seasonality stacked bars — cycled across the
 *  union of leave-type codes the server returns. */
const SEASONALITY_TONES: ChartTone[] = [
  'primary',
  'success',
  'warning',
  'info',
  'error',
  'neutral',
];

export function LeavesAnalyticsPage() {
  const [year, setYear] = useState<number>(dayjs().year());
  const [months, setMonths] = useState<AnalyticsMonths>(12);

  const utilizationQuery = useQuery({
    queryKey: ['analytics', 'leave-utilization', year],
    queryFn: ({ signal }) => fetchLeaveUtilization(year, { signal }),
    staleTime: 5 * 60 * 1000,
  });

  const seasonalityQuery = useQuery({
    queryKey: ['analytics', 'leave-seasonality', months],
    queryFn: ({ signal }) => fetchLeaveSeasonality(months, { signal }),
    staleTime: 5 * 60 * 1000,
  });

  // Seasonality data comes shaped as `series[].byType[]`. Recharts wants
  // a flat row per month with one column per leave-type code, so we
  // pivot here once and feed the result to StackedBars.
  //
  // Defensive against shape drift — server may omit `byType` for months
  // with zero leave activity, or return it as null. Coerce to [] before
  // iterating so we don't trip "is not iterable" on a quiet month.
  const { seasonalityRows, seasonalitySeries } = useMemo(() => {
    const series = Array.isArray(seasonalityQuery.data?.series)
      ? seasonalityQuery.data!.series
      : [];
    if (series.length === 0) {
      return { seasonalityRows: [], seasonalitySeries: [] };
    }
    const codeLabels = new Map<string, string>();
    for (const m of series) {
      const types = Array.isArray(m?.byType) ? m.byType : [];
      for (const t of types) codeLabels.set(t.code, t.label);
    }
    const rows = series.map((m) => {
      const types = Array.isArray(m?.byType) ? m.byType : [];
      const row: Record<string, unknown> = { month: m?.month ?? '' };
      for (const t of types) row[t.code] = t.days;
      return row;
    });
    const seriesDescriptors = [...codeLabels.entries()].map(
      ([code, label], i) => ({
        key: code,
        label,
        tone: SEASONALITY_TONES[i % SEASONALITY_TONES.length],
      }),
    );
    return { seasonalityRows: rows, seasonalitySeries: seriesDescriptors };
  }, [seasonalityQuery.data]);

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="Analytics"
        title="Leaves"
        description="Utilization for the chosen year and seasonality across the rolling window."
      />

      <ChartCard
        title="Utilization"
        subtitle={
          utilizationQuery.data
            ? `Across all employees · overall ${formatPct(utilizationQuery.data.overallPct)}`
            : 'Across all employees'
        }
        actions={
          <DatePicker
            picker="year"
            value={dayjs(`${year}-01-01`)}
            onChange={(v) => v && setYear(v.year())}
            allowClear={false}
            format="YYYY"
            style={{ width: 100 }}
          />
        }
        height="auto"
      >
        <RequestState
          loading={utilizationQuery.isLoading}
          error={utilizationQuery.error}
          empty={utilizationQuery.data?.byType?.length === 0}
          errorTitle={forbiddenOr(
            utilizationQuery.error,
            "You don't have permission to view leave analytics",
            "Couldn't load leave utilization",
          )}
          onRetry={() => utilizationQuery.refetch()}
        >
          {utilizationQuery.data ? (
            <HorizontalBars
              rows={(utilizationQuery.data.byType ?? []).map((t) => ({
                id: t.code,
                label: t.label,
                value: t.usedPct ?? 0,
                caption: `${t.used ?? 0} used · ${t.pending ?? 0} pending · ${t.allocated ?? 0} allocated`,
                tone:
                  (t.usedPct ?? 0) >= 90
                    ? 'error'
                    : (t.usedPct ?? 0) >= 75
                      ? 'warning'
                      : 'primary',
              }))}
              valueFormatter={(v) => formatPct(v, 1)}
              scaleMax={100}
              topN={20}
            />
          ) : null}
        </RequestState>
      </ChartCard>

      <ChartCard
        title="Seasonality"
        subtitle="Leave days taken per month, stacked by type."
        actions={<MonthsRangePicker value={months} onChange={setMonths} />}
      >
        <RequestState
          loading={seasonalityQuery.isLoading}
          error={seasonalityQuery.error}
          empty={seasonalityRows.length === 0}
          errorTitle={forbiddenOr(
            seasonalityQuery.error,
            "You don't have permission to view leave analytics",
            "Couldn't load leave seasonality",
          )}
          onRetry={() => seasonalityQuery.refetch()}
        >
          <StackedBars
            data={seasonalityRows}
            xKey="month"
            series={seasonalitySeries}
            valueFormatter={(v) =>
              Number.isFinite(v) ? `${v.toFixed(1)} d` : '—'
            }
            height={300}
          />
        </RequestState>
      </ChartCard>
    </Space>
  );
}
