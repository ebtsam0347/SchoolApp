import { Typography } from 'antd';
import type { CSSProperties, ReactNode } from 'react';

import { useChartTheme } from './theme';

const { Text } = Typography;

export interface ChartEmptyProps {
  title?: ReactNode;
  description?: ReactNode;
  className?: string;
  styles?: { root?: CSSProperties };
}

/**
 * Empty-state for chart bodies. Renders a flat dotted baseline + a small
 * label, never a bare axis frame. Drop in instead of a chart when the
 * series array is empty.
 */
export function ChartEmpty({
  title = 'No data yet',
  description,
  className,
  styles: slot,
}: ChartEmptyProps) {
  const t = useChartTheme();

  return (
    <div
      className={className}
      style={{
        flex: 1,
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 4,
        textAlign: 'center',
        padding: 16,
        minHeight: 120,
        ...slot?.root,
      }}
      role="status"
      aria-live="polite"
    >
      <svg
        aria-hidden
        width="100%"
        height="100%"
        preserveAspectRatio="none"
        style={{
          position: 'absolute',
          inset: 0,
          pointerEvents: 'none',
        }}
      >
        <line
          x1="6%"
          x2="94%"
          y1="80%"
          y2="80%"
          stroke={t.axis.stroke}
          strokeWidth={1}
          strokeDasharray="4 4"
        />
      </svg>
      <Text style={{ fontSize: 13, fontWeight: 500, color: t.text }}>
        {title}
      </Text>
      {description ? (
        <Text type="secondary" style={{ fontSize: 12, maxWidth: 320 }}>
          {description}
        </Text>
      ) : null}
    </div>
  );
}
