// /people/annual-leave-transaction - Annual Leave Transactions.
//
// Filter-first workflow: a searchable month/year picker at the top drives
// the calculation/review view directly below — no intermediate list page.

import { useEffect, useState } from 'react';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import {
  App as AntdApp,
  Alert,
  Button,
  DatePicker,
  Empty,
  Progress,
  Select,
  Space,
  Table,
  Tabs,
  Tooltip,
  theme as antdTheme,
  type TableProps,
} from 'antd';
import { CalendarRange, CircleCheck, CircleX, Clock, Lock, LockOpen, Minus } from 'lucide-react';

import { ColumnsToggle, EmptyState, FilterBar, PageHeader, PersonCell, Section, StatusPill } from '@/components';
import { getErrorMessage, useDebounced, useTableColumns } from '@/lib';
import { formatDateShort } from '@/lib/dates';
import { annualLeaveApi, formatPeriod, formatPeriodLong, type AnnualLeaveDto } from '@/payroll';

const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/people/annual-leave-transaction')({
  validateSearch: (search: Record<string, unknown>) => ({
    year: typeof search.year === 'number' ? search.year : undefined,
    month: typeof search.month === 'number' ? search.month : undefined,
  }),
  component: AnnualLeaveTransactionPage,
});

type PeriodRow = {
  key: string;
  year: number;
  month: number;
  label: string;
};

function monthParts(period: PeriodRow) {
  return { year: period.year, month: period.month };
}

function toPeriodRow(year: number, month: number): PeriodRow {
  return {
    key: `${year}-${String(month).padStart(2, '0')}`,
    year,
    month,
    label: formatPeriod(year, month),
  };
}

// Mirrors the GET /annual-leave filters. A row's paymentStatus is
// three-state: null = no decision yet (Not Marked), false = marked unpaid
// (leave days carried forward), true = paid.
type PaymentFilter = 'ALL' | 'PAID' | 'UNPAID' | 'PENDING';

const PAYMENT_FILTER_OPTIONS: { value: PaymentFilter; label: string }[] = [
  { value: 'ALL', label: 'All payment statuses' },
  { value: 'PAID', label: 'Paid' },
  { value: 'UNPAID', label: 'Unpaid' },
  { value: 'PENDING', label: 'Not Marked' },
];

const PAYMENT_FILTER_TITLES: Record<PaymentFilter, string> = {
  ALL: 'Calculated',
  PAID: 'Paid',
  UNPAID: 'Unpaid',
  PENDING: 'Not marked',
};

function filterParams(payment: PaymentFilter) {
  return {
    paymentStatus: payment === 'PAID' ? true : payment === 'UNPAID' ? false : undefined,
    paymentPending: payment === 'PENDING' ? true : undefined,
  };
}

function paymentLabel(row: AnnualLeaveDto) {
  if (row.paymentStatus === null) return 'Not Marked';
  return row.paymentStatus ? 'Paid' : 'Unpaid';
}

// Green = paid, red = unpaid (leave carried forward), orange = still needs a
// decision. Icons back up the colour so the three read apart at a glance.
function PaymentStatusPill({ row }: { row: AnnualLeaveDto }) {
  const iconProps = { size: 12, strokeWidth: 2 };
  const [tone, icon] =
    row.paymentStatus === null
      ? (['warning', <Clock {...iconProps} />] as const)
      : row.paymentStatus
        ? (['success', <CircleCheck {...iconProps} />] as const)
        : (['error', <CircleX {...iconProps} />] as const);
  return (
    <StatusPill tone={tone} size="sm" icon={icon}>
      {paymentLabel(row)}
    </StatusPill>
  );
}

// lockStatus is three-state: true = locked, false = explicitly not locked,
// null = no lock action has ever been taken on the row.
function LockStatusPill({ row }: { row: AnnualLeaveDto }) {
  const iconProps = { size: 12, strokeWidth: 2 };
  if (row.lockStatus === true) {
    return (
      <StatusPill tone="info" size="sm" icon={<Lock {...iconProps} />}>
        Locked
      </StatusPill>
    );
  }
  if (row.lockStatus === false) {
    return (
      <StatusPill tone="neutral" size="sm" icon={<LockOpen {...iconProps} />}>
        Not Locked
      </StatusPill>
    );
  }
  return (
    <StatusPill tone="neutral" size="sm" icon={<Minus {...iconProps} />}>
      No Action
    </StatusPill>
  );
}

// Upper bound on rows fetched in one go while name-searching client-side -
// comfortably above a single month's headcount without being unbounded.
const DETAIL_SEARCH_FETCH_SIZE = 1000;

// Columns available via the toggle but not shown until the user opts in -
// keeps the default table narrower. Must stay referentially stable (module
// constant) since useTableColumns re-applies it whenever the array changes.
const ANNUAL_LEAVE_DEFAULT_HIDDEN_COLUMNS = ['perDaySalary', 'grossAnnualLeaveAmount'];

function moneyCol(
  title: string,
  dataIndex: keyof AnnualLeaveDto,
  width = 100,
  bold = false,
): NonNullable<TableProps<AnnualLeaveDto>['columns']>[number] {
  return {
    title,
    dataIndex,
    key: dataIndex,
    align: 'right',
    width,
    render: (value: number) => (
      <span style={bold ? { fontWeight: 600 } : undefined}>{value.toLocaleString()}</span>
    ),
  };
}

// Card number is how payroll staff find people, so it reads as a tag:
// heavy weight in the primary colour on a tinted chip.
function EmployeeCode({ code }: { code: string }) {
  const { token } = useToken();
  return (
    <span
      style={{
        fontWeight: 700,
        color: token.colorPrimary,
        background: token.colorPrimaryBg,
        border: `1px solid ${token.colorPrimaryBorder}`,
        borderRadius: 4,
        padding: '0 5px',
        letterSpacing: 0.2,
      }}
    >
      {code}
    </span>
  );
}

const ellipsisLine: React.CSSProperties = {
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
};

// Widths trimmed to the minimum that keeps each column's actual values
// single-line (long text lives in headers, which are allowed to wrap onto a
// second line — only body-cell wrapping looks broken). Department / Section
// lives inside the Employee cell rather than its own column to keep the
// table narrow enough to fit without horizontal scrolling.
const annualLeaveDetailColumns: TableProps<AnnualLeaveDto>['columns'] = [
  {
    title: 'Employee',
    key: 'employee',
    fixed: 'left',
    width: 240,
    render: (_value: unknown, row: AnnualLeaveDto) => {
      const deptName = row.employee?.departmentName;
      const secName = row.employee?.sectionName;
      // Dedupe when section name == department name, same as the Directory
      // table's Department / Section column.
      const org = [deptName, secName && secName !== deptName ? secName : null].filter(Boolean).join(' › ');
      const designation = row.employee?.designationName;
      return (
        <PersonCell
          size="compact"
          person={{
            fullName: row.employee?.fullName ?? row.employeeCode,
            employeeCode: row.employeeCode,
            profilePictureUrl: row.employee?.profilePictureUrl,
            subtitle: (
              <>
                <span style={{ ...ellipsisLine, display: 'block', lineHeight: '18px', margin: '1px 0' }}>
                  {designation ? `${designation} · ` : null}
                  <EmployeeCode code={row.employeeCode} />
                </span>
                {org ? (
                  <span style={{ ...ellipsisLine, display: 'block' }} title={org}>
                    {org}
                  </span>
                ) : null}
              </>
            ),
          }}
        />
      );
    },
  },
  {
    title: 'Joining Date',
    dataIndex: 'joiningDate',
    key: 'joiningDate',
    width: 100,
    render: (value: string) => formatDateShort(value),
  },

  {
    // Start and end read as one fact ("1 Sep 2025 - 1 Aug 2026"), so they
    // share a single column instead of two date cells the eye has to pair up.
    title: 'Cycle',
    key: 'cycle',
    width: 180,
    render: (_value: unknown, row: AnnualLeaveDto) =>
      `${formatDateShort(row.cycleStart)} - ${formatDateShort(row.cycleEnd)}`,
  },
  moneyCol('Basic Salary', 'basicSalary'),
  moneyCol('Per Day Salary', 'perDaySalary'),
  {
    // Eligible, availed and payable are one calculation (eligible - availed
    // = payable), so they share a column; the tooltip spells out each number.
    title: (
      <Tooltip title="Eligible / Availed / Payable">
        <span>
          Leave Days
          <br />
          <span style={{ fontWeight: 400, fontSize: 11, opacity: 0.65 }}>Elig. / Avail. / Pay.</span>
        </span>
      </Tooltip>
    ),
    key: 'leaveDays',
    align: 'center',
    width: 120,
    render: (_value: unknown, row: AnnualLeaveDto) => (
      <Tooltip
        title={`Eligible ${row.eligibleLeaveDays} · Availed ${row.availedLeaveDays} · Payable ${row.payableLeaveDays}`}
      >
        <span style={{ whiteSpace: 'nowrap' }}>
          {row.eligibleLeaveDays} / {row.availedLeaveDays} / <strong>{row.payableLeaveDays}</strong>
        </span>
      </Tooltip>
    ),
  },
  moneyCol('Due Amount', 'leaveAmount'),
  moneyCol('Gross Annual Leave Amount', 'grossAnnualLeaveAmount', 120),
  moneyCol('Long-term Advance Deduction', 'deductionAsLongTermAdvance', 120),
  moneyCol('Final Amount', 'netAmount', 100, true),
  {
    title: 'Payment Status',
    key: 'paymentStatus',
    width: 100,
    render: (_value: unknown, row: AnnualLeaveDto) => <PaymentStatusPill row={row} />,
  },
  {
    // Locking is a month-level action (see the header), but it's stored per
    // row — shown here so a locked row explains its disabled action button.
    title: 'Lock Status',
    key: 'lockStatus',
    width: 110,
    render: (_value: unknown, row: AnnualLeaveDto) => <LockStatusPill row={row} />,
  },
];

// Not-marked rows need a first decision either way, so they get both
// buttons side by side; decided rows get the single button that flips them.
// Short labels keep the Action column narrow; the tooltip and the
// confirmation dialog spell out the full action.
function PaymentActions({
  row,
  savingStatus,
  disabled,
  onSetPaid,
}: {
  row: AnnualLeaveDto;
  /** The status being saved for this row right now, if any. */
  savingStatus: boolean | undefined;
  disabled?: boolean;
  onSetPaid: (row: AnnualLeaveDto, paymentStatus: boolean) => void;
}) {
  const showPaid = row.paymentStatus !== true;
  const showUnpaid = row.paymentStatus !== false;
  return (
    <Space size={4}>
      {showPaid ? (
        <Tooltip title="Mark as paid">
          <Button
            size="small"
            type="primary"
            icon={<CircleCheck size={12} strokeWidth={2} />}
            disabled={disabled}
            loading={savingStatus === true}
            onClick={() => onSetPaid(row, true)}
          >
            Set Paid
          </Button>
        </Tooltip>
      ) : null}
      {showUnpaid ? (
        <Tooltip title="Mark as unpaid - payable leave days carry forward">
          <Button
            size="small"
            danger
            icon={<CircleX size={12} strokeWidth={2} />}
            disabled={disabled}
            loading={savingStatus === false}
            onClick={() => onSetPaid(row, false)}
          >
            Set Unpaid
          </Button>
        </Tooltip>
      ) : null}
    </Space>
  );
}

function AnnualLeaveTransactionPage() {
  const { message, modal } = AntdApp.useApp();
  const { token } = useToken();
  const queryClient = useQueryClient();
  const navigate = useNavigate({ from: '/people/annual-leave-transaction' });
  const routeSearch = Route.useSearch();

  const today = dayjs();
  const selectedPeriod: PeriodRow =
    routeSearch.year && routeSearch.month
      ? toPeriodRow(routeSearch.year, routeSearch.month)
      : toPeriodRow(today.year(), today.month() + 1);

  // Applies the current calendar month once, on first load only (no
  // year/month picked in the URL yet), so the page never opens empty.
  // Picking a month afterwards never auto-navigates — that only happens
  // through the "Go" button below, so the picker can be changed freely
  // before it commits to a filter.
  useEffect(() => {
    if (routeSearch.year || routeSearch.month) return;
    void navigate({ search: { year: selectedPeriod.year, month: selectedPeriod.month }, replace: true });
  }, [routeSearch.year, routeSearch.month, navigate, selectedPeriod.year, selectedPeriod.month]);

  // Month/year picked in the header — held locally until "Go" is pressed,
  // so browsing the picker never filters the page on its own.
  const [pendingDate, setPendingDate] = useState<Dayjs>(() =>
    dayjs(`${selectedPeriod.year}-${String(selectedPeriod.month).padStart(2, '0')}-01`),
  );

  useEffect(() => {
    setPendingDate(dayjs(`${selectedPeriod.year}-${String(selectedPeriod.month).padStart(2, '0')}-01`));
  }, [selectedPeriod.year, selectedPeriod.month]);

  const applyFilter = () => {
    const d = pendingDate ?? dayjs();
    void navigate({ search: { year: d.year(), month: d.month() + 1 } });
  };

  const [activeTab, setActiveTab] = useState('calculation');
  const [paymentFilter, setPaymentFilter] = useState<PaymentFilter>('ALL');
  const [detailPage, setDetailPage] = useState(1);
  const [detailPageSize, setDetailPageSize] = useState(10);
  // GET /annual-leave only takes employeeCode as a text filter (no name/
  // designation/department search), so this hits the backend directly -
  // same as Directory's search - rather than filtering already-loaded rows.
  const [detailSearch, setDetailSearch] = useState('');
  const debouncedDetailSearch = useDebounced(detailSearch.trim(), 250);
  useEffect(() => {
    setActiveTab('calculation');
    setPaymentFilter('ALL');
    setDetailPage(1);
    setDetailPageSize(10);
    setDetailSearch('');
  }, [routeSearch.year, routeSearch.month]);

  useEffect(() => {
    setDetailPage(1);
  }, [debouncedDetailSearch]);

  const { data } = useQuery({
    queryKey: [
      'annual-leave',
      'month',
      selectedPeriod?.year,
      selectedPeriod?.month,
    ],
    queryFn: ({ signal }) =>
      annualLeaveApi.list(
        {
          ...monthParts(selectedPeriod!),
          page: 0,
          size: 1,
        },
        signal,
      ),
    enabled: !!selectedPeriod,
  });

  const { data: paidData } = useQuery({
    queryKey: [
      'annual-leave',
      'summary',
      'paid',
      selectedPeriod?.year,
      selectedPeriod?.month,
    ],
    queryFn: ({ signal }) =>
      annualLeaveApi.list(
        { ...monthParts(selectedPeriod!), paymentStatus: true, page: 0, size: 1 },
        signal,
      ),
    enabled: !!selectedPeriod,
  });

  const { data: unpaidData } = useQuery({
    queryKey: ['annual-leave', 'summary', 'unpaid', selectedPeriod?.year, selectedPeriod?.month],
    queryFn: ({ signal }) =>
      annualLeaveApi.list(
        { ...monthParts(selectedPeriod!), paymentStatus: false, page: 0, size: 1 },
        signal,
      ),
    enabled: !!selectedPeriod,
  });

  // Rows with no payment decision yet (paymentStatus null) - counted by the
  // backend's paymentPending filter. These block the month lock.
  const { data: notMarkedData } = useQuery({
    queryKey: ['annual-leave', 'summary', 'not-marked', selectedPeriod?.year, selectedPeriod?.month],
    queryFn: ({ signal }) =>
      annualLeaveApi.list(
        { ...monthParts(selectedPeriod!), paymentPending: true, page: 0, size: 1 },
        signal,
      ),
    enabled: !!selectedPeriod,
  });

  // Lock is a month-level fact, but it's stored per row — so the month counts
  // as locked once every calculated row carries lockStatus. Cheaper than a
  // dedicated endpoint: a size-1 page just for its totalElements.
  const { data: lockedData } = useQuery({
    queryKey: [
      'annual-leave',
      'summary',
      'locked',
      selectedPeriod?.year,
      selectedPeriod?.month,
    ],
    queryFn: ({ signal }) =>
      annualLeaveApi.list(
        { ...monthParts(selectedPeriod!), lockStatus: true, page: 0, size: 1 },
        signal,
      ),
    enabled: !!selectedPeriod,
  });

  // GET /annual-leave only accepts employeeCode as a text filter, not name -
  // so a name search can't be pushed to the backend as a query param. While
  // searching, fetch a large batch instead of the normal page and match
  // name/code client-side against that; otherwise page normally server-side.
  const isSearchingDetails = debouncedDetailSearch !== '';
  const { data: detailData, isFetching: isDetailFetching } = useQuery({
    queryKey: [
      'annual-leave',
      'details',
      selectedPeriod?.year,
      selectedPeriod?.month,
      paymentFilter,
      debouncedDetailSearch,
      isSearchingDetails ? 'search' : detailPage,
      isSearchingDetails ? DETAIL_SEARCH_FETCH_SIZE : detailPageSize,
    ],
    queryFn: ({ signal }) =>
      annualLeaveApi.list(
        {
          ...monthParts(selectedPeriod!),
          ...filterParams(paymentFilter),
          page: isSearchingDetails ? 0 : detailPage - 1,
          size: isSearchingDetails ? DETAIL_SEARCH_FETCH_SIZE : detailPageSize,
        },
        signal,
      ),
    enabled: !!selectedPeriod,
  });

  const calculateMutation = useMutation({
    mutationFn: annualLeaveApi.calculate,
    onSuccess: (result) => {
      if (!selectedPeriod) return;

      void queryClient.invalidateQueries({ queryKey: ['annual-leave'] });

      if (result.failedCount > 0) {
        message.warning(
          `Calculated ${result.successCount} employees. ${result.failedCount} failed - see errors below.`,
        );
      } else {
        message.success(`Annual leave calculated for ${result.successCount} employees.`);
      }
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const paymentStatusMutation = useMutation({
    mutationFn: ({ id, paymentStatus }: { id: number; paymentStatus: boolean }) =>
      annualLeaveApi.setPaymentStatus(id, paymentStatus),
    onSuccess: (_result, { paymentStatus }) => {
      // A payment change only moves a row between paid / unpaid / not marked,
      // so refetch just the table (with whatever filter is applied - its
      // query key carries the filter) and those three counts. The month total
      // and lock count can't change here, so they're left alone.
      if (!selectedPeriod) return;
      const { year, month } = selectedPeriod;
      for (const key of [
        ['annual-leave', 'details', year, month, paymentFilter],
        ['annual-leave', 'summary', 'paid', year, month],
        ['annual-leave', 'summary', 'unpaid', year, month],
        ['annual-leave', 'summary', 'not-marked', year, month],
      ]) {
        void queryClient.invalidateQueries({ queryKey: key });
      }
      message.success(paymentStatus ? 'Marked as paid.' : 'Marked as unpaid.');
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  // Month-level lock / unlock — one action for the whole calculation, same
  // shape as attendance period locks. Invalidates every query for the month
  // so rows, KPIs and the lock banner all re-read together.
  const invalidatePeriod = () => {
    void queryClient.invalidateQueries({ queryKey: ['annual-leave'] });
  };

  // The backend owns the whole check: it refuses while any row is still
  // not marked (no payment decision), then locks the month in one update.
  const lockPeriodMutation = useMutation({
    mutationFn: annualLeaveApi.lockMonth,
    onSuccess: (result) => {
      invalidatePeriod();
      message.success(
        `${formatPeriodLong(result.year, result.month)} locked (${result.lockedCount} records).`,
      );
    },
    onError: (err) => {
      invalidatePeriod();
      message.error(getErrorMessage(err));
    },
  });

  const total = data?.totalElements ?? 0;
  const paidCount = paidData?.totalElements ?? 0;
  const unpaidCount = unpaidData?.totalElements ?? 0;
  const pendingCount = notMarkedData?.totalElements ?? 0;
  const lockedCount = lockedData?.totalElements ?? 0;
  // Every calculated row locked = the month is closed. A partial count means
  // a lock only got halfway; treat that as still open so it can be re-run.
  const monthLocked = total > 0 && lockedCount === total;
  const periodLockPending = lockPeriodMutation.isPending;
  const fetchedDetailRows = detailData?.content ?? [];
  const searchTerm = debouncedDetailSearch.toLowerCase();
  const filteredDetailRows = isSearchingDetails
    ? fetchedDetailRows.filter(
        (row) =>
          (row.employee?.fullName ?? '').toLowerCase().includes(searchTerm) ||
          row.employeeCode.toLowerCase().includes(searchTerm),
      )
    : fetchedDetailRows;
  // Server already returned exactly one page when not searching; when
  // searching, slice the client-filtered batch into the current page.
  const detailRows = isSearchingDetails
    ? filteredDetailRows.slice((detailPage - 1) * detailPageSize, detailPage * detailPageSize)
    : filteredDetailRows;
  const detailTotal = isSearchingDetails ? filteredDetailRows.length : (detailData?.totalElements ?? 0);
  const selectPaymentFilter = (filter: PaymentFilter) => {
    setPaymentFilter(filter);
    setDetailPage(1);
  };


  const periodLabel = formatPeriodLong(selectedPeriod.year, selectedPeriod.month);
  const confirmLockPeriod = () => {
    // Pre-check from the KPI count so the user isn't asked to confirm a lock
    // the backend will refuse anyway. The backend still enforces it.
    if (pendingCount > 0) {
      modal.warning({
        title: `${periodLabel} can't be locked yet`,
        content: `${pendingCount} employee${pendingCount === 1 ? ' has' : 's have'} no payment status. Mark each one as Paid or Unpaid first - locking would freeze them with neither a payment nor a leave carry-forward.`,
        okText: 'Show not-marked employees',
        onOk: () => selectPaymentFilter('PENDING'),
      });
      return;
    }
    modal.confirm({
      title: `Lock ${periodLabel}?`,
      content:
        "The whole month's annual-leave calculation freezes permanently — it can't be recalculated or marked paid, and there's no way to unlock it afterward. Make sure everything is correct first.",
      okText: 'Lock month',
      okButtonProps: { danger: true },
      onOk: () => lockPeriodMutation.mutateAsync(monthParts(selectedPeriod)),
    });
  };
  const confirmCalculate = () =>
    modal.confirm({
      title: `Calculate ${periodLabel}?`,
      content:
        'Runs the annual-leave calculation for every eligible employee this month. This may take a moment.',
      okText: 'Calculate',
      onOk: () => calculateMutation.mutateAsync(monthParts(selectedPeriod)),
    });

  const {
    columns: visibleBaseColumns,
    options: columnOptions,
    toggle: toggleColumn,
    reset: resetColumns,
  } = useTableColumns('people.annual-leave-transaction.cols.v4', annualLeaveDetailColumns!, {
    defaultHidden: ANNUAL_LEAVE_DEFAULT_HIDDEN_COLUMNS,
  });

  // Payment marking moves money-facing state, so both directions ask first.
  // Used by the table's Action column.
  const confirmSetPaymentStatus = (row: AnnualLeaveDto, paymentStatus: boolean) => {
    const name = row.employee?.fullName ?? row.employeeCode;
    modal.confirm({
      title: paymentStatus ? `Mark ${name} as paid?` : `Mark ${name} as unpaid?`,
      content: paymentStatus
        ? `Records the annual-leave payment of ${row.netAmount.toLocaleString()} for ${periodLabel} as paid.${row.paymentStatus === false ? ' Leave days carried forward for this record are removed.' : ''}`
        : `The annual-leave amount of ${row.netAmount.toLocaleString()} for ${periodLabel} is not paid; ${row.payableLeaveDays} payable leave day${row.payableLeaveDays === 1 ? '' : 's'} carry forward to the next leave cycle instead.`,
      okText: paymentStatus ? 'Set Paid' : 'Set Unpaid',
      okButtonProps: paymentStatus ? undefined : { danger: true },
      onOk: () => paymentStatusMutation.mutateAsync({ id: row.id, paymentStatus }),
    });
  };

  // Marking paid/unpaid is a per-record action - no bulk selection, so each
  // row gets its own toggle button rather than a row-selection checkbox
  // column. Always shown, not part of the columns toggle. Both directions
  // stay available until the month is locked, which freezes the button.
  const detailColumns: TableProps<AnnualLeaveDto>['columns'] = [
    ...visibleBaseColumns,
    {
      title: 'Action',
      key: 'paidAction',
      fixed: 'right',
      width: 200,
      render: (_value: unknown, row: AnnualLeaveDto) => (
        <PaymentActions
          row={row}
          disabled={!!row.lockStatus}
          savingStatus={
            paymentStatusMutation.isPending && paymentStatusMutation.variables?.id === row.id
              ? paymentStatusMutation.variables.paymentStatus
              : undefined
          }
          onSetPaid={confirmSetPaymentStatus}
        />
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow="People"
        title={`${formatPeriodLong(selectedPeriod.year, selectedPeriod.month)} Annual Leave transaction`}
        description="Calculate and review transaction lines for the selected month."
        actions={
          <Space>
            <DatePicker
              picker="month"
              format="MMM YYYY"
              value={pendingDate}
              onChange={(d) => setPendingDate(d ?? dayjs())}
              allowClear={false}
              style={{ minWidth: 160 }}
            />
            <Button type="primary" onClick={applyFilter}>
              Search
            </Button>
            {/* Month-end close. Only meaningful once the month has rows;
                once locked, there's no unlock — the action just disappears. */}
            {total > 0 && !monthLocked ? (
              <Button
                icon={<Lock size={14} strokeWidth={1.75} />}
                loading={periodLockPending}
                onClick={confirmLockPeriod}
              >
                Lock month
              </Button>
            ) : null}
          </Space>
        }
      />

      {monthLocked ? (
        <Alert
          type="info"
          showIcon
          icon={<Lock size={14} strokeWidth={1.75} />}
          title={`${periodLabel} is locked`}
          description="The calculation is permanently frozen — no recalculation and no payment marking."
        />
      ) : null}

      {selectedPeriod ? (
        <>
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              {
                key: 'calculation',
                label: 'Calculation',
                children: (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                    {total === 0 && (
                      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                        <Button
                          type="primary"
                          icon={<CalendarRange size={14} strokeWidth={1.75} />}
                          loading={calculateMutation.isPending}
                          onClick={confirmCalculate}
                        >
                          Calculate
                        </Button>
                      </div>
                    )}
                    {calculateMutation.isPending && (
                      <Section title="Calculation in progress">
                        <Progress percent={99} status="active" />
                      </Section>
                    )}
                    {calculateMutation.data && calculateMutation.data.failedCount > 0 && (
                      <Alert
                        type="warning"
                        showIcon
                        title={`${calculateMutation.data.failedCount} employee${calculateMutation.data.failedCount === 1 ? '' : 's'} could not be calculated`}
                        description={calculateMutation.data.failedEmployees
                          .map((e) => `${e.cardNo}: ${e.message}`)
                          .join(' | ')}
                      />
                    )}
                    {total === 0 ? (
                      <EmptyState
                        title="Nothing calculated yet"
                        description={`No annual leave records exist for ${formatPeriodLong(selectedPeriod.year, selectedPeriod.month)} - press Calculate to run.`}
                        size="sm"
                      />
                    ) : null}
                    {total > 0 && (
                      <Section
                        title={`${PAYMENT_FILTER_TITLES[paymentFilter]} employees`}
                        subtitle={
                          !detailData ? (
                            'Loading records'
                          ) : (
                            <>
                              {`${total} records · `}
                              <span style={{ color: token.colorSuccess, fontWeight: 600 }}>Paid {paidCount}</span>
                              {' · '}
                              <span style={{ color: token.colorError, fontWeight: 600 }}>Unpaid {unpaidCount}</span>
                              {' · '}
                              <span style={{ color: token.colorWarning, fontWeight: 600 }}>Not Marked {pendingCount}</span>
                            </>
                          )
                        }
                      >
                        <FilterBar
                          search={{
                            value: detailSearch,
                            onChange: setDetailSearch,
                            placeholder: 'Search by name, employee code…',
                          }}
                          filters={
                            <Select<PaymentFilter>
                              size="small"
                              value={paymentFilter}
                              onChange={selectPaymentFilter}
                              options={PAYMENT_FILTER_OPTIONS}
                              style={{ minWidth: 170 }}
                            />
                          }
                          onReset={paymentFilter !== 'ALL' ? () => selectPaymentFilter('ALL') : undefined}
                          actions={
                            <ColumnsToggle
                              size="small"
                              options={columnOptions}
                              onToggle={toggleColumn}
                              onReset={resetColumns}
                            />
                          }
                          style={{ marginBottom: 12 }}
                        />
                          <Table<AnnualLeaveDto>
                            rowKey="id"
                            dataSource={detailRows}
                            columns={detailColumns}
                            size="small"
                            scroll={{ x: 'max-content' }}
                            // Header sticks under the 56px app top bar and the
                            // horizontal scrollbar stays pinned to the viewport
                            // bottom, so neither needs a scroll to reach.
                            sticky={{ offsetHeader: 56 }}
                            loading={isDetailFetching}
                            pagination={{
                              current: detailPage,
                              pageSize: detailPageSize,
                              total: detailTotal,
                              onChange: (page, pageSize) => {
                                setDetailPage(page);
                                setDetailPageSize(pageSize);
                              },
                              showSizeChanger: true,
                              pageSizeOptions: [10, 20, 50, 100],
                              showTotal: (value, range) => `Showing ${range[0]} to ${range[1]} of ${value} entries`,
                            }}
                            locale={{ emptyText: <Empty description="No records found" /> }}
                          />
                      </Section>
                    )}
                  </div>
                ),
              },
            ]}
          />
        </>
      ) : null}
    </div>
  );
}
