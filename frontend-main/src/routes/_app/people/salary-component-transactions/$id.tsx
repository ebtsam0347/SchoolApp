// /people/salary-component-transactions/$id — transaction detail page.
//
// Mirrors /payroll/runs/$id with three layers:
//   1. TransactionStateMachine — visualizes the transaction lifecycle.
//   2. Header KPIs — period, totals (server-side summary).
//   3. Transaction lines — search, export, column toggle, Table/Cards view.

import { createFileRoute, Link, useParams } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, useContext, useCallback, useEffect, useRef } from 'react';
import {
  App as AntdApp,
  Button,
  Pagination,
  Progress,
  Segmented,
  Space,
  Typography,
} from 'antd';
import { ArrowDownToLine, ChevronLeft, LayoutGrid, Rows3 } from 'lucide-react';

import {
  ColumnsToggle,
  FilterBar,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { AbilityContext } from '@/auth';
import { employeesBff } from '@/attendance';
import { downloadCsv, getErrorMessage, useDebounced, useTableColumns } from '@/lib';
import { formatDateTime, formatPeriodLong } from '@/payroll';
import {
  TRANSACTION_LINE_STATUS_LABEL,
  TRANSACTION_STATUS_LABEL,
  TRANSACTION_STATUS_TONE,
  salaryComponentTransactionsApi,
  salaryComponentTransactionComposites,
  toTransactionEmployeeRows,
  transactionEmployeeColumns,
  TransactionStateMachine,
  TransactionActions,
  TransactionLinesTable,
  TransactionEmployeeCardGrid,
  type SalaryComponentTransactionDto,
  type TransactionEmployeeDto,
} from '@/people/salary-component-transactions';

const { Text } = Typography;

export const Route = createFileRoute(
  '/_app/people/salary-component-transactions/$id',
)({
  component: SalaryComponentTransactionDetailPage,
});

// Upper bound on rows fetched in one go while name-searching client-side —
// the /transactions endpoint has no server-side name filter, so a search
// term pulls this many rows and filters/paginates locally instead.
const LINE_SEARCH_FETCH_SIZE = 1000;

function exportTransactionLinesCsv(
  employees: TransactionEmployeeDto[],
  summary: SalaryComponentTransactionDto,
) {
  const header = [
    'Employee Code',
    'Employee Name',
    'Department',
    'Section',
    'Designation',
    'Grade',
    'Fiscal Year',
    'Component',
    'Amount',
    'Status',
  ];
  const lines = employees.flatMap((emp) =>
    emp.components.map((c) => [
      emp.employeeCode,
      emp.employee?.fullName ?? '',
      emp.employee?.departmentName ?? '',
      emp.employee?.sectionName ?? '',
      emp.employee?.designationName ?? '',
      emp.grade,
      emp.fiscalYearCode,
      c.name,
      c.amount,
      TRANSACTION_LINE_STATUS_LABEL[c.status],
    ]),
  );
  downloadCsv(
    `salary-component-transaction-${summary.periodYear}-${String(summary.periodMonth).padStart(2, '0')}.csv`,
    header,
    lines,
  );
}

function SalaryComponentTransactionDetailPage() {
  const { id: idParam } = useParams({
    from: '/_app/people/salary-component-transactions/$id',
  });
  const id = Number(idParam);
  const ability = useContext(AbilityContext);
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const [linePage, setLinePage] = useState(0);
  const [linePageSize, setLinePageSize] = useState(20);
  const [lineView, setLineView] = useState<'table' | 'card'>('table');
  const [lineSearch, setLineSearch] = useState('');
  const debouncedLineSearch = useDebounced(lineSearch.trim(), 250);
  const isSearchingLines = debouncedLineSearch !== '';

  useEffect(() => {
    setLinePage(0);
  }, [debouncedLineSearch]);

  const summaryQuery = useQuery({
    queryKey: ['salary-component-transactions', 'transaction', id],
    queryFn: ({ signal }) =>
      salaryComponentTransactionsApi.get(id, signal),
    refetchInterval: (q) =>
      q.state.data?.status === 'COMPUTING' ? 4_000 : false,
  });

  const summaryData = summaryQuery.data;
  const status = summaryData?.status;
  const isComputing = status === 'COMPUTING';
  const postCompute = !!summaryData && !isComputing;

  const approvedById = summaryData?.approvedBy ?? null;
  const approverQuery = useQuery({
    queryKey: ['bff', 'employees', 'by-ids', approvedById],
    queryFn: ({ signal }) => employeesBff.byIds([approvedById as number], signal),
    enabled: approvedById != null,
    staleTime: 5 * 60_000,
  });
  const approverName = approverQuery.data?.employees[0]?.fullName;

  const kpiQuery = useQuery({
    queryKey: ['salary-component-transactions', 'transaction', id, 'summary'],
    queryFn: ({ signal }) =>
      salaryComponentTransactionComposites.summary(id, signal),
    enabled: postCompute,
  });

  const kpiData = kpiQuery.data;

  const transactionsQuery = useQuery({
    queryKey: [
      'salary-component-transactions',
      'transaction',
      id,
      'transactions',
      isSearchingLines ? 'search' : linePage,
      isSearchingLines ? LINE_SEARCH_FETCH_SIZE : linePageSize,
    ],
    queryFn: ({ signal }) =>
      salaryComponentTransactionsApi.transactions(
        id,
        {
          page: isSearchingLines ? 0 : linePage,
          size: isSearchingLines ? LINE_SEARCH_FETCH_SIZE : linePageSize,
        },
        signal,
      ),
    enabled: postCompute,
  });

  const transactionsPage = transactionsQuery.data;
  const fetchedLineEmployees = transactionsPage?.content ?? [];
  const lineSearchTerm = debouncedLineSearch.toLowerCase();
  const filteredLineEmployees = isSearchingLines
    ? fetchedLineEmployees.filter(
        (e) =>
          (e.employee?.fullName ?? '').toLowerCase().includes(lineSearchTerm) ||
          e.employeeCode.toLowerCase().includes(lineSearchTerm),
      )
    : fetchedLineEmployees;
  const pagedLineEmployees = isSearchingLines
    ? filteredLineEmployees.slice(
        linePage * linePageSize,
        (linePage + 1) * linePageSize,
      )
    : filteredLineEmployees;
  const lineTotal = isSearchingLines
    ? filteredLineEmployees.length
    : (transactionsPage?.totalElements ?? 0);
  const lineRows = toTransactionEmployeeRows(pagedLineEmployees);

  const {
    columns: visibleLineColumns,
    options: lineColumnOptions,
    toggle: toggleLineColumn,
    reset: resetLineColumns,
  } = useTableColumns(
    'people.salary-component-transactions.lines.cols.v1',
    transactionEmployeeColumns,
  );

  const [isExportingLines, setIsExportingLines] = useState(false);
  const exportLines = async () => {
    if (!summaryData) return;
    setIsExportingLines(true);
    try {
      const chunkSize = 200;
      const first = await salaryComponentTransactionsApi.transactions(id, {
        page: 0,
        size: chunkSize,
      });
      const total = first.totalElements ?? 0;
      const allEmployees: TransactionEmployeeDto[] = [...(first.content ?? [])];
      const pageCount = Math.ceil(total / chunkSize);
      for (let p = 1; p < pageCount; p++) {
        const res = await salaryComponentTransactionsApi.transactions(id, {
          page: p,
          size: chunkSize,
        });
        allEmployees.push(...(res.content ?? []));
      }
      const term = lineSearchTerm;
      const exportEmployees = term
        ? allEmployees.filter(
            (e) =>
              (e.employee?.fullName ?? '').toLowerCase().includes(term) ||
              e.employeeCode.toLowerCase().includes(term),
          )
        : allEmployees;
      exportTransactionLinesCsv(exportEmployees, summaryData);
      message.success(
        `Exported ${exportEmployees.length.toLocaleString()} employee${exportEmployees.length === 1 ? '' : 's'}.`,
      );
    } catch (err) {
      message.error(getErrorMessage(err));
    } finally {
      setIsExportingLines(false);
    }
  };

  const refreshAll = useCallback(() => {
    void queryClient.invalidateQueries({
      queryKey: ['salary-component-transactions', 'transaction', id],
    });
  }, [queryClient, id]);
  function onMutErr(e: unknown) {
    message.error(getErrorMessage(e));
  }

  // Compute runs in the background on the server — the summary poll (above)
  // is what detects COMPUTING → COMPUTED. The KPI + transaction-lines
  // queries only fetch once, right when they first become enabled, so a
  // status flip alone doesn't guarantee they pick up the freshly computed
  // rows. Force one explicit refetch the moment compute settles.
  const wasComputingRef = useRef(false);
  useEffect(() => {
    if (wasComputingRef.current && !isComputing) {
      refreshAll();
    }
    wasComputingRef.current = isComputing;
  }, [isComputing, refreshAll]);

  const compute = useMutation({
    mutationFn: () => salaryComponentTransactionsApi.compute(id),
    onSuccess: () => {
      message.success('Compute started.');
      refreshAll();
    },
    onError: onMutErr,
  });
  const approve = useMutation({
    mutationFn: () => salaryComponentTransactionsApi.approve(id),
    onSuccess: () => {
      message.success('Transaction approved.');
      refreshAll();
    },
    onError: onMutErr,
  });
  const cancel = useMutation({
    mutationFn: () => salaryComponentTransactionsApi.cancel(id),
    onSuccess: () => {
      message.success('Transaction cancelled.');
      refreshAll();
    },
    onError: onMutErr,
  });
  const reset = useMutation({
    mutationFn: () => salaryComponentTransactionsApi.reset(id),
    onSuccess: () => {
      message.success(
        'Transaction reset to Locked. Fix the data, then run Compute again.',
      );
      refreshAll();
    },
    onError: onMutErr,
  });

  const canWrite = ability.can('manage', 'employee');

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
          <Link to="/people/salary-component-transactions">
            <ChevronLeft
              size={12}
              style={{ verticalAlign: -1, marginRight: 2 }}
            />
            Transactions
          </Link>
        }
        title={
          summaryData
            ? `${formatPeriodLong(summaryData.periodYear, summaryData.periodMonth)} transaction`
            : 'Loading transaction…'
        }
        description={
          summaryData ? (
            <Space size={10} wrap>
              {summaryData.status !== 'LOCKED' && (
                <StatusPill
                  tone={TRANSACTION_STATUS_TONE[summaryData.status]}
                >
                  {TRANSACTION_STATUS_LABEL[summaryData.status]}
                </StatusPill>
              )}
              <Text type="secondary">FY {summaryData.fiscalYearCode}</Text>
              {summaryData.computedAt && (
                <Text type="secondary">
                  Computed {formatDateTime(summaryData.computedAt)}
                </Text>
              )}
              {summaryData.approvedAt && (
                <Text type="secondary">
                  Approved {formatDateTime(summaryData.approvedAt)}
                  {approverName ? ` by ${approverName}` : ''}
                </Text>
              )}
            </Space>
          ) : undefined
        }
      />

      <RequestState
        loading={summaryQuery.isLoading}
        error={
          summaryQuery.isError
            ? summaryQuery.error
            : transactionsQuery.isError
              ? transactionsQuery.error
              : undefined
        }
        errorTitle="Couldn't load transaction"
      >
        {summaryData && (
          <TransactionStateMachine
            status={summaryData.status}
            skippedCount={summaryData.skippedCount}
            actions={
              <TransactionActions
                status={summaryData.status}
                canWrite={canWrite}
                compute={compute}
                approve={approve}
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

        {kpiData && kpiData.totals && (
          <KpiGrid>
            <StatCard
              label="Employees"
              value={String(kpiData.totals.employeeCount ?? 0)}
            />
            <StatCard
              label="Total Amount"
              value={(kpiData.totals.allowance ?? 0).toLocaleString()}
            />
          </KpiGrid>
        )}

        <Section
          title="Transaction lines"
          subtitle={
            !transactionsQuery.data
              ? undefined
              : `${lineTotal} employee${lineTotal === 1 ? '' : 's'}`
          }
        >
          {!summaryData || !postCompute ? (
            <Text type="secondary">
              Lines appear once the transaction is computed.
            </Text>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <FilterBar
                search={{
                  value: lineSearch,
                  onChange: setLineSearch,
                  placeholder: 'Search by name, employee code…',
                }}
                actions={
                  <Space size={8} wrap>
                    <Button
                      size="small"
                      icon={<ArrowDownToLine size={14} strokeWidth={1.75} />}
                      onClick={() => void exportLines()}
                      loading={isExportingLines}
                    >
                      Export
                    </Button>
                    {lineView === 'table' ? (
                      <ColumnsToggle
                        size="small"
                        options={lineColumnOptions}
                        onToggle={toggleLineColumn}
                        onReset={resetLineColumns}
                      />
                    ) : null}
                    <Segmented<'table' | 'card'>
                      value={lineView}
                      onChange={setLineView}
                      size="small"
                      options={[
                        { value: 'table', icon: <Rows3 size={14} strokeWidth={1.75} />, label: 'Table' },
                        { value: 'card', icon: <LayoutGrid size={14} strokeWidth={1.75} />, label: 'Cards' },
                      ]}
                    />
                  </Space>
                }
              />

              {lineView === 'card' ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <TransactionEmployeeCardGrid
                    rows={lineRows}
                    loading={transactionsQuery.isLoading}
                    transactionStatus={summaryData.status}
                  />
                  {lineTotal > 0 ? (
                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                      <Pagination
                        current={linePage + 1}
                        pageSize={linePageSize}
                        total={lineTotal}
                        showSizeChanger
                        pageSizeOptions={['20', '50', '100', '200', '400', '']}
                        showTotal={(n, [from, to]) => `${from}-${to} of ${n}`}
                        onChange={(p, s) => {
                          setLinePage(p - 1);
                          setLinePageSize(s);
                        }}
                      />
                    </div>
                  ) : null}
                </div>
              ) : (
                <TransactionLinesTable
                  rows={lineRows}
                  columns={visibleLineColumns}
                  loading={transactionsQuery.isLoading}
                  transactionStatus={summaryData.status}
                  page={linePage}
                  pageSize={linePageSize}
                  totalElements={lineTotal}
                  onPageChange={(p, s) => {
                    setLinePage(p);
                    setLinePageSize(s);
                  }}
                />
              )}
            </div>
          )}
        </Section>
      </RequestState>
    </div>
  );
}
