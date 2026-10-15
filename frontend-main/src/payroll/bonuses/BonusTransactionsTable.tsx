// Per-employee bonus transactions for one bonus run. Mirrors
// RunPayslipsTable: money columns use the shared moneyColumn factory
// (tabular nums), status renders as a tone-mapped pill, pagination is
// server-side.

import { Empty, Table, type TableColumnsType } from 'antd';
import { MoneyCell, StatusPill, employeeColumn, moneyColumn } from '@/components';
import {
  BONUS_PAYMENT_MODE_LABEL,
  PAYSLIP_STATUS_LABEL,
  PAYSLIP_STATUS_TONE,
  formatDateShort,
  type BonusPaymentMode,
  type BonusTransactionDto,
  type PayslipStatus,
} from '@/payroll';

export type BonusTransactionsTableProps = {
  rows: BonusTransactionDto[];
  loading: boolean;
  /** 0-based current page (matches Spring). */
  page: number;
  pageSize: number;
  totalElements: number;
  /** AntD pagination is 1-based; the route handles the offset. */
  onPageChange: (page: number, pageSize: number) => void;
};

export function BonusTransactionsTable({
  rows,
  loading,
  page,
  pageSize,
  totalElements,
  onPageChange,
}: BonusTransactionsTableProps) {
  const columns: TableColumnsType<BonusTransactionDto> = [
    employeeColumn<BonusTransactionDto>({
      // `myEmployee` is the server-side enrichment; fall back to the code
      // when the employee lookup came back empty.
      employee: (r) =>
        r.myEmployee ?? {
          id: r.employeeId,
          fullName: r.employeeCode || `#${r.employeeId}`,
          employeeCode: r.employeeCode,
        },
    }),
    {
      title: 'Joined',
      dataIndex: 'dateOfJoining',
      key: 'dateOfJoining',
      render: (v: string | null) => (v ? formatDateShort(v) : '—'),
    },
    moneyColumn<BonusTransactionDto>({
      title: 'Basic salary',
      key: 'basicSalary',
      dataIndex: (r) => r.basicSalary,
    }),
    {
      title: '% of salary',
      dataIndex: 'percentageOfSalary',
      key: 'percentageOfSalary',
      align: 'right',
      render: (v: number | null) => (v == null ? '—' : `${v}%`),
    },
    {
      // Full FY, or joining date → FY end for mid-year joiners.
      title: 'No. of days',
      dataIndex: 'noOfDays',
      key: 'noOfDays',
      align: 'right',
      render: (v: number | null) => (
        <span style={{ fontVariantNumeric: 'tabular-nums' }}>{v ?? '—'}</span>
      ),
    },
    moneyColumn<BonusTransactionDto>({
      title: 'Bonus amount',
      key: 'percentageSalaryAmount',
      dataIndex: (r) => r.percentageSalaryAmount,
    }),
    moneyColumn<BonusTransactionDto>({
      title: 'LTA deduction',
      key: 'deductionAsLongTermAdvance',
      dataIndex: (r) => r.deductionAsLongTermAdvance,
    }),
    moneyColumn<BonusTransactionDto>({
      title: 'RFD advance',
      key: 'rfdAdvanceAdded',
      dataIndex: (r) => r.rfdAdvanceAdded,
    }),
    {
      title: 'Net',
      dataIndex: 'netAmount',
      key: 'netAmount',
      align: 'right',
      render: (v: number) => (
        <MoneyCell amount={v} style={{ fontWeight: 600 }} />
      ),
    },
    {
      title: 'Payment',
      dataIndex: 'paymentMode',
      key: 'paymentMode',
      render: (m: BonusPaymentMode | null) =>
        m ? BONUS_PAYMENT_MODE_LABEL[m] : '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: PayslipStatus) => (
        <StatusPill tone={PAYSLIP_STATUS_TONE[s]}>{PAYSLIP_STATUS_LABEL[s]}</StatusPill>
      ),
    },
  ];

  return (
    <Table<BonusTransactionDto>
      rowKey="id"
      loading={loading}
      dataSource={rows}
      columns={columns}
      pagination={{
        current: page + 1,
        pageSize,
        total: totalElements,
        showSizeChanger: true,
        pageSizeOptions: [20, 50, 100, 200],
        showTotal: (t, [from, to]) => `${from}–${to} of ${t}`,
        onChange: (next, nextSize) => onPageChange(next - 1, nextSize),
      }}
      locale={{
        emptyText: <Empty description="No bonus transactions matched the filter." />,
      }}
      scroll={{ x: 1200 }}
    />
  );
}
