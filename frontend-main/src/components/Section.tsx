import { theme as antdTheme, Typography } from 'antd';
import { ChevronDown } from 'lucide-react';
import { useState, type CSSProperties, type ReactNode } from 'react';

const { Title, Text } = Typography;
const { useToken } = antdTheme;

export type SectionProps = {
  title: ReactNode;
  subtitle?: ReactNode;
  /** Right-aligned controls in the title row. */
  actions?: ReactNode;
  /** Heading level for the title. Default 5. */
  level?: 1 | 2 | 3 | 4 | 5;
  /** If true, body can be toggled open/closed. */
  collapsible?: boolean;
  /** Initial collapsed state when `collapsible`. Default false. */
  defaultCollapsed?: boolean;
  children: ReactNode;
  className?: string;
  styles?: {
    root?: CSSProperties;
    header?: CSSProperties;
    title?: CSSProperties;
    subtitle?: CSSProperties;
    body?: CSSProperties;
  };
};

export function Section({
  title,
  subtitle,
  actions,
  level = 5,
  collapsible = false,
  defaultCollapsed = false,
  children,
  className,
  styles: slot,
}: SectionProps) {
  const { token } = useToken();
  const [collapsed, setCollapsed] = useState(defaultCollapsed);

  const titleNode = (
    <Title level={level} style={{ margin: 0, ...slot?.title }}>
      {title}
    </Title>
  );

  return (
    <div className={className} style={slot?.root}>
      <div
        style={{
          marginBottom: 12,
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 12,
          ...slot?.header,
        }}
      >
        <div style={{ minWidth: 0, flex: 1 }}>
          {collapsible ? (
            <button
              type="button"
              onClick={() => setCollapsed((c) => !c)}
              aria-expanded={!collapsed}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                padding: 0,
                background: 'transparent',
                border: 'none',
                cursor: 'pointer',
                color: 'inherit',
              }}
            >
              <ChevronDown
                size={16}
                strokeWidth={2}
                color={token.colorTextSecondary}
                aria-hidden
                style={{
                  transform: collapsed ? 'rotate(-90deg)' : 'rotate(0deg)',
                  transition: 'transform 180ms cubic-bezier(0.4, 0, 0.2, 1)',
                }}
              />
              {titleNode}
            </button>
          ) : (
            titleNode
          )}
          {subtitle ? (
            <Text
              type="secondary"
              style={{
                display: 'block',
                fontSize: 12,
                ...slot?.subtitle,
              }}
            >
              {subtitle}
            </Text>
          ) : null}
        </div>
        {actions ? (
          <div style={{ flexShrink: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
            {actions}
          </div>
        ) : null}
      </div>
      {collapsible && collapsed ? null : <div style={slot?.body}>{children}</div>}
    </div>
  );
}
