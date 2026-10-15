import { theme as antdTheme } from 'antd';
import { ChevronRight } from 'lucide-react';
import type { CSSProperties, ReactNode } from 'react';

const { useToken } = antdTheme;

export type OrgPathSegment = {
  label: ReactNode;
  /** Optional code muted next to the label. */
  code?: string | null;
};

export type OrgPathProps = {
  /** Ordered segments, e.g. [department, section]. Falsy entries are skipped. */
  segments: (OrgPathSegment | null | undefined | false)[];
  /** Compact dot separator instead of chevron. */
  separator?: 'chevron' | 'dot' | 'slash';
  /** Tighter font + spacing for table rows. */
  compact?: boolean;
  className?: string;
  style?: CSSProperties;
};

export function OrgPath({
  segments,
  separator = 'chevron',
  compact = false,
  className,
  style,
}: OrgPathProps) {
  const { token } = useToken();
  const visible = segments.filter(
    (s): s is OrgPathSegment => Boolean(s) && Boolean((s as OrgPathSegment).label),
  );
  if (visible.length === 0) return null;

  const fontSize = compact ? 12 : 13;
  const sepColor = token.colorTextQuaternary;

  const sepNode =
    separator === 'chevron' ? (
      <ChevronRight size={compact ? 12 : 13} color={sepColor} aria-hidden />
    ) : (
      <span style={{ color: sepColor }}>{separator === 'dot' ? '·' : '/'}</span>
    );

  return (
    <span
      className={className}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: compact ? 6 : 8,
        fontSize,
        color: token.colorTextSecondary,
        minWidth: 0,
        ...style,
      }}
    >
      {visible.map((seg, i) => (
        <span
          key={i}
          style={{
            display: 'inline-flex',
            alignItems: 'baseline',
            gap: 4,
            minWidth: 0,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          <span style={{ color: token.colorTextHeading, fontWeight: 500 }}>
            {seg.label}
          </span>
          {seg.code ? (
            <span style={{ color: token.colorTextTertiary, fontSize: fontSize - 1 }}>
              {seg.code}
            </span>
          ) : null}
          {i < visible.length - 1 ? (
            <span style={{ marginLeft: compact ? 4 : 6, display: 'inline-flex' }}>
              {sepNode}
            </span>
          ) : null}
        </span>
      ))}
    </span>
  );
}
