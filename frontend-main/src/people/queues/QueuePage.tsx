// Shared scaffold for the four /people/queues/* routes
// (probation, certifications, disciplinary, suspensions).
//
// They all share the same shape: a header, an optional KPI strip, an
// optional filter row, and a single table. Only the columns + filter
// content differ between routes — those stay in the route file. This
// component owns the chrome.

import { Alert, Button, Space, Tooltip } from 'antd';
import { RefreshCcw } from 'lucide-react';
import type { ReactNode } from 'react';

import { KpiGrid, PageHeader } from '@/components';
import { getErrorMessage } from '@/lib';

export type QueuePageProps = {
  title: ReactNode;
  description?: ReactNode;
  /** Defaults to "HR queue". Pass `null` to drop the eyebrow entirely. */
  eyebrow?: ReactNode;
  /** Right-aligned header actions (e.g. a "Back to People" button). */
  headerActions?: ReactNode;
  /** When set, a Refresh button is rendered before headerActions. */
  onRefresh?: () => void;
  /** Drives the Refresh button's loading state. */
  isFetching?: boolean;
  /** Stat cards. Wrapped in <KpiGrid/>. Pass `false`/`null` to omit. */
  kpis?: ReactNode;
  /** Optional filter row above the table. */
  filters?: ReactNode;
  /** When set, an error Alert is shown above the children. */
  error?: unknown;
  /** Override the default "Couldn't load queue" message. */
  errorMessage?: string;
  /** The table (and any modals it owns). */
  children: ReactNode;
};

export function QueuePage({
  title,
  description,
  eyebrow = 'HR queue',
  headerActions,
  onRefresh,
  isFetching,
  kpis,
  filters,
  error,
  errorMessage = "Couldn't load queue",
  children,
}: QueuePageProps) {
  const refreshButton = onRefresh ? (
    <Tooltip title="Refresh">
      <Button
        icon={<RefreshCcw size={14} strokeWidth={1.75} />}
        onClick={onRefresh}
        loading={isFetching}
        aria-label="Refresh"
      />
    </Tooltip>
  ) : null;

  const actions =
    refreshButton && headerActions ? (
      <Space>
        {refreshButton}
        {headerActions}
      </Space>
    ) : (
      (refreshButton ?? headerActions)
    );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <PageHeader
        eyebrow={eyebrow ?? undefined}
        title={title}
        description={description}
        actions={actions}
      />

      {kpis ? <KpiGrid>{kpis}</KpiGrid> : null}

      {filters ? <div>{filters}</div> : null}

      {error ? (
        <Alert
          type="error"
          showIcon
          title={errorMessage}
          description={getErrorMessage(error)}
        />
      ) : null}

      {children}
    </div>
  );
}
