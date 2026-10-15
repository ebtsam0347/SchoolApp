import type { CSSProperties, ReactNode } from 'react';
import {
  CartesianGrid,
  Cell,
  ReferenceLine,
  ResponsiveContainer,
  Scatter,
  ScatterChart,
  Tooltip,
  XAxis,
  YAxis,
  ZAxis,
} from 'recharts';

import { ChartEmpty } from './ChartEmpty';
import { alphaMix, useChartTheme, useReducedMotion, type ChartTone } from './theme';

export interface AnomalyScatterPoint {
  /** Stable React key + identity. */
  id: string | number;
  /** Hover label. */
  label: string;
  /** Sub-label rendered below the headline in the tooltip. */
  caption?: string;
  /** X-axis value — typically the prior-period figure. */
  x: number;
  /** Y-axis value — typically variance vs prior, as fraction or percent. */
  y: number;
  /** Tone override. Falls back to threshold-based resolution. */
  tone?: ChartTone;
}

export interface AnomalyScatterProps {
  data: AnomalyScatterPoint[];
  /**
   * Symmetric threshold band on the y-axis. Points with `|y| > threshold`
   * render in `outlierTone`; everything else in `baselineTone`.
   */
  threshold?: number;
  /** Tone for in-band points. Default 'neutral'. */
  baselineTone?: ChartTone;
  /** Tone for out-of-band points. Default 'error'. */
  outlierTone?: ChartTone;
  /** Render dashed lines at +/- threshold. Default true. */
  showThresholdLines?: boolean;
  /** Render a dashed line at y=0 to anchor "no change". Default true. */
  showZeroLine?: boolean;
  /** Marker radius. Default 6. */
  pointRadius?: number;
  height?: number | string;
  xLabel?: string;
  yLabel?: string;
  xTickFormatter?: (value: number) => string;
  yTickFormatter?: (value: number) => string;
  /** Tooltip value formatters. */
  formatX?: (value: number) => string;
  formatY?: (value: number) => string;
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
  className?: string;
  styles?: { root?: CSSProperties };
}

/**
 * Variance scatter — each point is one entity (employee, department,
 * vendor) plotted against a baseline + a delta. Far better than a list
 * of "10 anomalies" because outliers are visually obvious and similar
 * variances cluster.
 *
 * The outlier-vs-baseline split is purely visual; callers decide what
 * counts as an anomaly via `threshold`. The tooltip carries the
 * identity so users can pivot to the underlying record.
 */
export function AnomalyScatter({
  data,
  threshold,
  baselineTone = 'neutral',
  outlierTone = 'error',
  showThresholdLines = true,
  showZeroLine = true,
  pointRadius = 6,
  height = 360,
  xLabel,
  yLabel,
  xTickFormatter,
  yTickFormatter,
  formatX,
  formatY,
  emptyTitle,
  emptyDescription,
  className,
  styles: slot,
}: AnomalyScatterProps) {
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

  const baseline = t.toneColor(baselineTone);
  const outlier = t.toneColor(outlierTone);

  const isOutlier = (p: AnomalyScatterPoint): boolean =>
    threshold !== undefined && Math.abs(p.y) > threshold;

  // See TrendLine for the rationale on passing pixel height directly.
  const rcHeight = typeof height === 'number' ? height : 360;

  return (
    <div className={className} style={{ flex: 1, width: '100%', height, minWidth: 0, ...slot?.root }}>
      <ResponsiveContainer width="100%" height={rcHeight}>
        <ScatterChart margin={{ top: 16, right: 24, bottom: xLabel ? 28 : 16, left: yLabel ? 32 : 8 }}>
          <CartesianGrid
            stroke={t.grid.stroke}
            strokeDasharray={t.grid.strokeDasharray}
          />
          <XAxis
            type="number"
            dataKey="x"
            name={xLabel}
            tick={t.axis.tick}
            stroke={t.axis.stroke}
            tickLine={false}
            axisLine={{ stroke: t.axis.stroke }}
            tickFormatter={xTickFormatter}
            label={
              xLabel
                ? {
                    value: xLabel,
                    position: 'insideBottom',
                    offset: -8,
                    fill: t.textMuted,
                    fontSize: 11,
                  }
                : undefined
            }
          />
          <YAxis
            type="number"
            dataKey="y"
            name={yLabel}
            tick={t.axis.tick}
            stroke={t.axis.stroke}
            tickLine={false}
            axisLine={false}
            tickFormatter={yTickFormatter}
            width={48}
            label={
              yLabel
                ? {
                    value: yLabel,
                    angle: -90,
                    position: 'insideLeft',
                    offset: 8,
                    fill: t.textMuted,
                    fontSize: 11,
                  }
                : undefined
            }
          />
          <ZAxis range={[pointRadius * pointRadius, pointRadius * pointRadius]} />
          {showZeroLine ? (
            <ReferenceLine
              y={0}
              stroke={t.axis.stroke}
              strokeDasharray="4 4"
            />
          ) : null}
          {showThresholdLines && threshold !== undefined ? (
            <>
              <ReferenceLine
                y={threshold}
                stroke={alphaMix(outlier, 60)}
                strokeDasharray="3 3"
              />
              <ReferenceLine
                y={-threshold}
                stroke={alphaMix(outlier, 60)}
                strokeDasharray="3 3"
              />
            </>
          ) : null}
          <Tooltip
            cursor={{ stroke: t.axis.stroke, strokeDasharray: '4 4' }}
            contentStyle={t.tooltip.contentStyle}
            itemStyle={t.tooltip.itemStyle}
            labelStyle={t.tooltip.labelStyle}
            content={({ active, payload }) => {
              if (!active || !payload || payload.length === 0) return null;
              const point = payload[0].payload as AnomalyScatterPoint;
              const x = formatX ? formatX(point.x) : String(point.x);
              const y = formatY ? formatY(point.y) : String(point.y);
              return (
                <div style={t.tooltip.contentStyle}>
                  <div style={{ ...t.tooltip.labelStyle }}>{point.label}</div>
                  <div style={{ ...t.tooltip.itemStyle, fontSize: 12 }}>
                    {xLabel ?? 'X'}: <strong>{x}</strong>
                  </div>
                  <div style={{ ...t.tooltip.itemStyle, fontSize: 12 }}>
                    {yLabel ?? 'Y'}: <strong>{y}</strong>
                  </div>
                  {point.caption ? (
                    <div
                      style={{
                        ...t.tooltip.itemStyle,
                        fontSize: 11,
                        opacity: 0.85,
                        marginTop: 4,
                      }}
                    >
                      {point.caption}
                    </div>
                  ) : null}
                </div>
              );
            }}
          />
          <Scatter
            data={data}
            isAnimationActive={!reducedMotion}
            shape="circle"
          >
            {data.map((p) => {
              const fill = p.tone
                ? t.toneColor(p.tone)
                : isOutlier(p)
                  ? outlier
                  : baseline;
              return (
                <Cell
                  key={String(p.id)}
                  fill={fill}
                  stroke={t.surface}
                  strokeWidth={1.5}
                />
              );
            })}
          </Scatter>
        </ScatterChart>
      </ResponsiveContainer>
    </div>
  );
}
