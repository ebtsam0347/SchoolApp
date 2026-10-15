import { theme as antdTheme } from 'antd';
import { useMemo, type CSSProperties } from 'react';

import { CURRENCY, LOCALE } from '@/config/locale';

const { useToken } = antdTheme;

// `Intl.NumberFormat` is ~150µs to construct; cache by the small set of
// option permutations MoneyCell actually uses so list-heavy pages don't
// rebuild a formatter per row.
const FORMATTER_CACHE = new Map<string, Intl.NumberFormat>();
function getMoneyFormatter(
  compact: boolean,
  maxFrac: number,
  minFrac: number,
  showSign: boolean,
): Intl.NumberFormat {
  const key = `${compact ? 'c' : 's'}|${maxFrac}|${minFrac}|${showSign ? '+' : '-'}`;
  let f = FORMATTER_CACHE.get(key);
  if (!f) {
    f = new Intl.NumberFormat(LOCALE, {
      style: 'currency',
      currency: CURRENCY,
      notation: compact ? 'compact' : 'standard',
      maximumFractionDigits: maxFrac,
      minimumFractionDigits: minFrac,
      signDisplay: showSign ? 'exceptZero' : 'auto',
    });
    FORMATTER_CACHE.set(key, f);
  }
  return f;
}

export type MoneyCellProps = {
  amount: number;
  /** Use compact notation — e.g. Rs 14.2M. Default false. */
  compact?: boolean;
  /** Override max fraction digits. Default 2 in normal, 1 in compact mode. */
  maximumFractionDigits?: number;
  minimumFractionDigits?: number;
  /** Always render an explicit + on positive values. Default false. */
  showSign?: boolean;
  /** Color positive green and negative red via tokens. Default false. */
  coloredSign?: boolean;
  className?: string;
  style?: CSSProperties;
};

export function MoneyCell({
  amount,
  compact = false,
  maximumFractionDigits,
  minimumFractionDigits,
  showSign = false,
  coloredSign = false,
  className,
  style,
}: MoneyCellProps) {
  const { token } = useToken();

  const maxFrac = maximumFractionDigits ?? (compact ? 1 : 2);
  const minFrac = minimumFractionDigits ?? 0;

  // A missing or non-numeric amount renders as an em dash rather than
  // "RsNaN". Backend contract drift (a renamed money field) used to leak
  // straight into the cell as NaN; this keeps it from reaching the user,
  // though the caller still needs fixing at the source.
  const formatted = useMemo(
    () =>
      typeof amount === 'number' && Number.isFinite(amount)
        ? getMoneyFormatter(compact, maxFrac, minFrac, showSign).format(amount)
        : '—',
    [amount, compact, maxFrac, minFrac, showSign],
  );

  const color = coloredSign
    ? amount > 0
      ? token.colorSuccess
      : amount < 0
        ? token.colorError
        : undefined
    : undefined;

  return (
    <span
      className={className}
      style={{
        fontVariantNumeric: 'tabular-nums',
        color,
        ...style,
      }}
    >
      {formatted}
    </span>
  );
}
