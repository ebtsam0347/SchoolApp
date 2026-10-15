/**
 * Chart theming — single source of truth for axis / grid / tooltip styling
 * across all chart primitives. Driven entirely by `theme.useToken()` so a
 * theme switch reflows charts without per-component knowledge.
 *
 * Multi-series palette is derived from AntD's semantic tokens (5 hues +
 * neutral). Anything past 6 series will repeat — if a chart truly needs
 * 8 distinct hues, add `--app-chart-c1…c8` to themes.css and read them
 * here. Until then, 6 is the practical ceiling.
 */
import { theme as antdTheme } from 'antd';
import { useEffect, useState } from 'react';

const { useToken } = antdTheme;

export type ChartTone =
  | 'primary'
  | 'success'
  | 'warning'
  | 'error'
  | 'info'
  | 'neutral';

export interface ChartTheme {
  tones: Record<ChartTone, string>;
  palette: string[];
  alpha: (color: string, percent: number) => string;
  toneColor: (tone: ChartTone) => string;
  axis: {
    stroke: string;
    tick: { fill: string; fontSize: number };
  };
  grid: {
    stroke: string;
    strokeDasharray: string;
  };
  tooltip: {
    contentStyle: Record<string, string | number>;
    itemStyle: Record<string, string | number>;
    labelStyle: Record<string, string | number>;
    cursor: { stroke: string; strokeWidth: number; fill: string };
  };
  surface: string;
  surfaceMuted: string;
  text: string;
  textMuted: string;
  textTertiary: string;
}

export const alphaMix = (color: string, percent: number) =>
  `color-mix(in srgb, ${color} ${percent}%, transparent)`;

export function useChartTheme(): ChartTheme {
  const { token } = useToken();

  const tones: Record<ChartTone, string> = {
    primary: token.colorPrimary,
    success: token.colorSuccess,
    warning: token.colorWarning,
    error: token.colorError,
    info: token.colorInfo,
    neutral: token.colorTextTertiary,
  };

  const palette: string[] = [
    token.colorPrimary,
    token.colorSuccess,
    token.colorWarning,
    token.colorInfo,
    token.colorError,
    token.colorTextTertiary,
  ];

  return {
    tones,
    palette,
    alpha: alphaMix,
    toneColor: (tone) => tones[tone],
    axis: {
      stroke: token.colorBorderSecondary,
      tick: { fill: token.colorTextTertiary, fontSize: 11 },
    },
    grid: {
      stroke: token.colorBorderSecondary,
      strokeDasharray: '3 3',
    },
    tooltip: {
      contentStyle: {
        background: token.colorBgElevated,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadius,
        boxShadow: token.boxShadowSecondary,
        padding: '8px 10px',
        fontSize: 12,
      },
      itemStyle: {
        color: token.colorText,
        padding: 0,
      },
      labelStyle: {
        color: token.colorTextSecondary,
        fontWeight: 500,
        marginBottom: 4,
      },
      cursor: {
        stroke: token.colorBorderSecondary,
        strokeWidth: 1,
        fill: alphaMix(token.colorPrimary, 6),
      },
    },
    surface: token.colorBgContainer,
    surfaceMuted: token.colorFillTertiary,
    text: token.colorText,
    textMuted: token.colorTextSecondary,
    textTertiary: token.colorTextTertiary,
  };
}

export function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  });

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
    const handler = (e: MediaQueryListEvent) => setReduced(e.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, []);

  return reduced;
}
