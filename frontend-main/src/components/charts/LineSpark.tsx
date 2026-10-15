import type { CSSProperties } from 'react';
import { Area, AreaChart, Line, LineChart, ResponsiveContainer } from 'recharts';

import { alphaMix, useChartTheme, useReducedMotion, type ChartTone } from './theme';

export interface LineSparkProps {
  /** Series of values, oldest → newest. */
  data: number[];
  tone?: ChartTone;
  /** Height in px. Default 28. */
  height?: number;
  /** Width: number for fixed px, string for responsive (e.g. '100%'). Default 80. */
  width?: number | string;
  /** Render as area chart with translucent fill. Default false. */
  area?: boolean;
  /** Stroke width. Default 1.5. */
  strokeWidth?: number;
  className?: string;
  styles?: { root?: CSSProperties };
}

/**
 * Inline sparkline. No axes, no grid, no tooltip — just the shape. Drop
 * into a card or table cell as glanceable trend context.
 */
export function LineSpark({
  data,
  tone = 'primary',
  height = 28,
  width = 80,
  area = false,
  strokeWidth = 1.5,
  className,
  styles: slot,
}: LineSparkProps) {
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

  const inner = area ? (
    <AreaChart data={series} margin={margin}>
      <Area
        type="monotone"
        dataKey="v"
        stroke={color}
        strokeWidth={strokeWidth}
        fill={alphaMix(color, 14)}
        isAnimationActive={!reducedMotion}
        dot={false}
        activeDot={false}
      />
    </AreaChart>
  ) : (
    <LineChart data={series} margin={margin}>
      <Line
        type="monotone"
        dataKey="v"
        stroke={color}
        strokeWidth={strokeWidth}
        isAnimationActive={!reducedMotion}
        dot={false}
        activeDot={false}
      />
    </LineChart>
  );

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
        {inner}
      </ResponsiveContainer>
    </div>
  );
}
