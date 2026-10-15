// /people/termination - submitted final settlements (employee terminations).
//
// List over GET /settlement/employees so HR can see every settlement
// submitted from the Terminate flow and where it stands (Draft → HR
// approved → Paid, or Rejected / Cancelled). Review opens the full
// breakdown in SettlementReviewDrawer, where the stage actions live.

import { useEffect, useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { Button, Empty, Select, Table, Tooltip, type TableProps } from 'antd';
import { Ban, CircleCheck, CircleX, Clock, Eye, ShieldCheck } from 'lucide-react';

import { FilterBar, PageHeader, PersonCell, Section, StatusPill } from '@/components';
import { useDebounced } from '@/lib';
import { formatDateShort, formatDateTime } from '@/lib/dates';
import { settlementApi, type FinalSettlementListDto, type FinalSettlementStatus } from '@/payroll';
import { SettlementReviewDrawer, type SettlementReviewMode } from '@/people/SettlementReviewDrawer';
import { SettlementStatementButton } from '@/people/SettlementStatementButton';

export const Route = createFileRoute('/_app/people/termination')({
  component: TerminationPage,
});

const STATUS_META: Record<
  FinalSettlementStatus,
  { label: string; tone: 'warning' | 'info' | 'success' | 'error' | 'neutral'; icon: typeof Clock }
> = {
  DRAFT: { label: 'Draft', tone: 'warning', icon: Clock },
  HR_APPROVED: { label: 'HR Approved', tone: 'info', icon: ShieldCheck },
  PAID: { label: 'Paid', tone: 'success', icon: CircleCheck },
  REJECTED: { label: 'Rejected', tone: 'error', icon: CircleX },
  CANCELLED: { label: 'Cancelled', tone: 'neutral', icon: Ban },
};

// No "all" option — the list always shows one stage, Draft by default.
const STATUS_FILTER_OPTIONS = (Object.keys(STATUS_META) as FinalSettlementStatus[]).map((value) => ({
  value,
  label: STATUS_META[value].label,
}));

function SettlementStatusPill({ status }: { status: FinalSettlementStatus }) {
  const meta = STATUS_META[status] ?? { label: status, tone: 'neutral' as const, icon: Clock };
  const Icon = meta.icon;
  return (
    <StatusPill tone={meta.tone} size="sm" icon={<Icon size={12} strokeWidth={2} />}>
      {meta.label}
    </StatusPill>
  );
}

function moneyCol(
  title: string,
  dataIndex: keyof FinalSettlementListDto,
  bold = false,
): NonNullable<TableProps<FinalSettlementListDto>['columns']>[number] {
  return {
    title,
    dataIndex,
    key: dataIndex,
    align: 'right',
    width: 110,
    render: (value: number | null) => (
      <span style={bold ? { fontWeight: 600 } : undefined}>{(value ?? 0).toLocaleString()}</span>
    ),
  };
}

const columns: TableProps<FinalSettlementListDto>['columns'] = [
  {
    title: 'Employee',
    key: 'employee',
    fixed: 'left',
    width: 260,
    render: (_value: unknown, row: FinalSettlementListDto) => {
      const org = [
        row.department,
        row.section && row.section !== row.department ? row.section : null,
      ]
        .filter(Boolean)
        .join(' › ');
      return (
        <PersonCell
          size="compact"
          person={{
            fullName: row.employeeName ?? row.employeeCode,
            employeeCode: row.employeeCode,
            subtitle: (
              <>
                <span style={{ display: 'block' }}>
                  {row.designation ? `${row.designation} · ` : null}
                  {row.employeeCode}
                </span>
                {org ? <span style={{ display: 'block' }}>{org}</span> : null}
              </>
            ),
          }}
        />
      );
    },
  },
  {
    title: 'Grade',
    key: 'grade',
    width: 130,
    render: (_value: unknown, row: FinalSettlementListDto) =>
      [row.grade, row.gradeType].filter(Boolean).join(' · ') || '—',
  },
  {
    title: 'Hire Date',
    dataIndex: 'hireDate',
    key: 'hireDate',
    width: 110,
    render: (value: string | null) => formatDateShort(value),
  },
  {
    title: 'Last Working Day',
    dataIndex: 'lastWorkingDay',
    key: 'lastWorkingDay',
    width: 120,
    render: (value: string) => formatDateShort(value),
  },
  moneyCol('Gross', 'grossSettlement'),
  moneyCol('Deductions', 'totalDeductions'),
  moneyCol('Net Payable', 'netSettlement', true),
  {
    title: 'Status',
    dataIndex: 'status',
    key: 'status',
    width: 130,
    render: (value: FinalSettlementStatus) => <SettlementStatusPill status={value} />,
  },
  {
    title: 'Remarks',
    dataIndex: 'remarks',
    key: 'remarks',
    width: 200,
    ellipsis: { showTitle: false },
    render: (value: string | null) =>
      value ? (
        <Tooltip title={value}>
          <span>{value}</span>
        </Tooltip>
      ) : (
        '—'
      ),
  },
  {
    title: 'Submitted',
    dataIndex: 'createdAt',
    key: 'createdAt',
    width: 150,
    render: (value: string) => formatDateTime(value),
  },
];

function TerminationPage() {
  const [active, setActive] = useState<{
    row: FinalSettlementListDto;
    mode: SettlementReviewMode;
  } | null>(null);
  const [statusFilter, setStatusFilter] = useState<FinalSettlementStatus>('DRAFT');
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounced(search.trim(), 250);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  useEffect(() => {
    setPage(1);
  }, [debouncedSearch, statusFilter]);

  // Backend filters code and name separately — digits-only reads as a code.
  const isCode = /^\d+$/.test(debouncedSearch);

  const { data, isFetching } = useQuery({
    queryKey: ['settlements', 'list', statusFilter, debouncedSearch, page, pageSize],
    queryFn: ({ signal }) =>
      settlementApi.list(
        {
          status: statusFilter,
          employeeCode: debouncedSearch && isCode ? debouncedSearch : undefined,
          employeeName: debouncedSearch && !isCode ? debouncedSearch : undefined,
          page: page - 1,
          size: pageSize,
          sort: 'createdAt,DESC',
        },
        signal,
      ),
    placeholderData: (prev) => prev,
    // Settlements are created from the employee page; always refetch on
    // arrival instead of serving the 30s-stale cached list.
    refetchOnMount: 'always',
  });

  const rows = data?.content ?? [];
  const total = data?.totalElements ?? 0;

  // Both open the Review modal: Draft → approve / reject; HR approved →
  // cancel the approval (back to Draft). Rejected, paid and cancelled rows
  // have no further action. Print Statement works at every stage.
  const tableColumns: TableProps<FinalSettlementListDto>['columns'] = [
    ...columns!,
    {
      title: 'Action',
      key: 'action',
      fixed: 'right',
      width: 150,
      render: (_value: unknown, row: FinalSettlementListDto) => {
        const mode: SettlementReviewMode | null =
          row.status === 'DRAFT' ? 'review' : row.status === 'HR_APPROVED' ? 'cancel' : null;
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            {mode ? (
              <Button
                size="small"
                type="primary"
                icon={<Eye size={12} strokeWidth={2} />}
                onClick={() => setActive({ row, mode })}
              >
                Review
              </Button>
            ) : null}
            <SettlementStatementButton
              iconOnly
              size="small"
              settlementId={row.settlementId}
              status={row.status}
            />
          </div>
        );
      },
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow="People"
        title="Termination Stages"
        description="Full & final settlements for leaving employees, tracked through each stage — Draft, HR Approved, Paid."
      />

      <Section title="Final settlements" subtitle={data ? `${total} records` : 'Loading records'}>
        <FilterBar
          search={{
            value: search,
            onChange: setSearch,
            placeholder: 'Search by name or employee code…',
          }}
          filters={
            <Select<FinalSettlementStatus>
              size="small"
              value={statusFilter}
              onChange={setStatusFilter}
              options={STATUS_FILTER_OPTIONS}
              style={{ minWidth: 150 }}
            />
          }
          onReset={statusFilter !== 'DRAFT' ? () => setStatusFilter('DRAFT') : undefined}
          style={{ marginBottom: 12 }}
        />
        <Table<FinalSettlementListDto>
          rowKey="settlementId"
          dataSource={rows}
          columns={tableColumns}
          size="small"
          scroll={{ x: 'max-content' }}
          sticky={{ offsetHeader: 56 }}
          loading={isFetching}
          pagination={{
            current: page,
            pageSize,
            total,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (value, range) => `Showing ${range[0]} to ${range[1]} of ${value} entries`,
          }}
          locale={{ emptyText: <Empty description="No settlements found" /> }}
        />
      </Section>

      {active ? (
        <SettlementReviewDrawer
          key={active.row.settlementId}
          settlementId={active.row.settlementId}
          mode={active.mode}
          statusPill={(status) => <SettlementStatusPill status={status} />}
          onClose={() => setActive(null)}
        />
      ) : null}
    </div>
  );
}
