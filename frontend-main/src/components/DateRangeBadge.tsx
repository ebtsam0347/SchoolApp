import { theme as antdTheme } from 'antd';
import type { CSSProperties } from 'react';

import { LOCALE } from '@/config/locale';

const { useToken } = antdTheme;

export type DateRangeBadgeProps = {
  start: Date | string;
  end: Date | string;
  /** Date format style. Default 'short' → "Apr 5, 2026". */
  format?: 'short' | 'medium' | 'long';
  /** Append a duration tail like "· 7d". Default true. */
  showDuration?: boolean;
  /** Separator between start and end. Default ' – '. */
  separator?: string;
  className?: string;
  style?: CSSProperties;
};

type DateFormat = NonNullable<DateRangeBadgeProps['format']>;

const FORMATTERS: Record<DateFormat, Intl.DateTimeFormat> = {
  short: new Intl.DateTimeFormat(LOCALE, { month: 'short', day: 'numeric' }),
  medium: new Intl.DateTimeFormat(LOCALE, { month: 'short', day: 'numeric', year: 'numeric' }),
  long: new Intl.DateTimeFormat(LOCALE, { month: 'long', day: 'numeric', year: 'numeric' }),
};

function toDate(value: Date | string): Date {
  return value instanceof Date ? value : new Date(value);
}

function formatDate(date: Date, format: DateFormat) {
  return FORMATTERS[format].format(date);
}

export function DateRangeBadge({
  start,
  end,
  format = 'short',
  showDuration = true,
  separator = ' – ',
  className,
  style,
}: DateRangeBadgeProps) {
  const { token } = useToken();

  const startDate = toDate(start);
  const endDate = toDate(end);

  const startLabel = formatDate(startDate, format);
  const endLabel = formatDate(endDate, format);

  const days = Math.max(
    1,
    Math.round((endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24)) + 1,
  );

  return (
    <span
      className={className}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '2px 8px',
        fontSize: 12,
        fontWeight: 500,
        color: token.colorTextSecondary,
        background: token.colorFillQuaternary,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadiusSM,
        fontVariantNumeric: 'tabular-nums',
        ...style,
      }}
    >
      <span>
        {startLabel}
        {separator}
        {endLabel}
      </span>
      {showDuration ? (
        <span style={{ color: token.colorTextTertiary }}>· {days}d</span>
      ) : null}
    </span>
  );
}
