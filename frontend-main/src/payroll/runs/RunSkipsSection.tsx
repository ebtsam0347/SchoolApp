// Skipped-employees panel on the payroll run detail page.
//
// Compute persists one row per employee it could not produce a payslip for
// (missing salary, missing bank details, etc.). HR fixes the underlying
// data, then retries — either per row or all at once. Successful retries
// drop the row and add a payslip; failures get their reason updated in
// place. Run stays COMPUTED throughout, so this panel is the canonical
// "fix-and-retry" surface, sitting between the totals and the payslip
// table on /payroll/runs/$id. Payslips HR undoes by hand land here too
// (`MANUALLY_UNDONE`); clicking a row opens RunSkipDrawer, where HR can
// preview the salary and skip this month's permanent-advance deduction
// before retrying.
//
// Only mounted once the run leaves COMPUTING — the parent polls the
// lightweight /summary endpoint during compute, then loads this list once
// when status reaches COMPUTED. No timer-driven refetch lives here.

import { useState } from 'react';
import {
  App as AntdApp,
  Button,
  Empty,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { Link } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ExternalLink, RotateCcw } from 'lucide-react';

import { RequestState, Section, employeeColumn } from '@/components';
import { getErrorMessage } from '@/lib';
import {
  MANUALLY_UNDONE,
  payrollRunsApi,
  type PayrollRunSkipDto,
} from '@/payroll';
import { RunSkipDrawer } from './RunSkipDrawer';

/**
 * Maps a compute-skip `errorCode` to the page where HR can fix the
 * underlying data. The salary tab is the default — it bundles basic
 * salary, components, bank details, and payroll settings, which covers
 * the bulk of compute preconditions. Codes that point elsewhere (e.g.
 * attendance gaps) get an explicit case here.
 */
function fixHrefFor(errorCode: string, employeeId: number): string {
  if (errorCode.startsWith('ATTENDANCE_')) {
    return `/people/${employeeId}/attendance`;
  }
  return `/people/${employeeId}/salary`;
}

const { Text } = Typography;

export type RunSkipsSectionProps = {
  runId: number;
  /** Skip count from the run row. Drives whether we even mount this section. */
  skippedCount: number;
  /** Run is only retryable in COMPUTED. */
  retryable: boolean;
  /** Caller's CASL `manage:payroll` resolution. */
  canWrite: boolean;
  /** Bumps the parent run query so the KPI tile + state machine refresh too. */
  onChanged: () => void;
};

type SkipRow = PayrollRunSkipDto;

export function RunSkipsSection({
  runId,
  skippedCount,
  retryable,
  canWrite,
  onChanged,
}: RunSkipsSectionProps) {
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [openSkip, setOpenSkip] = useState<SkipRow | null>(null);

  const skipsQuery = useQuery({
    queryKey: ['payroll', 'run', runId, 'skips', page, pageSize],
    queryFn: ({ signal }) =>
      payrollRunsApi.skips(runId, { page, size: pageSize }, signal),
    enabled: skippedCount > 0,
  });

  const refresh = () => {
    void queryClient.invalidateQueries({
      queryKey: ['payroll', 'run', runId, 'skips'],
    });
    onChanged();
  };

  const retryAll = useMutation({
    mutationFn: () => payrollRunsApi.retrySkips(runId, {}),
    onSuccess: (run) => {
      const remaining = run.skippedCount;
      const resolved = skippedCount - remaining;
      if (remaining === 0) {
        message.success(`All ${resolved} skip${resolved === 1 ? '' : 's'} resolved.`);
      } else if (resolved > 0) {
        message.success(
          `${resolved} resolved · ${remaining} still skipped.`,
        );
      } else {
        message.info(`No skips resolved — ${remaining} still need attention.`);
      }
      refresh();
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const retryOne = useMutation({
    mutationFn: (employeeId: number) =>
      payrollRunsApi.retrySkips(runId, { employeeIds: [employeeId] }),
    onSuccess: () => {
      // The run row's skippedCount reflects the whole run, not this row;
      // the skips refetch is what reveals whether this employee was
      // resolved or got an updated reason. Keep the toast neutral.
      message.success('Retry submitted.');
      setOpenSkip(null);
      refresh();
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  // Hidden when the run had no skips.
  if (skippedCount === 0) return null;

  const rows = skipsQuery.data?.content ?? [];
  const totalElements = skipsQuery.data?.totalElements ?? skippedCount;

  const columns: TableColumnsType<SkipRow> = [
    employeeColumn<SkipRow>({
      employee: (r) => ({
        id: r.employeeId,
        fullName: r.employeeFullName,
        employeeCode: r.employeeCode,
      }),
      to: (r) => `/people/${r.employeeId}`,
    }),
    {
      title: 'Reason',
      key: 'reason',
      render: (_v, row) => (
        <Space orientation="vertical" size={2} style={{ minWidth: 0 }}>
          {row.errorCode === MANUALLY_UNDONE ? (
            <Tag color="warning" style={{ marginInlineEnd: 0 }}>
              Undone
            </Tag>
          ) : (
            <Tag color="error" style={{ marginInlineEnd: 0, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
              {row.errorCode}
            </Tag>
          )}
          <Text type="secondary" style={{ fontSize: 12 }}>
            {row.message}
          </Text>
        </Space>
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 200,
      align: 'right',
      // Keep Fix / Retry clicks from also opening the drawer.
      onCell: () => ({ onClick: (e) => e.stopPropagation() }),
      render: (_v, row) => (
        <Space size={4} wrap>
          <Link to={fixHrefFor(row.errorCode, row.employeeId)}>
            <Button size="small" icon={<ExternalLink size={12} strokeWidth={1.75} />}>
              Fix
            </Button>
          </Link>
          {canWrite && retryable && (
            <Button
              size="small"
              type="primary"
              icon={<RotateCcw size={12} strokeWidth={1.75} />}
              loading={
                retryOne.isPending && retryOne.variables === row.employeeId
              }
              onClick={() => retryOne.mutate(row.employeeId)}
            >
              Retry
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Section
      title="Skipped employees"
      subtitle={
        retryable
          ? `${totalElements} ${totalElements === 1 ? 'employee needs' : 'employees need'} data fixes before a payslip can be produced. Fix the data, then retry.`
          : `${totalElements} employee${totalElements === 1 ? '' : 's'} ${totalElements === 1 ? 'was' : 'were'} skipped during compute.`
      }
      actions={
        canWrite && retryable ? (
          <Popconfirm
            title="Retry all skipped employees?"
            description="Re-runs compute for every skipped employee. Resolved rows drop off the list; rows that still fail keep their place with an updated reason."
            okText="Retry all"
            okButtonProps={{ type: 'primary' }}
            onConfirm={() => retryAll.mutateAsync()}
          >
            <Button
              type="primary"
              icon={<RotateCcw size={14} strokeWidth={1.75} />}
              loading={retryAll.isPending}
            >
              Retry all
            </Button>
          </Popconfirm>
        ) : null
      }
    >
      <RequestState
        loading={skipsQuery.isLoading}
        error={skipsQuery.isError ? skipsQuery.error : undefined}
        errorTitle="Couldn't load skipped employees"
      >
        <Table<SkipRow>
          rowKey="id"
          dataSource={rows}
          columns={columns}
          size="small"
          onRow={(r) => ({
            onClick: () => setOpenSkip(r),
            style: { cursor: 'pointer' },
          })}
          pagination={
            totalElements > pageSize
              ? {
                  current: page + 1,
                  pageSize,
                  total: totalElements,
                  showSizeChanger: true,
                  pageSizeOptions: [20, 50, 100],
                  showTotal: (t, [from, to]) => `${from}–${to} of ${t}`,
                  onChange: (next, nextSize) => {
                    setPage(next - 1);
                    setPageSize(nextSize);
                  },
                }
              : false
          }
          locale={{
            emptyText: (
              <Empty description="No skipped employees on this run." />
            ),
          }}
          scroll={{ x: 600 }}
        />
      </RequestState>
      <RunSkipDrawer
        runId={runId}
        skip={openSkip}
        editable={retryable}
        canWrite={canWrite}
        fixHref={openSkip ? fixHrefFor(openSkip.errorCode, openSkip.employeeId) : null}
        retrying={retryOne.isPending}
        onRetry={(employeeId) => retryOne.mutate(employeeId)}
        onClose={() => setOpenSkip(null)}
      />
    </Section>
  );
}
