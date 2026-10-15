// /payroll/runs/$id — payroll run detail.
//
// Three layers:
//   1. RunStateMachine — visualizes the run as a finite state machine
//      (DRAFT → LOCKED → COMPUTED → APPROVED → POSTED), with the current
//      state highlighted, transient COMPUTING/POSTING animated on the edge,
//      and side-routes for Reset (loop back), Cancel, and Failed.
//   2. Run header KPIs — period, gross/net rollups (server-side summary).
//   3. Payslip table for the period. Status filter + employee-code search
//      (both server-driven via the composite), per-row "View PDF" (enabled
//      once POSTED), inline hold/unhold/release actions for PAYROLL_ADMIN.
//
// "Download all payslips (PDF)" in the header (`payroll.reports.run`)
// renders every slip of the run into one PDF on demand — works for DRAFT
// runs too. A popover picks the layout (full page / 3 per page, last choice
// remembered) and the slip language. Hidden while the run has no payslips.
//
// Polling model:
//   • While the run is COMPUTING, only the lightweight
//     GET /payroll/runs/{id}/summary endpoint is polled (status + counts
//     + totals). The heavier payslip page and the skips list are not
//     hit at all during compute.
//   • While POSTING (PDFs being generated), the summary is polled every
//     second for the "Generating PDFs n / total" counter; the status change
//     to POSTED re-keys the detail query, so the table refetches.
//   • Once status leaves COMPUTING (COMPUTED or later), the page fires
//     the detail composite (payslips page) and the skips list. They
//     refetch on user action / mutation invalidation, not on a timer.
//
// Subcomponents — RunStateMachine, RunActions, RunPayslipsTable,
// PayslipRowActions — live in src/payroll/runs/.

import { createFileRoute, Link, useParams } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  App as AntdApp,
  Button,
  Popover,
  Progress,
  Segmented,
  Space,
  Typography,
} from 'antd';
import { ChevronLeft, Download } from 'lucide-react';

import {
  FilterBar,
  KpiGrid,
  MoneyCell,
  PageHeader,
  RequestState,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { useContext } from 'react';
import type { SlipLanguage } from '@/reports/accounts/types';
import {
  SHEET_LAYOUT_HINT,
  SLIP_LAYOUT_OPTIONS,
  loadSlipLayout,
  saveSlipLayout,
  type SlipLayout,
} from '@/reports/slip-layout';
import { AbilityContext } from '@/auth';
import { getErrorMessage, isApiError, useDebounced } from '@/lib';
import {
  PAYROLL_RUN_STATUS_LABEL,
  PAYROLL_RUN_STATUS_TONE,
  PayslipDetailDrawer,
  formatDateTime,
  formatPeriodLong,
  payrollComposites,
  payrollReportsApi,
  payrollRunsApi,
  type PayslipStatus,
  type PayslipWithEmployee,
} from '@/payroll';
import {
  ATTENDANCE_STATUS_LABEL,
  ATTENDANCE_STATUS_TONE,
  attendanceBff,
  toIsoDate,
} from '@/attendance';
import {
  AttendanceDayDrawer,
  AttendanceRecordDayBody,
} from '@/attendance/admin';
import {
  RunActions,
  RunPayslipsTable,
  RunSkipsSection,
  RunStateMachine,
} from '@/payroll/runs';

const { Text } = Typography;

export const Route = createFileRoute('/_app/payroll/runs/$id')({
  component: PayrollRunDetailPage,
});

function PayrollRunDetailPage() {
  const { id: idParam } = useParams({ from: '/_app/payroll/runs/$id' });
  const id = Number(idParam);
  const ability = useContext(AbilityContext);
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const [payslipFilter, setPayslipFilter] = useState<PayslipStatus | 'ALL'>(
    'ALL',
  );
  // Server-side payslip search (`q` on the detail composite — substring
  // match on employeeCode). Debounced so typing doesn't fire a request per
  // keystroke; any change resets to the first page.
  const [searchInput, setSearchInput] = useState('');
  const payslipSearch = useDebounced(searchInput.trim(), 300);
  const [payslipPage, setPayslipPage] = useState(0);
  const [payslipPageSize, setPayslipPageSize] = useState(20);
  const [openPayslipId, setOpenPayslipId] = useState<number | null>(null);
  // Employee-cell click in the payslips table opens the shared attendance
  // day drawer (same shell the attendance dashboard uses).
  const [attendanceRow, setAttendanceRow] = useState<PayslipWithEmployee | null>(null);

  // Lightweight poll: status + counts + totals. Drives the header / KPIs
  // and gates the heavier detail call below.
  const summaryQuery = useQuery({
    queryKey: ['payroll', 'run', id, 'summary'],
    queryFn: ({ signal }) => payrollComposites.runSummary(id, signal),
    refetchInterval: (q) =>
      q.state.data?.status === 'COMPUTING'
        ? 4_000
        : q.state.data?.status === 'POSTING'
          ? 1_000
          : false,
  });

  const summaryData = summaryQuery.data;
  const status = summaryData?.status;
  const isComputing = status === 'COMPUTING';
  const isPosting = status === 'POSTING';
  // Once compute finishes, the run has payslips + (maybe) skip rows to
  // fetch. Before that, both lists are guaranteed empty.
  const postCompute = !!summaryData && !isComputing;

  const detailQuery = useQuery({
    queryKey: [
      'payroll',
      'run',
      id,
      'bff',
      // Re-keyed on status so POSTING → POSTED refreshes the table.
      status,
      payslipFilter,
      payslipSearch,
      payslipPage,
      payslipPageSize,
    ],
    queryFn: ({ signal }) =>
      payrollComposites.run(
        id,
        {
          status: payslipFilter === 'ALL' ? undefined : payslipFilter,
          q: payslipSearch || undefined,
          page: payslipPage,
          size: payslipPageSize,
        },
        signal,
      ),
    enabled: postCompute,
    // Keep the current rows on screen while a status change refetches.
    placeholderData: (prev) => prev,
  });

  const payslipsPage = detailQuery.data?.payslips;
  const payslips = payslipsPage?.content ?? [];
  // The run-detail composite row carries the whole slip (identity, bank,
  // earnings/deductions, leaves) — strictly more than GET /payslips/{id} —
  // so hand it to the drawer instead of making it refetch. Looked up by id
  // rather than held in state so a hold/release refetch re-renders it.
  const openPayslipRow =
    payslips.find((p) => p.id === openPayslipId) ?? null;

  function refreshAll() {
    void queryClient.invalidateQueries({ queryKey: ['payroll', 'run', id] });
  }
  function onMutErr(e: unknown) {
    message.error(getErrorMessage(e));
  }

  const lockAttendance = useMutation({
    mutationFn: () => payrollRunsApi.lockAttendance(id),
    onSuccess: () => {
      message.success('Attendance locked.');
      refreshAll();
    },
    onError: onMutErr,
  });
  const compute = useMutation({
    mutationFn: () => payrollRunsApi.compute(id),
    onSuccess: () => {
      message.success('Compute started.');
      refreshAll();
    },
    onError: onMutErr,
  });
  const approve = useMutation({
    mutationFn: () => payrollRunsApi.approve(id),
    onSuccess: () => {
      message.success('Run approved.');
      refreshAll();
    },
    onError: onMutErr,
  });
  const post = useMutation({
    mutationFn: () => payrollRunsApi.post(id),
    onSuccess: () => {
      message.success('Posting started — PDFs are being generated.');
      refreshAll();
    },
    onError: onMutErr,
  });
  const cancel = useMutation({
    mutationFn: () => payrollRunsApi.cancel(id),
    onSuccess: () => {
      message.success('Run cancelled.');
      refreshAll();
    },
    onError: onMutErr,
  });
  const reset = useMutation({
    mutationFn: () => payrollRunsApi.reset(id),
    onSuccess: () => {
      message.success('Run reset to Locked. Fix the data, then run Compute again.');
      refreshAll();
    },
    onError: onMutErr,
  });

  // Large runs take several seconds to render; the button spins meanwhile.
  const [pdfOpen, setPdfOpen] = useState(false);
  const [pdfLayout, setPdfLayout] = useState<SlipLayout>(loadSlipLayout);
  const [pdfLanguage, setPdfLanguage] = useState<SlipLanguage>('en');
  const downloadAllPdf = useMutation({
    mutationFn: () =>
      payrollReportsApi.downloadRunPayslipsPdf(id, { layout: pdfLayout, language: pdfLanguage }),
    onMutate: () => saveSlipLayout(pdfLayout),
    onSuccess: () => setPdfOpen(false),
    onError: (e) => {
      if (isApiError(e)) {
        console.error('Payslips PDF download failed', { correlationId: e.correlationId });
        if (e.status === 404) {
          message.error('Nothing to print for this run — it has no payslips.');
          return;
        }
      }
      message.error(getErrorMessage(e));
    },
  });

  // The transition endpoints all require `payroll.admin`. CASL maps `admin`
  // to `manage`, so a `manage:payroll` check is the equivalent FE gate.
  const canWrite = ability.can('manage', 'payroll');

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--app-space-lg)',
      }}
    >
      <PageHeader
        eyebrow={
          <Link to="/payroll/runs">
            <ChevronLeft size={12} style={{ verticalAlign: -1, marginRight: 2 }} />
            Runs
          </Link>
        }
        title={
          summaryData
            ? `${formatPeriodLong(summaryData.periodYear, summaryData.periodMonth)} run`
            : 'Loading run…'
        }
        description={
          summaryData ? (
            <Space size={10} wrap>
              <StatusPill tone={PAYROLL_RUN_STATUS_TONE[summaryData.status]}>
                {PAYROLL_RUN_STATUS_LABEL[summaryData.status]}
              </StatusPill>
              <Text type="secondary">FY {summaryData.fiscalYearCode}</Text>
              {summaryData.computedAt && (
                <Text type="secondary">
                  Computed {formatDateTime(summaryData.computedAt)}
                </Text>
              )}
              {summaryData.postedAt && (
                <Text type="secondary">
                  Posted {formatDateTime(summaryData.postedAt)}
                </Text>
              )}
            </Space>
          ) : undefined
        }
        actions={
          // `payroll.reports.run` → CASL ('run', 'payroll.reports').
          ability.can('run', 'payroll.reports') &&
          (summaryData?.totals.employeeCount ?? 0) > 0 ? (
            <Popover
              trigger="click"
              placement="bottomRight"
              open={pdfOpen}
              onOpenChange={(o) => !downloadAllPdf.isPending && setPdfOpen(o)}
              content={
                <div
                  style={{ display: 'flex', flexDirection: 'column', gap: 12, maxWidth: 340 }}
                >
                  <div>
                    <Text strong style={{ display: 'block', marginBottom: 4 }}>
                      Layout
                    </Text>
                    <Segmented<SlipLayout>
                      options={SLIP_LAYOUT_OPTIONS}
                      value={pdfLayout}
                      onChange={setPdfLayout}
                      disabled={downloadAllPdf.isPending}
                    />
                    {pdfLayout === 'sheet' && (
                      <Text type="secondary" style={{ display: 'block', fontSize: 12, marginTop: 4 }}>
                        {SHEET_LAYOUT_HINT}
                      </Text>
                    )}
                  </div>
                  <div>
                    <Text strong style={{ display: 'block', marginBottom: 4 }}>
                      Language
                    </Text>
                    <Segmented<SlipLanguage>
                      options={[
                        { label: 'English', value: 'en' },
                        { label: 'اردو', value: 'ur' },
                      ]}
                      value={pdfLanguage}
                      onChange={setPdfLanguage}
                      disabled={downloadAllPdf.isPending}
                    />
                  </div>
                  <Button
                    type="primary"
                    icon={<Download size={14} strokeWidth={1.75} />}
                    loading={downloadAllPdf.isPending}
                    disabled={downloadAllPdf.isPending}
                    onClick={() => downloadAllPdf.mutate()}
                  >
                    {downloadAllPdf.isPending ? 'Rendering PDF…' : 'Download'}
                  </Button>
                </div>
              }
            >
              <Button
                icon={<Download size={14} strokeWidth={1.75} />}
                loading={downloadAllPdf.isPending}
                disabled={downloadAllPdf.isPending}
              >
                Download all payslips (PDF)
              </Button>
            </Popover>
          ) : undefined
        }
      />

      <RequestState
        loading={summaryQuery.isLoading}
        error={
          summaryQuery.isError
            ? summaryQuery.error
            : detailQuery.isError
              ? detailQuery.error
              : undefined
        }
        errorTitle="Couldn't load run"
      >
        {summaryData && (
          <RunStateMachine
            status={summaryData.status}
            skippedCount={summaryData.skippedCount}
            actions={
              <RunActions
                status={summaryData.status}
                canWrite={canWrite}
                lockAttendance={lockAttendance}
                compute={compute}
                approve={approve}
                post={post}
                cancel={cancel}
                reset={reset}
              />
            }
          />
        )}

        {summaryData && isComputing && summaryData.totalCount > 0 && (
          <Section
            title="Compute progress"
            subtitle={`${summaryData.progressCount} of ${summaryData.totalCount} employees processed`}
          >
            <Progress
              percent={Math.min(
                100,
                Math.round(
                  (summaryData.progressCount / summaryData.totalCount) * 100,
                ),
              )}
              status="active"
            />
          </Section>
        )}

        {summaryData && isPosting && summaryData.totalCount > 0 && (
          <Section
            title="Posting"
            subtitle={`Generating PDFs ${summaryData.progressCount} / ${summaryData.totalCount}`}
          >
            <Progress
              percent={Math.min(
                100,
                Math.round(
                  (summaryData.progressCount / summaryData.totalCount) * 100,
                ),
              )}
              status="active"
            />
          </Section>
        )}

        {summaryData && (
          <KpiGrid>
            <StatCard
              label="Employees"
              value={String(summaryData.totals.employeeCount)}
              tone="info"
            />
            <StatCard
              label="Basic"
              value={<MoneyCell amount={summaryData.totals.basic} compact />}
              tone="primary"
            />
            <StatCard
              label="Allowances"
              value={<MoneyCell amount={summaryData.totals.allowances} compact />}
              tone="primary"
            />
            <StatCard
              label="Gross"
              value={<MoneyCell amount={summaryData.totals.gross} compact />}
              tone="primary"
            />
            <StatCard
              label="Deductions"
              value={<MoneyCell amount={summaryData.totals.deductions} compact />}
              tone="warning"
            />
            <StatCard
              label="Tax"
              value={<MoneyCell amount={summaryData.totals.tax} compact />}
              tone="warning"
            />
            <StatCard
              label="EOBI"
              value={<MoneyCell amount={summaryData.totals.eobi} compact />}
              tone="warning"
            />
            {summaryData.totals.pessiEmployer > 0 && (
              <StatCard
                label="PESSI (employer)"
                value={<MoneyCell amount={summaryData.totals.pessiEmployer} compact />}
                tone="info"
              />
            )}
            <StatCard
              label="Net"
              value={<MoneyCell amount={summaryData.totals.net} compact />}
              tone="success"
            />
            {(summaryData.totals.heldCount > 0 ||
              summaryData.totals.releasePendingCount > 0) && (
              <StatCard
                label="On hold"
                value={`${summaryData.totals.heldCount} held · ${summaryData.totals.releasePendingCount} pending`}
                tone="warning"
              />
            )}
            {summaryData.skippedCount > 0 && (
              <StatCard
                label="Skipped"
                value={String(summaryData.skippedCount)}
                tone="error"
              />
            )}
          </KpiGrid>
        )}

        {summaryData && postCompute && (
          <RunSkipsSection
            runId={id}
            skippedCount={summaryData.skippedCount}
            retryable={summaryData.status === 'COMPUTED'}
            canWrite={canWrite}
            onChanged={refreshAll}
          />
        )}

        <Section
          title="Payslips"
          subtitle={
            payslipsPage
              ? `${payslipsPage.totalElements} payslip${payslipsPage.totalElements === 1 ? '' : 's'}`
              : undefined
          }
          actions={
            <Segmented<PayslipStatus | 'ALL'>
              value={payslipFilter}
              onChange={(v) => {
                setPayslipFilter(v);
                setPayslipPage(0);
              }}
              options={[
                { label: 'All', value: 'ALL' },
                { label: 'Draft', value: 'DRAFT' },
                { label: 'Paid', value: 'PAID' },
                { label: 'Held', value: 'HELD' },
                { label: 'Release pending', value: 'RELEASE_PENDING' },
                { label: 'Released', value: 'RELEASED' },
              ]}
            />
          }
        >
          {!summaryData || !postCompute ? (
            <Text type="secondary">
              Payslips appear once the run is computed.
            </Text>
          ) : (
            <>
              <FilterBar
                search={{
                  value: searchInput,
                  onChange: (v) => {
                    setSearchInput(v);
                    setPayslipPage(0);
                  },
                  placeholder: 'Search by employee code…',
                  width: 300,
                }}
                onReset={
                  searchInput || payslipFilter !== 'ALL'
                    ? () => {
                        setSearchInput('');
                        setPayslipFilter('ALL');
                        setPayslipPage(0);
                      }
                    : undefined
                }
                style={{ marginBottom: 'var(--app-space-sm)' }}
              />
              <RunPayslipsTable
                runId={id}
                payslips={payslips}
                loading={detailQuery.isLoading}
                runStatus={summaryData.status}
                onRefresh={refreshAll}
                page={payslipPage}
                pageSize={payslipPageSize}
                totalElements={payslipsPage?.totalElements ?? 0}
                onPageChange={(p, s) => {
                  setPayslipPage(p);
                  setPayslipPageSize(s);
                }}
                onOpenDetail={setOpenPayslipId}
                onOpenAttendance={setAttendanceRow}
              />
            </>
          )}
        </Section>
      </RequestState>

      <PayslipDetailDrawer
        open={openPayslipId != null}
        id={openPayslipId}
        row={openPayslipRow}
        onClose={() => setOpenPayslipId(null)}
      />
      {attendanceRow ? (
        <RunEmployeeAttendanceDrawer
          row={attendanceRow}
          onClose={() => setAttendanceRow(null)}
        />
      ) : null}
    </div>
  );
}

/** Employee-cell drawer on the run detail page: reuses the shared
 *  attendance day↔month shell, anchored at today. Today's record (when
 *  there is one) feeds the same day body the attendance dashboard shows;
 *  the "View full month" toggle works regardless. */
function RunEmployeeAttendanceDrawer({
  row,
  onClose,
}: {
  row: PayslipWithEmployee;
  onClose: () => void;
}) {
  const today = toIsoDate(new Date());
  const recordQuery = useQuery({
    queryKey: ['attendance', 'org', 'records', 'day', row.employee.employeeId, today],
    queryFn: ({ signal }) =>
      attendanceBff.records(
        { employeeId: row.employee.employeeId, from: today, to: today, size: 1 },
        signal,
      ),
  });
  const record = recordQuery.data?.content[0]?.record;
  const e = row.employee;

  return (
    <AttendanceDayDrawer
      employeeId={e.employeeId}
      person={{
        fullName: e.fullName,
        employeeCode: e.employeeCode,
        designation: e.designation ?? e.department ?? null,
        profilePictureUrl: e.profilePictureUrl ?? undefined,
      }}
      date={today}
      statusPill={
        record ? (
          <StatusPill tone={ATTENDANCE_STATUS_TONE[record.status]}>
            {ATTENDANCE_STATUS_LABEL[record.status]}
          </StatusPill>
        ) : undefined
      }
      onClose={onClose}
    >
      {record ? (
        <AttendanceRecordDayBody record={record} />
      ) : (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {recordQuery.isLoading
            ? 'Loading today\'s attendance…'
            : 'No attendance record for today — open the full month below.'}
        </Text>
      )}
    </AttendanceDayDrawer>
  );
}
