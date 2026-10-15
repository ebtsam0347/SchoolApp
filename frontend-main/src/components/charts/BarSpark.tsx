import type { CSSProperties } from 'react';
import { Bar, BarChart, ResponsiveContainer } from 'recharts';

import { useChartTheme, useReducedMotion, type ChartTone } from './theme';

export interface BarSparkProps {
  /** Series of values, oldest → newest. */
  data: number[];
  tone?: ChartTone;
  /** Height in px. Default 28. */
  height?: number;
  /** Width: number for fixed px, string for responsive. Default 80. */
  width?: number | string;
  /** Bar corner radius. Default 1. */
  radius?: number;
  className?: string;
  styles?: { root?: CSSProperties };
}

/**
 * Inline sparkline of bars. No axes, no grid, no tooltip. Better than
 * LineSpark when the values are discrete (counts, statuses) rather than
 * continuous (percentages, latencies).
 */
export function BarSpark({
  data,
  tone = 'primary',
  height = 28,
  width = 80,
  radius = 1,
  className,
  styles: slot,
}: BarSparkProps) {
  const t = useChartTheme();
  const reducedMotion = useReducedMotion();
  const color = t.toneColor(tone);

  if (!data || data.length === 0) {
    return (
      <div
        className={className}
        style={{ width, height, ...slot?.root }}
        aria-hidden
      />
    );
  }

  const series = data.map((v, i) => ({ i, v }));
  const margin = { top: 2, right: 2, bottom: 2, left: 2 };
  const isResponsive = typeof width === 'string';

  return (
    <div
      className={className}
      style={{
        width,
        height,
        display: 'inline-block',
        verticalAlign: 'middle',
        ...slot?.root,
      }}
      aria-hidden
    >
      <ResponsiveContainer
        width={isResponsive ? '100%' : (width as number)}
        height={height}
      >
        <BarChart data={series} margin={margin} barCategoryGap="15%">
          <Bar
            dataKey="v"
            fill={color}
            radius={radius}
            isAnimationActive={!reducedMotion}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
