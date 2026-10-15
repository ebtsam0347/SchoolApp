// Label for an AntD <Segmented /> option: lucide icon + text + optional
// count badge. Used by inbox / leaves / time-today / delegations to render
// segmented controls that show pending counts.
//
// Usage inside a Segmented options array:
//   { label: <SegmentLabel icon={Plane} text="Leaves" count={data.counts.leaves} />,
//     value: 'LEAVES' }

import { Badge, theme as antdTheme } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

const { useToken } = antdTheme;

export type SegmentLabelProps = {
  icon?: LucideIcon;
  text: ReactNode;
  /** Badge count. Hidden when 0 / undefined. */
  count?: number;
  /**
   * Badge tone. Defaults to 'warning' (matches inbox usage). Pass 'primary'
   * for neutral/info contexts.
   */
  tone?: 'warning' | 'primary' | 'error' | 'success' | 'info';
  iconSize?: number;
  className?: string;
  style?: CSSProperties;
};

export function SegmentLabel({
  icon: Icon,
  text,
  count,
  tone = 'warning',
  iconSize = 13,
  className,
  style,
}: SegmentLabelProps) {
  const { token } = useToken();
  const badgeColor =
    tone === 'primary'
      ? token.colorPrimary
      : tone === 'error'
        ? token.colorError
        : tone === 'success'
          ? token.colorSuccess
          : tone === 'info'
            ? token.colorInfo
            : token.colorWarning;

  return (
    <span
      className={className}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 'var(--app-space-sm)',
        ...style,
      }}
    >
      {Icon ? <Icon size={iconSize} strokeWidth={1.75} /> : null}
      {text}
      {count && count > 0 ? (
        <Badge count={count} style={{ background: badgeColor }} />
      ) : null}
    </span>
  );
}
