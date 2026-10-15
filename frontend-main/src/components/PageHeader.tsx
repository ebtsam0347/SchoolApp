import { theme as antdTheme, Typography } from 'antd';
import type { CSSProperties, ReactNode } from 'react';

const { Title, Text } = Typography;
const { useToken } = antdTheme;

export type PageHeaderProps = {
  title: ReactNode;
  /** Small uppercase tracked-out line above the title — date, breadcrumb, status, etc. */
  eyebrow?: ReactNode;
  /** Optional secondary description below the title. */
  description?: ReactNode;
  /** Right-aligned action buttons / controls. */
  actions?: ReactNode;
  /** Heading level for the title element. Defaults to 2 (page-level). */
  level?: 1 | 2 | 3 | 4 | 5;
  className?: string;
  styles?: {
    root?: CSSProperties;
    eyebrow?: CSSProperties;
    title?: CSSProperties;
    description?: CSSProperties;
    actions?: CSSProperties;
  };
};

export function PageHeader({
  title,
  eyebrow,
  description,
  actions,
  level = 2,
  className,
  styles: slot,
}: PageHeaderProps) {
  const { token } = useToken();

  return (
    <div
      className={className}
      style={{
        display: 'flex',
        alignItems: 'flex-end',
        justifyContent: 'space-between',
        gap: 16,
        flexWrap: 'wrap',
        ...slot?.root,
      }}
    >
      <div style={{ minWidth: 0, flex: 1 }}>
        {eyebrow ? (
          <Text
            type="secondary"
            style={{
              fontSize: 11,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              ...slot?.eyebrow,
            }}
          >
            {eyebrow}
          </Text>
        ) : null}
        <Title
          level={level}
          style={{
            margin: eyebrow ? '4px 0 0 0' : 0,
            ...slot?.title,
          }}
        >
          {title}
        </Title>
        {description ? (
          <Text
            type="secondary"
            style={{
              display: 'block',
              marginTop: 2,
              color: token.colorTextSecondary,
              ...slot?.description,
            }}
          >
            {description}
          </Text>
        ) : null}
      </div>
      {actions ? (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            flexShrink: 0,
            // The root above wraps the actions block onto its own line on a
            // narrow screen, but without this the block itself stays one
            // unbreakable row — several Selects or buttons then push the page
            // sideways. `maxWidth` caps it at the container so the wrap can
            // actually take effect; on a wide viewport everything still fits
            // on one line, so nothing changes.
            flexWrap: 'wrap',
            justifyContent: 'flex-end',
            maxWidth: '100%',
            ...slot?.actions,
          }}
        >
          {actions}
        </div>
      ) : null}
    </div>
  );
}
