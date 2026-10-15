// /reports/jobs — async report submissions for the current browser.
//
// Source of truth split:
//   - localStorage (`job-history-store`) owns the list of jobs the user
//     submitted in this browser (server has no "list my jobs" endpoint and
//     doesn't echo back the request body, so we cache both for retry).
//   - `useQueries` polls each job's current status from the server every
//     2.5s, but only while it's still QUEUED / RUNNING. Once a job settles
//     (READY or FAILED), its query stops polling.
//
// Row actions:
//   - Download — only on READY; pulls the blob via the 302 redirect.
//   - Retry — only on FAILED; re-runs the same catalog entry with the
//     cached parameters and lands a fresh job in the table.
//   - Forget — drops the job from local history (server keeps it).
//
// `?highlight={id}` (set by the catalog drawer after submit) flashes the
// matching row so the user lands looking at "their" job.

import { useEffect, useMemo, useSyncExternalStore } from 'react';
import {
  App as AntdApp,
  Button,
  Popconfirm,
  Space,
  Table,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearch } from '@tanstack/react-router';
import {
  Copy,
  Download,
  RefreshCcw,
  RotateCcw,
  Trash2,
  X,
} from 'lucide-react';

import {
  EmptyState,
  PageHeader,
  RequestState,
  StatusPill,
  type StatusPillTone,
} from '@/components';
import { formatDateTime, getErrorMessage, isApiError } from '@/lib';

import {
  downloadJobOutput,
  fetchReportCatalog,
  fetchReportJob,
  runAsyncReport,
} from '../bff-api';
import {
  forgetSubmittedJob,
  listSubmittedJobs,
  rememberSubmittedJob,
  subscribeSubmittedJobs,
  type RememberedJob,
} from './job-history-store';
import type { ReportJob, ReportJobStatus } from '../types';

const { Text } = Typography;

const POLL_MS = 2500;

const STATUS_LABEL: Record<ReportJobStatus, string> = {
  QUEUED: 'Queued',
  RUNNING: 'Running',
  READY: 'Ready',
  FAILED: 'Failed',
};

const STATUS_TONE: Record<ReportJobStatus, StatusPillTone> = {
  QUEUED: 'neutral',
  RUNNING: 'info',
  READY: 'success',
  FAILED: 'error',
};

/** A row in the table — joins the cached submission with whatever the
 *  server says about the job right now. */
interface JobRow {
  remembered: RememberedJob;
  /** Latest server-side state, or null while loading / on error. */
  job: ReportJob | null;
  /** When the status query failed (e.g. 404 — job pruned). */
  fetchError: unknown;
  fetching: boolean;
}

function formatBytes(bytes: number | null): string {
  if (bytes === null || bytes === undefined) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

export function JobsPage() {
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const search = useSearch({ from: '/_app/reports/jobs' });
  const highlight = search.highlight;

  // Subscribe to the local store so a fresh submission from another route
  // (or another tab) shows up live without a manual refresh.
  const remembered = useSyncExternalStore(
    subscribeSubmittedJobs,
    listSubmittedJobs,
    listSubmittedJobs,
  );

  // Catalog drives Retry — we need the entry's parameter schema to
  // re-run with the cached values. Cached at the same key the catalog
  // page uses, so this is free when the user navigates here from /reports.
  const catalogQuery = useQuery({
    queryKey: ['reports', 'catalog'],
    queryFn: ({ signal }) => fetchReportCatalog({ signal }),
    staleTime: 5 * 60 * 1000,
    enabled: remembered.length > 0,
  });

  const statusQueries = useQueries({
    queries: remembered.map((r) => ({
      queryKey: ['reports', 'jobs', r.jobId] as const,
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        fetchReportJob(r.jobId, { signal }),
      // Poll while the job hasn't settled. `false` halts the interval —
      // TanStack stops scheduling further fetches but the query stays cached.
      refetchInterval: (query: { state: { data: ReportJob | undefined } }) => {
        const data = query.state.data;
        if (!data) return POLL_MS;
        return data.status === 'QUEUED' || data.status === 'RUNNING'
          ? POLL_MS
          : false;
      },
      // The page is meant to track live state — never serve stale data.
      staleTime: 0,
    })),
  });

  const rows = useMemo<JobRow[]>(
    () =>
      remembered.map((r, i) => {
        const q = statusQueries[i];
        return {
          remembered: r,
          job: (q?.data as ReportJob | undefined) ?? null,
          fetchError: q?.error,
          fetching: !!q?.isFetching,
        };
      }),
    [remembered, statusQueries],
  );

  const isAnyPolling = rows.some(
    (r) => r.job && (r.job.status === 'QUEUED' || r.job.status === 'RUNNING'),
  );

  // Strip the highlight param after a beat so a refresh doesn't keep
  // animating the row. Soft cleanup — the URL update isn't critical.
  useEffect(() => {
    if (!highlight) return;
    const timer = setTimeout(() => {
      const url = new URL(window.location.href);
      url.searchParams.delete('highlight');
      window.history.replaceState(null, '', url.toString());
    }, 4000);
    return () => clearTimeout(timer);
  }, [highlight]);

  const onDownload = async (job: ReportJob) => {
    try {
      await downloadJobOutput(job);
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  const onCopyError = async (errorMessage: string | null) => {
    try {
      await navigator.clipboard.writeText(errorMessage ?? '');
      message.success('Error copied');
    } catch {
      message.error("Couldn't copy to clipboard");
    }
  };

  const onForget = (jobId: number) => {
    forgetSubmittedJob(jobId);
    queryClient.removeQueries({ queryKey: ['reports', 'jobs', jobId] });
  };

  const onRetry = async (row: JobRow) => {
    const entry = catalogQuery.data?.find(
      (e) => e.id === row.remembered.entryId,
    );
    if (!entry) {
      message.error(
        "Can't retry — this report is no longer available to you.",
      );
      return;
    }
    try {
      const fresh = await runAsyncReport(entry, row.remembered.parameters);
      rememberSubmittedJob(fresh, entry, row.remembered.parameters);
      message.success(`Re-queued · job #${fresh.id}`);
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  const columns: TableColumnsType<JobRow> = [
    {
      title: 'Report',
      key: 'title',
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Text strong>{row.remembered.title}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            #{row.remembered.jobId}
          </Text>
        </div>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 140,
      render: (_v, row) => {
        if (row.fetchError && !row.job) {
          const msg = isApiError(row.fetchError) && row.fetchError.status === 404
            ? 'Pruned'
            : 'Unknown';
          return (
            <Tooltip title={getErrorMessage(row.fetchError)}>
              <StatusPill tone="neutral">{msg}</StatusPill>
            </Tooltip>
          );
        }
        if (!row.job) {
          return <StatusPill tone="neutral">Loading…</StatusPill>;
        }
        return (
          <StatusPill tone={STATUS_TONE[row.job.status]}>
            {STATUS_LABEL[row.job.status]}
          </StatusPill>
        );
      },
    },
    {
      title: 'Submitted',
      key: 'submittedAt',
      width: 200,
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {formatDateTime(row.remembered.submittedAt)}
        </Text>
      ),
    },
    {
      title: 'Size',
      key: 'size',
      width: 100,
      align: 'right',
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {formatBytes(row.job?.outputSize ?? null)}
        </Text>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 240,
      render: (_v, row) => {
        const status = row.job?.status;
        return (
          <Space size={4}>
            {status === 'READY' ? (
              <Button
                size="small"
                type="primary"
                icon={<Download size={12} strokeWidth={1.75} />}
                onClick={() => onDownload(row.job!)}
              >
                Download
              </Button>
            ) : null}
            {status === 'FAILED' ? (
              <>
                <Button
                  size="small"
                  icon={<RotateCcw size={12} strokeWidth={1.75} />}
                  onClick={() => onRetry(row)}
                  disabled={catalogQuery.isLoading}
                >
                  Retry
                </Button>
                <Tooltip title={row.job?.errorMessage ?? 'No details'}>
                  <Button
                    size="small"
                    icon={<Copy size={12} strokeWidth={1.75} />}
                    onClick={() => onCopyError(row.job?.errorMessage ?? null)}
                    aria-label="Copy error"
                  />
                </Tooltip>
              </>
            ) : null}
            <Popconfirm
              title="Forget this job?"
              description="The server keeps it; this just hides it from your list."
              okText="Forget"
              cancelText="Cancel"
              onConfirm={() => onForget(row.remembered.jobId)}
            >
              <Button
                size="small"
                type="text"
                icon={<X size={12} strokeWidth={1.75} />}
                aria-label="Forget"
              />
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="Insights"
        title="Job history"
        description="Background reports submitted from this browser. Jobs that aren't ready yet will refresh automatically."
        actions={
          <Space>
            {isAnyPolling ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                Live · refreshing every {POLL_MS / 1000}s
              </Text>
            ) : null}
            <Button
              icon={<RefreshCcw size={14} strokeWidth={1.75} />}
              onClick={() =>
                queryClient.invalidateQueries({ queryKey: ['reports', 'jobs'] })
              }
              loading={statusQueries.some((q) => q.isFetching)}
              aria-label="Refresh statuses"
            />
            <Popconfirm
              title="Clear job history?"
              description="Removes every job from your local list. Server-side records are unaffected."
              okText="Clear"
              cancelText="Cancel"
              okButtonProps={{ danger: true }}
              disabled={remembered.length === 0}
              onConfirm={() => {
                for (const r of remembered) onForget(r.jobId);
              }}
            >
              <Button
                danger
                disabled={remembered.length === 0}
                icon={<Trash2 size={14} strokeWidth={1.75} />}
              >
                Clear
              </Button>
            </Popconfirm>
          </Space>
        }
      />

      <RequestState
        loading={false}
        empty={remembered.length === 0}
        emptyFallback={
          <EmptyState
            title="No background reports yet"
            description="When you run a report that takes a while, it'll appear here while it's preparing."
          />
        }
      >
        <Table<JobRow>
          rowKey={(row) => row.remembered.jobId}
          dataSource={rows}
          columns={columns}
          pagination={false}
          // Scroll wide columns inside the table, not the page.
          scroll={{ x: 'max-content' }}
          onRow={(row) =>
            highlight && row.remembered.jobId === highlight
              ? {
                  style: {
                    background:
                      'color-mix(in srgb, var(--app-accent-grad-from) 12%, transparent)',
                    transition: 'background var(--app-transition-slow)',
                  },
                }
              : {}
          }
        />
      </RequestState>
    </Space>
  );
}
