import { Typography } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';

import { alphaMix, useChartTheme, useReducedMotion, type ChartTone } from './theme';

const { Text } = Typography;

export interface MetricRingSegment {
  value: number;
  tone: ChartTone;
  label?: string;
}

export interface MetricRingProps {
  /** Filled value. Ignored when `segments` is provided. */
  value?: number;
  /** Total / capacity. Required when `value` is provided. */
  max?: number;
  /** Multi-segment ring (e.g. used / pending / remaining). */
  segments?: MetricRingSegment[];
  /** Single-segment tone. Default 'primary'. */
  tone?: ChartTone;
  /** Centre label — usually the headline figure. */
  label?: ReactNode;
  /** Sub-label below the centre figure. */
  caption?: ReactNode;
  /** Diameter in px. Default 140. */
  size?: number;
  /** Stroke thickness as fraction of diameter (0–0.5). Default 0.16. */
  thickness?: number;
  /** Show recharts tooltip on hover over slices. Auto-enabled when `segments` is provided. */
  tooltip?: boolean;
  /** Optional formatter for tooltip values. Receives the raw segment value. */
  tooltipValueFormatter?: (value: number, name: string) => string;
  className?: string;
  styles?: {
    root?: CSSProperties;
    label?: CSSProperties;
    caption?: CSSProperties;
  };
}

/**
 * Donut ring with a centre label. Use for single-value progress (leave
 * balance, % complete) or multi-segment splits via `segments`.
 *
 * The remainder slice (when `value < max`) is drawn at low alpha so the
 * ring still reads as full — empty space looks like missing data.
 */
export function MetricRing({
  value,
  max,
  segments,
  tone = 'primary',
  label,
  caption,
  size = 140,
  thickness = 0.16,
  tooltip,
  tooltipValueFormatter,
  className,
  styles: slot,
}: MetricRingProps) {
  const t = useChartTheme();
  const reducedMotion = useReducedMotion();

  const outerRadius = size / 2;
  const innerRadius = outerRadius - outerRadius * Math.min(Math.max(thickness, 0.05), 0.5);

  let pieData: { name: string; value: number; fill: string; _placeholder?: boolean }[];

  if (segments && segments.length > 0) {
    pieData = segments.map((s, i) => ({
      name: s.label ?? `seg-${i}`,
      value: Math.max(0, s.value),
      fill: t.toneColor(s.tone),
    }));
    const total = pieData.reduce((acc, x) => acc + x.value, 0);
    if (total === 0) {
      pieData = [{ name: 'empty', value: 1, fill: t.surfaceMuted, _placeholder: true }];
    }
  } else {
    const v = Math.max(0, value ?? 0);
    const m = Math.max(v, max ?? v);
    const remainder = Math.max(0, m - v);
    const filled = v;
    const fillColor = t.toneColor(tone);
    pieData = [
      { name: 'filled', value: filled, fill: fillColor },
      { name: 'remainder', value: remainder, fill: alphaMix(fillColor, 14) },
    ];
    if (filled === 0 && remainder === 0) {
      pieData = [{ name: 'empty', value: 1, fill: t.surfaceMuted, _placeholder: true }];
    }
  }

  const tooltipEnabled =
    (tooltip ?? (segments && segments.length > 0)) && !pieData[0]?._placeholder;

  return (
    <div
      className={className}
      style={{
        position: 'relative',
        width: size,
        height: size,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        ...slot?.root,
      }}
    >
      <ResponsiveContainer width={size} height={size}>
        <PieChart>
          <Pie
            data={pieData}
            cx="50%"
            cy="50%"
            startAngle={90}
            endAngle={-270}
            innerRadius={innerRadius}
            outerRadius={outerRadius}
            paddingAngle={pieData.length > 2 ? 1 : 0}
            dataKey="value"
            isAnimationActive={!reducedMotion}
            stroke="none"
          >
            {pieData.map((d) => (
              <Cell key={d.name} fill={d.fill} />
            ))}
          </Pie>
          {tooltipEnabled ? (
            <Tooltip
              contentStyle={{ ...t.tooltip.contentStyle, fontSize: 12, padding: '6px 10px' }}
              itemStyle={t.tooltip.itemStyle}
              labelStyle={t.tooltip.labelStyle}
              cursor={false}
              wrapperStyle={{ zIndex: 20, outline: 'none' }}
              allowEscapeViewBox={{ x: true, y: true }}
              formatter={
                tooltipValueFormatter
                  ? ((v: unknown, key: unknown) =>
                      tooltipValueFormatter(Number(v), String(key))) as never
                  : ((v: unknown) => String(v)) as never
              }
            />
          ) : null}
        </PieChart>
      </ResponsiveContainer>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 2,
          pointerEvents: 'none',
        }}
      >
        {label !== undefined ? (
          <span
            style={{
              fontSize: Math.max(14, size * 0.18),
              fontWeight: 700,
              color: t.text,
              fontVariantNumeric: 'tabular-nums',
              lineHeight: 1.05,
              letterSpacing: '-0.01em',
              ...slot?.label,
            }}
          >
            {label}
          </span>
        ) : null}
        {caption !== undefined ? (
          <Text
            type="secondary"
            style={{
              fontSize: 11,
              letterSpacing: '0.04em',
              textTransform: 'uppercase',
              ...slot?.caption,
            }}
          >
            {caption}
          </Text>
        ) : null}
      </div>
    </div>
  );
}
