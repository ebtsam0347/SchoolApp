import { theme as antdTheme, Typography } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

const { Title, Text } = Typography;
const { useToken } = antdTheme;

export type EmptyStateProps = {
  title: ReactNode;
  description?: ReactNode;
  /** Icon shown in a tinted circular badge. Ignored if `illustration` is provided. */
  icon?: LucideIcon;
  /** Custom illustration node (replaces the icon badge). */
  illustration?: ReactNode;
  /** Primary action — usually a Button. */
  action?: ReactNode;
  /** Secondary action — usually a Button type="text" or "link". */
  secondaryAction?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
  styles?: {
    root?: CSSProperties;
    illustration?: CSSProperties;
    title?: CSSProperties;
    description?: CSSProperties;
    actions?: CSSProperties;
  };
};

export function EmptyState({
  title,
  description,
  icon: Icon,
  illustration,
  action,
  secondaryAction,
  size = 'md',
  className,
  styles: slot,
}: EmptyStateProps) {
  const { token } = useToken();

  const dims =
    size === 'sm'
      ? { padY: 24, badge: 48, iconSize: 20, titleLevel: 5 as const, gap: 8 }
      : size === 'lg'
        ? { padY: 56, badge: 80, iconSize: 32, titleLevel: 3 as const, gap: 14 }
        : { padY: 40, badge: 64, iconSize: 26, titleLevel: 4 as const, gap: 12 };

  return (
    <div
      className={className}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        textAlign: 'center',
        paddingTop: dims.padY,
        paddingBottom: dims.padY,
        gap: dims.gap,
        ...slot?.root,
      }}
    >
      {illustration ? (
        <div style={slot?.illustration}>{illustration}</div>
      ) : Icon ? (
        <div
          style={{
            width: dims.badge,
            height: dims.badge,
            borderRadius: '50%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: token.colorFillTertiary,
            color: token.colorTextTertiary,
            ...slot?.illustration,
          }}
          aria-hidden
        >
          <Icon size={dims.iconSize} strokeWidth={1.75} />
        </div>
      ) : null}
      <Title level={dims.titleLevel} style={{ margin: 0, ...slot?.title }}>
        {title}
      </Title>
      {description ? (
        <Text
          type="secondary"
          style={{ maxWidth: 420, ...slot?.description }}
        >
          {description}
        </Text>
      ) : null}
      {action || secondaryAction ? (
        <div
          style={{
            marginTop: 4,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            flexWrap: 'wrap',
            justifyContent: 'center',
            ...slot?.actions,
          }}
        >
          {action}
          {secondaryAction}
        </div>
      ) : null}
    </div>
  );
}
