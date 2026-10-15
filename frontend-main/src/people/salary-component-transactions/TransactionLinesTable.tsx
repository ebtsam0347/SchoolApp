// Transaction lines table + card grid for one salary component transaction.
// One row/card per employee (from the /transactions endpoint), expandable
// (table) or inline (cards) to show that employee's individual allowance
// lines. Mirrors the Annual Leave Transactions detail table: search,
// export, column toggle, and a Table/Cards view switch all live on the
// route page — this file only owns row shaping + rendering.

import {
  Empty,
  Skeleton,
  Table,
  Typography,
  theme as antdTheme,
  type TableColumnsType,
} from 'antd';

import { MoneyCell, OrgPath, PersonCell, StatusPill, moneyColumn } from '@/components';
import { transactionLineStatusView } from './format';
import type {
  TransactionEmployeeDto,
  TransactionComponentDto,
  TransactionEmployeeSnapshot,
  SalaryComponentTransactionDto,
} from './types';

const { Text } = Typography;
const { useToken } = antdTheme;

/** One row per employee — components stay nested for the expanded panel. */
export type TransactionEmployeeRow = TransactionEmployeeDto & {
  key: string;
  totalAmount: number;
};

export function toTransactionEmployeeRows(
  employees: TransactionEmployeeDto[],
): TransactionEmployeeRow[] {
  return employees.map((emp) => ({
    ...emp,
    key: String(emp.employeeId),
    totalAmount: emp.components.reduce((sum, c) => sum + c.amount, 0),
  }));
}

function orgSegments(employee: TransactionEmployeeSnapshot | null | undefined) {
  const deptName = employee?.departmentName;
  const secName = employee?.sectionName;
  const showSection = secName && secName !== deptName;
  return [
    deptName ? { label: deptName } : null,
    showSection ? { label: secName } : null,
  ];
}

/** Module-level so it can feed `useTableColumns` on the route page. */
export const transactionEmployeeColumns: TableColumnsType<TransactionEmployeeRow> = [
  {
    title: 'Employee',
    key: 'employee',
    render: (_v, row) => (
      <PersonCell
        size="compact"
        person={{
          fullName: row.employee?.fullName ?? row.employeeCode,
          employeeCode: row.employeeCode,
          profilePictureUrl: row.employee?.profilePictureUrl,
          designation: row.employee?.designationName,
        }}
      />
    ),
  },
  {
    title: 'Department / Section',
    key: 'org',
    render: (_v, row) => <OrgPath compact segments={orgSegments(row.employee)} />,
  },
  {
    title: 'Grade',
    dataIndex: 'grade',
    key: 'grade',
    width: 90,
  },
  {
    title: 'Fiscal Year',
    dataIndex: 'fiscalYearCode',
    key: 'fiscalYearCode',
    width: 110,
  },
  {
    title: 'Allowances Count',
    key: 'allowanceCount',
    align: 'right',
    width: 110,
    render: (_v, row) => row.components.length,
  },
  moneyColumn<TransactionEmployeeRow>({
    title: 'Total Amount',
    dataIndex: (r) => r.totalAmount,
    width: 140,
  }),
];

/* -------------------------------- Table -------------------------------- */

export type TransactionLinesTableProps = {
  rows: TransactionEmployeeRow[];
  columns: TableColumnsType<TransactionEmployeeRow>;
  loading: boolean;
  transactionStatus: SalaryComponentTransactionDto['status'];
  page: number;
  pageSize: number;
  totalElements: number;
  onPageChange: (page: number, pageSize: number) => void;
};

export function TransactionLinesTable({
  rows,
  columns,
  loading,
  transactionStatus,
  page,
  pageSize,
  totalElements,
  onPageChange,
}: TransactionLinesTableProps) {
  return (
    <Table<TransactionEmployeeRow>
      rowKey="key"
      loading={loading}
      dataSource={rows}
      columns={columns}
      expandable={{
        expandedRowRender: (row) => (
          <EmployeeLinesPanel
            components={row.components}
            transactionStatus={transactionStatus}
          />
        ),
        expandRowByClick: true,
        showExpandColumn: false,
      }}
      onRow={() => ({
        title: 'Click to view allowance details',
        style: { cursor: 'pointer' },
      })}
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
        emptyText: (
          <Empty
            description={
              transactionStatus === 'COMPUTING'
                ? 'Computing lines…'
                : 'No lines matched the filter.'
            }
          />
        ),
      }}
      scroll={{ x: 900 }}
    />
  );
}

/* ------------------------- Expanded row panel -------------------------- */

function EmployeeLinesPanel({
  components,
  transactionStatus,
}: {
  components: TransactionComponentDto[];
  transactionStatus: SalaryComponentTransactionDto['status'];
}) {
  const total = components.reduce((sum, c) => sum + c.amount, 0);

  return (
    <Table<TransactionComponentDto>
      rowKey="componentId"
      size="small"
      dataSource={components}
      columns={getComponentColumns(transactionStatus)}
      pagination={false}
      summary={() => (
        <Table.Summary.Row>
          <Table.Summary.Cell index={0}>
            <Text strong>Total</Text>
          </Table.Summary.Cell>
          <Table.Summary.Cell index={1} align="right">
            <MoneyCell amount={total} style={{ fontWeight: 600 }} />
          </Table.Summary.Cell>
          <Table.Summary.Cell index={2} />
        </Table.Summary.Row>
      )}
    />
  );
}

function getComponentColumns(
  transactionStatus: SalaryComponentTransactionDto['status'],
): TableColumnsType<TransactionComponentDto> {
  return [
    {
      title: 'Component',
      dataIndex: 'name',
      key: 'name',
    },
    moneyColumn<TransactionComponentDto>({
      title: 'Amount',
      dataIndex: (c) => c.amount,
      width: 140,
    }),
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (s: TransactionComponentDto['status']) => {
        const { label, tone } = transactionLineStatusView(s, transactionStatus);
        return <StatusPill tone={tone}>{label}</StatusPill>;
      },
    },
  ];
}

/* -------------------------------- Cards --------------------------------- */

const cardGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
  gap: 12,
};

export type TransactionEmployeeCardGridProps = {
  rows: TransactionEmployeeRow[];
  loading: boolean;
  transactionStatus: SalaryComponentTransactionDto['status'];
};

export function TransactionEmployeeCardGrid({
  rows,
  loading,
  transactionStatus,
}: TransactionEmployeeCardGridProps) {
  const { token } = useToken();

  const cardStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
    padding: 14,
    background: token.colorBgContainer,
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: token.borderRadiusLG,
  };

  if (loading && rows.length === 0) {
    return (
      <div style={cardGridStyle}>
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} style={cardStyle}>
            <Skeleton avatar active paragraph={{ rows: 3 }} />
          </div>
        ))}
      </div>
    );
  }

  if (rows.length === 0) {
    return <Empty description="No lines matched the filter." />;
  }

  return (
    <div style={cardGridStyle}>
      {rows.map((row) => (
        <div key={row.key} style={cardStyle}>
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
            <PersonCell
              size="default"
              person={{
                fullName: row.employee?.fullName ?? row.employeeCode,
                employeeCode: row.employeeCode,
                profilePictureUrl: row.employee?.profilePictureUrl,
                designation: row.employee?.designationName,
              }}
            />
            <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
              {row.components.length} allowance{row.components.length === 1 ? '' : 's'}
            </Text>
          </div>

          <OrgPath compact segments={orgSegments(row.employee)} />

          <Text type="secondary" style={{ fontSize: 12 }}>
            Grade {row.grade} · FY {row.fiscalYearCode}
          </Text>

          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
              paddingTop: 8,
              borderTop: `1px solid ${token.colorBorderSecondary}`,
            }}
          >
            {row.components.map((c) => {
              const { label, tone } = transactionLineStatusView(c.status, transactionStatus);
              return (
                <div
                  key={c.componentId}
                  style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}
                >
                  <Text style={{ fontSize: 12, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {c.name}
                  </Text>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                    <MoneyCell amount={c.amount} style={{ fontSize: 12 }} />
                    <StatusPill tone={tone} size="sm">
                      {label}
                    </StatusPill>
                  </div>
                </div>
              );
            })}
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              paddingTop: 8,
              borderTop: `1px solid ${token.colorBorderSecondary}`,
            }}
          >
            <Text type="secondary" style={{ fontSize: 11 }}>
              Total amount
            </Text>
            <Text strong style={{ fontSize: 14 }}>
              {row.totalAmount.toLocaleString()}
            </Text>
          </div>
        </div>
      ))}
    </div>
  );
}
