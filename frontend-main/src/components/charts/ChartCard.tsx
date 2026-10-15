import { Card, theme as antdTheme, Typography } from 'antd';
import type { CSSProperties, ReactNode } from 'react';

const { Title, Text } = Typography;
const { useToken } = antdTheme;

export interface ChartCardProps {
  title?: ReactNode;
  subtitle?: ReactNode;
  /** Right-aligned controls in the title row. */
  actions?: ReactNode;
  /** Body min-height in px or CSS value. Default 320. */
  height?: number | string;
  /** Heading level for the title. Default 5. */
  level?: 1 | 2 | 3 | 4 | 5;
  className?: string;
  styles?: {
    root?: CSSProperties;
    header?: CSSProperties;
    body?: CSSProperties;
  };
  children: ReactNode;
}

/**
 * Chart frame: card chrome + optional title/subtitle/actions header + body
 * sized for a chart. The body's ResponsiveContainer (inside child) sizes to
 * `height`; ChartCard owns the chrome, the chart owns the canvas.
 */
export function ChartCard({
  title,
  subtitle,
  actions,
  height = 320,
  level = 5,
  className,
  styles: slot,
  children,
}: ChartCardProps) {
  const { token } = useToken();
  const hasHeader = Boolean(title || subtitle || actions);

  return (
    <Card
      className={className}
      style={slot?.root}
      styles={{
        body: {
          padding: 0,
          display: 'flex',
          flexDirection: 'column',
        },
      }}
    >
      {hasHeader ? (
        <div
          style={{
            padding: '14px 18px 10px',
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: 12,
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
            ...slot?.header,
          }}
        >
          <div style={{ minWidth: 0, flex: 1 }}>
            {title ? (
              <Title level={level} style={{ margin: 0 }}>
                {title}
              </Title>
            ) : null}
            {subtitle ? (
              <Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
                {subtitle}
              </Text>
            ) : null}
          </div>
          {actions ? (
            <div
              style={{
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
            >
              {actions}
            </div>
          ) : null}
        </div>
      ) : null}
      <div
        style={{
          padding: 12,
          minHeight: height,
          height,
          display: 'flex',
          ...slot?.body,
        }}
      >
        {children}
      </div>
    </Card>
  );
}
