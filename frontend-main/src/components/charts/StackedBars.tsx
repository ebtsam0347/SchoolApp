import type { CSSProperties, ReactNode } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { useChartTheme, useReducedMotion, type ChartTone } from './theme';
import { ChartEmpty } from './ChartEmpty';

export interface StackedBarsSeries {
  key: string;
  label?: string;
  tone?: ChartTone;
}

export interface StackedBarsProps {
  data: Array<Record<string, unknown>>;
  /** Key in each row holding the x-axis label. */
  xKey: string;
  series: StackedBarsSeries[];
  /** Stack series instead of grouping. Default true. */
  stacked?: boolean;
  /** Bar corner radius (top of stack only when stacked). Default 3. */
  radius?: number;
  /** Bar category gap as % of category width. Default '20%'. */
  barGap?: string | number;
  showLegend?: boolean;
  height?: number | string;
  xTickFormatter?: (value: unknown) => string;
  yTickFormatter?: (value: number) => string;
  valueFormatter?: (value: number, seriesKey: string) => string;
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
  className?: string;
  styles?: { root?: CSSProperties };
}

/**
 * Headline bar chart — stacked by default, switch to grouped via
 * `stacked={false}`. Multi-series tones come from the `tone` per series;
 * single-series defaults to primary.
 */
export function StackedBars({
  data,
  xKey,
  series,
  stacked = true,
  radius = 3,
  barGap = '20%',
  showLegend,
  height = 320,
  xTickFormatter,
  yTickFormatter,
  valueFormatter,
  emptyTitle,
  emptyDescription,
  className,
  styles: slot,
}: StackedBarsProps) {
  const t = useChartTheme();
  const reducedMotion = useReducedMotion();

  if (!data || data.length === 0) {
    return (
      <div
        className={className}
        style={{ flex: 1, display: 'flex', height, ...slot?.root }}
      >
        <ChartEmpty title={emptyTitle} description={emptyDescription} />
      </div>
    );
  }

  const showLegendResolved = showLegend ?? series.length > 1;
  const lastSeriesKey = series[series.length - 1]?.key;

  // See TrendLine for the rationale on passing pixel height directly.
  const rcHeight = typeof height === 'number' ? height : 320;

  return (
    <div
      className={className}
      style={{ flex: 1, width: '100%', height, minWidth: 0, ...slot?.root }}
    >
      <ResponsiveContainer width="100%" height={rcHeight}>
        <BarChart
          data={data}
          margin={{ top: 8, right: 16, bottom: 8, left: 8 }}
          barCategoryGap={barGap}
        >
          <CartesianGrid
            stroke={t.grid.stroke}
            strokeDasharray={t.grid.strokeDasharray}
            vertical={false}
          />
          <XAxis
            dataKey={xKey}
            tick={t.axis.tick}
            stroke={t.axis.stroke}
            tickLine={false}
            axisLine={{ stroke: t.axis.stroke }}
            tickFormatter={xTickFormatter as never}
          />
          <YAxis
            tick={t.axis.tick}
            stroke={t.axis.stroke}
            tickLine={false}
            axisLine={false}
            tickFormatter={yTickFormatter as never}
            width={48}
          />
          <Tooltip
            contentStyle={t.tooltip.contentStyle}
            itemStyle={t.tooltip.itemStyle}
            labelStyle={t.tooltip.labelStyle}
            cursor={{ fill: t.tooltip.cursor.fill }}
            formatter={
              valueFormatter
                ? ((v: unknown, key: unknown) =>
                    valueFormatter(Number(v), String(key))) as never
                : undefined
            }
          />
          {showLegendResolved ? (
            <Legend
              verticalAlign="bottom"
              height={28}
              iconType="circle"
              iconSize={8}
              wrapperStyle={{ fontSize: 12, color: t.textMuted }}
            />
          ) : null}
          {series.map((s, idx) => {
            const color = t.toneColor(
              s.tone ?? (['primary', 'success', 'warning', 'info', 'error', 'neutral'][idx % 6] as ChartTone),
            );
            const isLast = s.key === lastSeriesKey;
            const r: [number, number, number, number] = stacked
              ? isLast
                ? [radius, radius, 0, 0]
                : [0, 0, 0, 0]
              : [radius, radius, 0, 0];
            return (
              <Bar
                key={s.key}
                dataKey={s.key}
                name={s.label ?? s.key}
                stackId={stacked ? 'stack' : undefined}
                fill={color}
                radius={r}
                isAnimationActive={!reducedMotion}
              />
            );
          })}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
