// Drawer scaffold for entity-detail views (leave detail, payslip detail,
// employee detail). Provides consistent chrome (size, destroy-on-hidden,
// sticky footer) and integrates RequestState for the body when a query
// hasn't loaded yet.
//
// Why: every feature was hand-rolling
//   <Drawer title=… open=… onClose=… size=520 destroyOnHidden>
//     {isLoading ? <Spin/> : isError ? <Alert/> : <body/>}
//   </Drawer>
// — same scaffolding repeated. DetailDrawer owns the chrome; the caller
// owns the data and the body.
//
// Pairs with `useUrlDrawerId(routeFrom, key)` for URL-search-param sync —
// see usage in src/routes/_app/time/leaves.tsx.

import { Drawer } from 'antd';
import type { ReactNode } from 'react';
import { useIsDesktop } from '@/lib';
import { RequestState } from './RequestState';

export type DetailDrawerProps = {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  /** Top-right slot — typically a status pill or a small icon-button group. */
  extra?: ReactNode;
  /** Bottom slot. Renders inside Drawer's footer with safe-area padding. */
  footer?: ReactNode;
  /** Width preset. Defaults to 520. */
  size?: number;
  /** Explicit responsive drawer width (any CSS length, e.g. a `clamp()`).
   * Takes precedence over `size`. */
  width?: number | string;
  /** Body slot — what to render once data is loaded. */
  children: ReactNode;

  /** Optional RequestState integration: while loading/error/empty, the
   *  body is replaced with a centered Spin / Alert / EmptyState. */
  loading?: boolean;
  error?: unknown;
  empty?: boolean;
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
  errorTitle?: ReactNode;
  loadingFallback?: ReactNode;

  /** Allow callers to opt out of `destroyOnHidden` (default: true). */
  keepMounted?: boolean;
};

export function DetailDrawer({
  open,
  onClose,
  title,
  extra,
  footer,
  size = 520,
  width,
  children,
  loading,
  error,
  empty,
  emptyTitle,
  emptyDescription,
  errorTitle,
  loadingFallback,
  keepMounted = false,
}: DetailDrawerProps) {
  // Mirrors DrawerForm: the `size` preset is a desktop width. Handing a
  // fixed 520px to a ~390px phone overflows it, so fall back to full-width
  // there. An explicit `width` is respected as-is — callers passing a
  // responsive value like `clamp(...)` have already solved this themselves.
  const isDesktop = useIsDesktop();
  const resolvedSize = width ?? (isDesktop ? size : '100%');

  const hasState = loading || error !== undefined || empty;
  const body = hasState ? (
    <RequestState
      loading={loading}
      error={error}
      empty={empty}
      emptyTitle={emptyTitle}
      emptyDescription={emptyDescription}
      errorTitle={errorTitle}
      loadingFallback={loadingFallback}
    >
      {children}
    </RequestState>
  ) : (
    children
  );

  return (
    <Drawer
      title={title}
      extra={extra}
      open={open}
      onClose={onClose}
      // antd v6 deprecated `width` — `size` now takes any CSS length too,
      // so the explicit width just feeds straight into it.
      size={resolvedSize}
      destroyOnHidden={!keepMounted}
      footer={footer}
    >
      {body}
    </Drawer>
  );
}
