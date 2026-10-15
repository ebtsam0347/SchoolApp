import { theme as antdTheme, Typography } from 'antd';
import { Link } from '@tanstack/react-router';
import { CheckCircle2, ChevronRight, type LucideIcon } from 'lucide-react';
import type { CSSProperties, ReactNode } from 'react';

import { EmptyState } from './EmptyState';
import { alphaMix, type ChartTone } from './charts';

import './NeedsYouList.css';

const { Text } = Typography;
const { useToken } = antdTheme;

export interface NeedsYouItem {
  id: string | number;
  title: ReactNode;
  description?: ReactNode;
  /** Right-aligned slot for badges (ExpiryBadge, MoneyCell, etc). */
  meta?: ReactNode;
  /** Inline action slot (ApproveRejectActions, Buttons). When set, the row chrome stops being clickable. */
  actions?: ReactNode;
  /** Tone for the leading marker dot. Default 'primary'. */
  tone?: ChartTone;
  /** Optional leading slot — usually a PersonCell avatar or LucideIcon node. */
  leading?: ReactNode;
  /** Click target. Internal router link. Mutually exclusive with `actions`. */
  to?: string;
  /** External link / direct href. */
  href?: string;
  /** Click handler for non-link rows. */
  onClick?: () => void;
}

export interface NeedsYouListProps {
  items: NeedsYouItem[];
  /** Empty state copy. */
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
  emptyIcon?: LucideIcon;
  /** "View all" router link below the list. Hidden when items is empty. */
  viewAllTo?: string;
  viewAllLabel?: string;
  /** Cap rendered rows. The rest are gestured at via the view-all CTA. Default 5. */
  maxItems?: number;
  className?: string;
  styles?: {
    root?: CSSProperties;
    item?: CSSProperties;
    viewAll?: CSSProperties;
  };
}

/**
 * Actionable list for "needs your attention" sections on dashboards.
 * Rows are deep-link or action-bearing (mutually exclusive). Empty state
 * renders a celebratory check, not a blank panel.
 */
export function NeedsYouList({
  items,
  emptyTitle = 'All clear',
  emptyDescription = 'Nothing waiting on you right now.',
  emptyIcon = CheckCircle2,
  viewAllTo,
  viewAllLabel = 'View all',
  maxItems = 5,
  className,
  styles: slot,
}: NeedsYouListProps) {
  const { token } = useToken();

  if (items.length === 0) {
    return (
      <div className={className} style={slot?.root}>
        <EmptyState
          size="sm"
          icon={emptyIcon}
          title={emptyTitle}
          description={emptyDescription}
        />
      </div>
    );
  }

  const visible = items.slice(0, maxItems);

  const rootStyle: CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    gap: 4,
    ['--needs-you-row-hover' as never]: token.colorFillTertiary,
    ['--needs-you-row-focus' as never]: token.colorPrimaryBorder,
    ...slot?.root,
  };

  return (
    <div className={className} style={rootStyle}>
      <ul
        style={{
          listStyle: 'none',
          margin: 0,
          padding: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
        }}
      >
        {visible.map((item) => (
          <li key={item.id}>
            <NeedsYouRow item={item} style={slot?.item} />
          </li>
        ))}
      </ul>
      {viewAllTo ? (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            paddingTop: 4,
            ...slot?.viewAll,
          }}
        >
          <Link
            to={viewAllTo}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 4,
              fontSize: 13,
              fontWeight: 500,
              color: token.colorPrimary,
            }}
          >
            {viewAllLabel}
            <ChevronRight size={14} strokeWidth={2} aria-hidden />
          </Link>
        </div>
      ) : null}
    </div>
  );
}

function NeedsYouRow({
  item,
  style,
}: {
  item: NeedsYouItem;
  style?: CSSProperties;
}) {
  const { token } = useToken();
  const tone = item.tone ?? 'primary';

  const toneColor =
    tone === 'success'
      ? token.colorSuccess
      : tone === 'warning'
        ? token.colorWarning
        : tone === 'error'
          ? token.colorError
          : tone === 'info'
            ? token.colorInfo
            : tone === 'neutral'
              ? token.colorTextTertiary
              : token.colorPrimary;

  const interactive = !item.actions && (item.to || item.href || item.onClick);

  const body = (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '10px 12px',
        borderRadius: token.borderRadius,
        background: 'transparent',
        transition: 'background 160ms ease',
        ...style,
      }}
      data-interactive={interactive ? 'true' : 'false'}
    >
      <span
        aria-hidden
        style={{
          width: 8,
          height: 8,
          borderRadius: '50%',
          background: toneColor,
          boxShadow: `0 0 0 3px ${alphaMix(toneColor, 18)}`,
          flexShrink: 0,
        }}
      />
      {item.leading ? <div style={{ flexShrink: 0 }}>{item.leading}</div> : null}
      <div style={{ minWidth: 0, flex: 1 }}>
        <div
          style={{
            fontSize: 14,
            fontWeight: 500,
            color: token.colorTextHeading,
            lineHeight: 1.3,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {item.title}
        </div>
        {item.description ? (
          <Text
            type="secondary"
            style={{
              display: 'block',
              fontSize: 12,
              lineHeight: 1.4,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {item.description}
          </Text>
        ) : null}
      </div>
      {item.meta ? <div style={{ flexShrink: 0 }}>{item.meta}</div> : null}
      {item.actions ? (
        <div style={{ flexShrink: 0, display: 'flex', alignItems: 'center', gap: 6 }}>
          {item.actions}
        </div>
      ) : null}
      {interactive && !item.actions ? (
        <ChevronRight
          size={16}
          strokeWidth={1.75}
          color={token.colorTextTertiary}
          aria-hidden
          style={{ flexShrink: 0 }}
        />
      ) : null}
    </div>
  );

  if (item.actions) return body;

  if (item.to) {
    return (
      <Link
        to={item.to}
        style={{ color: 'inherit', textDecoration: 'none', display: 'block' }}
        className="needs-you-row"
      >
        {body}
      </Link>
    );
  }
  if (item.href) {
    return (
      <a
        href={item.href}
        style={{ color: 'inherit', textDecoration: 'none', display: 'block' }}
        className="needs-you-row"
      >
        {body}
      </a>
    );
  }
  if (item.onClick) {
    return (
      <button
        type="button"
        onClick={item.onClick}
        className="needs-you-row"
        style={{
          all: 'unset',
          display: 'block',
          width: '100%',
          cursor: 'pointer',
          color: 'inherit',
        }}
      >
        {body}
      </button>
    );
  }
  return body;
}
