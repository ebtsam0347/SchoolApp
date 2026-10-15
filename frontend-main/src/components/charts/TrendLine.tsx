import type { CSSProperties, ReactNode } from 'react';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { useChartTheme, useReducedMotion, type ChartTone } from './theme';
import { ChartEmpty } from './ChartEmpty';

export interface TrendLineSeries {
  key: string;
  label?: string;
  tone?: ChartTone;
  /** Stroke style. Default 'solid'. */
  dash?: 'solid' | 'dashed' | 'dotted';
  /** Stroke width. Default 2. */
  strokeWidth?: number;
}

export interface TrendLineProps {
  data: Array<Record<string, unknown>>;
  /** Key in each row holding the x-axis label. */
  xKey: string;
  /** One or more series to plot. Multiple = overlaid lines. */
  series: TrendLineSeries[];
  /**
   * Returns true if the row should render an anomaly dot. Non-anomaly rows
   * stay dot-less.
   */
  isAnomaly?: (row: Record<string, unknown>) => boolean;
  /** Show the legend below the chart. Default true when series.length > 1. */
  showLegend?: boolean;
  /** Show curve fill (area under the line). Default false. */
  area?: boolean;
  height?: number | string;
  xTickFormatter?: (value: unknown) => string;
  yTickFormatter?: (value: number) => string;
  valueFormatter?: (value: number, seriesKey: string) => string;
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
  className?: string;
  styles?: { root?: CSSProperties };
}

const dashArray = (dash: TrendLineSeries['dash']) =>
  dash === 'dashed' ? '6 4' : dash === 'dotted' ? '2 4' : undefined;

/**
 * Headline trend chart — single or multi-series line. Token-driven axes,
 * grid, and tooltip. Anomaly dots opt-in via `isAnomaly`.
 */
export function TrendLine({
  data,
  xKey,
  series,
  isAnomaly,
  showLegend,
  height = 320,
  xTickFormatter,
  yTickFormatter,
  valueFormatter,
  emptyTitle,
  emptyDescription,
  className,
  styles: slot,
}: TrendLineProps) {
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

  // Pass `height` directly to ResponsiveContainer rather than relying on the
  // wrapping div's % sizing — recharts measures its parent on first paint, and
  // when the chart sits inside an AntD Row/Col grid the parent often reports
  // 0/0 before layout settles, producing the "width(-1) and height(-1)"
  // warning across every chart on the page. Explicit pixels skip the dance.
  const rcHeight = typeof height === 'number' ? height : 320;

  return (
    <div
      className={className}
      style={{ flex: 1, width: '100%', height, minWidth: 0, ...slot?.root }}
    >
      <ResponsiveContainer width="100%" height={rcHeight}>
        <LineChart
          data={data}
          margin={{ top: 8, right: 16, bottom: 8, left: 8 }}
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
            cursor={t.tooltip.cursor}
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
          {series.map((s) => {
            const color = t.toneColor(s.tone ?? 'primary');
            return (
              <Line
                key={s.key}
                type="monotone"
                dataKey={s.key}
                name={s.label ?? s.key}
                stroke={color}
                strokeWidth={s.strokeWidth ?? 2}
                strokeDasharray={dashArray(s.dash)}
                dot={
                  isAnomaly
                    ? ((props: {
                        cx?: number;
                        cy?: number;
                        payload?: Record<string, unknown>;
                        index?: number;
                      }) => {
                        const { cx, cy, payload, index } = props;
                        if (
                          cx === undefined ||
                          cy === undefined ||
                          !payload ||
                          !isAnomaly(payload)
                        ) {
                          return (
                            <g
                              key={`dot-empty-${s.key}-${index ?? 0}`}
                              aria-hidden
                            />
                          );
                        }
                        return (
                          <g key={`dot-${s.key}-${index ?? 0}`}>
                            <circle
                              cx={cx}
                              cy={cy}
                              r={6}
                              fill={t.tones.error}
                              fillOpacity={0.16}
                            />
                            <circle
                              cx={cx}
                              cy={cy}
                              r={3}
                              fill={t.tones.error}
                              stroke={t.surface}
                              strokeWidth={1.5}
                            />
                          </g>
                        );
                      }) as never
                    : false
                }
                activeDot={{ r: 4, stroke: t.surface, strokeWidth: 2, fill: color }}
                isAnimationActive={!reducedMotion}
              />
            );
          })}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
