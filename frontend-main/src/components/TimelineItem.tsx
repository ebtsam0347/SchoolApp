import { theme as antdTheme, Typography } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

import { LOCALE } from '@/config/locale';
import type { StatCardTone } from './StatCard';

const { Text } = Typography;
const { useToken } = antdTheme;

export type TimelineItemProps = {
  title: ReactNode;
  /** Pre-formatted timestamp string, or a Date for default formatting. */
  timestamp?: ReactNode | Date;
  /** Author / actor — name only (for now). */
  author?: { name: string };
  description?: ReactNode;
  /** Icon shown in the marker. Falls back to a small dot. */
  icon?: LucideIcon;
  /** Tone for the marker — colors the dot/icon, not the text. */
  tone?: StatCardTone;
  /** True for the last item in the list — suppresses the connecting line. */
  isLast?: boolean;
  /** Extra content rendered below `description` — typed details, diff, etc. */
  children?: ReactNode;
  className?: string;
  styles?: {
    root?: CSSProperties;
    marker?: CSSProperties;
    body?: CSSProperties;
  };
};

function formatTimestamp(ts: ReactNode | Date | undefined): ReactNode {
  if (ts === undefined || ts === null) return null;
  if (ts instanceof Date) {
    return new Intl.DateTimeFormat(LOCALE, {
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    }).format(ts);
  }
  return ts;
}

export function TimelineItem({
  title,
  timestamp,
  author,
  description,
  icon: Icon,
  tone = 'neutral',
  isLast = false,
  children,
  className,
  styles: slot,
}: TimelineItemProps) {
  const { token } = useToken();

  const toneColor =
    tone === 'primary'
      ? token.colorPrimary
      : tone === 'success'
        ? token.colorSuccess
        : tone === 'warning'
          ? token.colorWarning
          : tone === 'error'
            ? token.colorError
            : tone === 'info'
              ? token.colorInfo
              : token.colorTextTertiary;

  const markerSize = Icon ? 28 : 12;
  const markerOffsetY = Icon ? 0 : 8;

  return (
    <div
      className={className}
      style={{
        position: 'relative',
        display: 'flex',
        alignItems: 'flex-start',
        gap: 12,
        paddingBottom: isLast ? 0 : 18,
        ...slot?.root,
      }}
    >
      {/* Marker column — dot/icon + connecting line */}
      <div
        style={{
          position: 'relative',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          flexShrink: 0,
          width: 28,
          paddingTop: markerOffsetY,
          ...slot?.marker,
        }}
      >
        {Icon ? (
          <span
            style={{
              width: markerSize,
              height: markerSize,
              borderRadius: '50%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: `color-mix(in srgb, ${toneColor} 14%, transparent)`,
              border: `1px solid ${toneColor}`,
              flexShrink: 0,
            }}
            aria-hidden
          >
            <Icon size={14} strokeWidth={2} color={toneColor} />
          </span>
        ) : (
          <span
            style={{
              width: markerSize,
              height: markerSize,
              borderRadius: '50%',
              background: toneColor,
              flexShrink: 0,
            }}
            aria-hidden
          />
        )}
        {!isLast ? (
          <span
            style={{
              flex: 1,
              width: 1,
              marginTop: 4,
              minHeight: 12,
              background: token.colorBorderSecondary,
            }}
            aria-hidden
          />
        ) : null}
      </div>

      {/* Content column */}
      <div style={{ flex: 1, minWidth: 0, ...slot?.body }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'baseline',
            justifyContent: 'space-between',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <Text strong style={{ fontSize: 13 }}>
            {title}
          </Text>
          {timestamp !== undefined && timestamp !== null ? (
            <Text
              type="secondary"
              style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums', flexShrink: 0 }}
            >
              {formatTimestamp(timestamp)}
            </Text>
          ) : null}
        </div>
        {author ? (
          <Text type="secondary" style={{ display: 'block', fontSize: 12, marginTop: 2 }}>
            by {author.name}
          </Text>
        ) : null}
        {description ? (
          <div style={{ marginTop: 4, color: token.colorTextSecondary, fontSize: 13 }}>
            {description}
          </div>
        ) : null}
        {children ? <div style={{ marginTop: 8 }}>{children}</div> : null}
      </div>
    </div>
  );
}
