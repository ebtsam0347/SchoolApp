// Single wrapper for the loading / error / empty / content branching that
// every list page hand-rolls. Keeps the cross-state UX consistent and lifts
// the boilerplate out of routes.
//
// Three escape hatches:
//   - `loadingFallback` swap the default Spin (e.g. <TableSkeleton/>)
//   - `emptyFallback` render any node instead of <EmptyState/>
//   - `errorFallback(err)` render any node instead of the standard <Alert/>
//
// Convenience props that cover the most common cases:
//   - `variant="table"` swaps the loading default to a TableSkeleton
//   - `onRetry` adds a Retry button to the default error Alert
//   - `filterActive` + `onClearFilters` swaps the empty-state copy + action

import { Alert, Button, Skeleton, Spin, theme as antdTheme } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import { RotateCcw } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { getErrorMessage } from '@/lib';
import { EmptyState } from './EmptyState';

export type RequestStateProps = {
  loading?: boolean;
  error?: unknown;
  /** When `true` — and not loading and no error — render the empty fallback. */
  empty?: boolean;
  /** Slot for the success state. Only rendered when no other state matches. */
  children: ReactNode;

  /** Empty-state copy + icon (used unless `emptyFallback` provided). */
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
  emptyIcon?: LucideIcon;
  emptyAction?: ReactNode;
  emptyFallback?: ReactNode;

  /**
   * When true, the empty state copy switches to "no matches" + a clear-filters
   * action (if `onClearFilters` is set). Lets routes keep one RequestState
   * but tell users *why* the list is empty when filters are active.
   */
  filterActive?: boolean;
  onClearFilters?: () => void;

  /** Error-state copy (used unless `errorFallback` provided). */
  errorTitle?: ReactNode;
  errorFallback?: (err: unknown) => ReactNode;
  /** Adds a Retry button on the default error Alert — usually `query.refetch`. */
  onRetry?: () => void;

  /** Loading override — defaults to a centered Spin (or table skeleton). */
  loadingFallback?: ReactNode;
  /** When `"table"`, the default loading state becomes a TableSkeleton. */
  variant?: 'spinner' | 'table';
  /** Skeleton row count when variant="table". */
  loadingRows?: number;

  className?: string;
  style?: CSSProperties;
};

export function RequestState({
  loading,
  error,
  empty,
  children,
  emptyTitle = 'Nothing here yet',
  emptyDescription,
  emptyIcon,
  emptyAction,
  emptyFallback,
  filterActive,
  onClearFilters,
  errorTitle = "Couldn't load",
  errorFallback,
  onRetry,
  loadingFallback,
  variant = 'spinner',
  loadingRows = 6,
  className,
  style,
}: RequestStateProps) {
  if (error) {
    return (
      <div className={className} style={style}>
        {errorFallback ? (
          errorFallback(error)
        ) : (
          <Alert
            type="error"
            showIcon
            title={errorTitle}
            description={getErrorMessage(error)}
            action={
              onRetry ? (
                <Button
                  size="small"
                  icon={<RotateCcw size={14} strokeWidth={1.75} />}
                  onClick={onRetry}
                >
                  Retry
                </Button>
              ) : undefined
            }
          />
        )}
      </div>
    );
  }

  if (loading) {
    if (loadingFallback) {
      return (
        <div className={className} style={style}>
          {loadingFallback}
        </div>
      );
    }
    if (variant === 'table') {
      return (
        <div className={className} style={style}>
          <TableSkeleton rows={loadingRows} />
        </div>
      );
    }
    return (
      <div
        className={className}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 'var(--app-space-xl)',
          ...style,
        }}
      >
        <Spin />
      </div>
    );
  }

  if (empty) {
    const showFilteredCopy = filterActive === true;
    const finalTitle = showFilteredCopy ? 'No matches' : emptyTitle;
    const finalDescription = showFilteredCopy
      ? 'Try clearing filters or widening your search.'
      : emptyDescription;
    const finalAction =
      showFilteredCopy && onClearFilters ? (
        <Button onClick={onClearFilters}>Clear filters</Button>
      ) : (
        emptyAction
      );

    return (
      <div className={className} style={style}>
        {emptyFallback ?? (
          <EmptyState
            title={finalTitle}
            description={finalDescription}
            icon={emptyIcon}
            action={finalAction}
          />
        )}
      </div>
    );
  }

  return <>{children}</>;
}

/**
 * Lightweight table-shaped skeleton — header row + N body rows of placeholder
 * bars. Intended as the default loading state for list pages so the layout
 * doesn't collapse to a centered Spin.
 */
export function TableSkeleton({
  rows = 6,
  columns = 5,
}: {
  rows?: number;
  columns?: number;
}) {
  const { token } = antdTheme.useToken();
  const widths = ['28%', '18%', '14%', '20%', '12%', '8%'];
  return (
    <div
      style={{
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadiusLG,
        overflow: 'hidden',
        background: token.colorBgContainer,
      }}
      aria-busy="true"
      aria-live="polite"
      role="status"
    >
      <div
        style={{
          display: 'flex',
          gap: 16,
          padding: '12px 16px',
          background: token.colorFillTertiary,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        {Array.from({ length: columns }).map((_, i) => (
          <div key={i} style={{ flex: `0 0 ${widths[i % widths.length]}` }}>
            <Skeleton.Input active size="small" block />
          </div>
        ))}
      </div>
      {Array.from({ length: rows }).map((_, r) => (
        <div
          key={r}
          style={{
            display: 'flex',
            gap: 16,
            padding: '14px 16px',
            borderBottom:
              r === rows - 1 ? 'none' : `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          {Array.from({ length: columns }).map((_, c) => (
            <div key={c} style={{ flex: `0 0 ${widths[c % widths.length]}` }}>
              <Skeleton.Input active size="small" block />
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
