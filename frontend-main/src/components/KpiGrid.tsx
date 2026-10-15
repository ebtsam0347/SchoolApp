import type { CSSProperties, ReactNode } from 'react';

export type KpiGridProps = {
  children: ReactNode;
  /**
   * Minimum column width before the grid wraps. Default 220.
   *
   * Capped at 100% of the container on narrow viewports — a value wider than
   * the phone content box would otherwise push the page sideways rather than
   * collapsing to a single column. See the grid-template below.
   */
  minColumnWidth?: number;
  /** Gap between cards in px. Default 16. */
  gap?: number;
  className?: string;
  style?: CSSProperties;
};

export function KpiGrid({
  children,
  minColumnWidth = 220,
  gap = 16,
  className,
  style,
}: KpiGridProps) {
  return (
    <div
      className={className}
      style={{
        display: 'grid',
        // A grid track cannot shrink below its minmax() minimum, so a bare
        // `minmax(420px, 1fr)` stays 420px wide inside a ~358px phone content
        // box and pushes the whole page sideways. Wrapping the minimum in
        // `min(…, 100%)` lets it collapse to a single full-width column
        // instead. Measured in-browser at 358px: 420 overflowed by 62px and
        // 380 by 22px; both resolve to 358px with this form, and values
        // already below the container width are unaffected.
        gridTemplateColumns: `repeat(auto-fit, minmax(min(${minColumnWidth}px, 100%), 1fr))`,
        gap,
        ...style,
      }}
    >
      {children}
    </div>
  );
}
