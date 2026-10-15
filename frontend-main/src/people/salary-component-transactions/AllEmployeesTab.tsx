// All employees tab — one row per employee with their current allowance set:
// one column per allowance component, the total, and the month it took
// effect. Columns come from the catalog, so a new allowance component shows
// up without a code change.

import { useQuery } from '@tanstack/react-query';
import { App as AntdApp, Button, Input, Space, Table, Typography, type TableColumnsType } from 'antd';
import { Download, History, Pencil, Search } from 'lucide-react';
import { useState } from 'react';

import { MoneyCell, PersonCell, Section } from '@/components';
import { downloadCsv, formatDateNumeric, getErrorMessage, useDebounced } from '@/lib';

import { allowancesApi } from './allowances-api';
import type { AllowanceColumn, EmployeeAllowanceRow } from './allowances-types';

export type AllEmployeesTabProps = {
  canWrite: boolean;
  onEdit: (row: EmployeeAllowanceRow) => void;
  onHistory: (row: EmployeeAllowanceRow) => void;
};

const { Text } = Typography;

/** "Accommodation Allowance" → "Accommodation"; every column here is an allowance. */
function shortName(name: string) {
  return name.replace(/\s+allowance$/i, '') || name;
}

const PAGE_SIZE = 20;

export function AllEmployeesTab({ canWrite, onEdit, onHistory }: AllEmployeesTabProps) {
  const { message } = AntdApp.useApp();
  const [q, setQ] = useState('');
  const debouncedQ = useDebounced(q.trim(), 300);
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);

  const columnsQuery = useQuery({
    queryKey: ['allowances', 'columns'],
    queryFn: ({ signal }) => allowancesApi.columns(signal),
    staleTime: 5 * 60_000,
  });
  const rowsQuery = useQuery({
    queryKey: ['allowances', 'employees', debouncedQ, page],
    queryFn: ({ signal }) =>
      allowancesApi.employees({ q: debouncedQ || undefined, page, size: PAGE_SIZE }, signal),
    placeholderData: (prev) => prev,
  });

  const allowanceColumns = columnsQuery.data ?? [];

  const exportCsv = async () => {
    setExporting(true);
    try {
      const all = await allowancesApi.allEmployees(debouncedQ || undefined);
      downloadCsv(
        'employee-allowances.csv',
        ['Card no', 'Name', 'Department', 'Section', ...allowanceColumns.map((c) => c.name), 'Total', 'Last changed'],
        all.map((r) => [
          r.employee.employeeCode ?? '',
          r.employee.fullName,
          r.employee.departmentName ?? '',
          r.employee.sectionName ?? '',
          ...allowanceColumns.map((c) => r.amounts[c.code] ?? 0),
          r.total,
          r.lastChanged,
        ]),
      );
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setExporting(false);
    }
  };

  const columns: TableColumnsType<EmployeeAllowanceRow> = [
    {
      title: 'Employee',
      key: 'employee',
      fixed: 'left',
      render: (_v, r) => (
        <PersonCell
          size="compact"
          person={{
            fullName: r.employee.fullName,
            employeeCode: r.employee.employeeCode,
            profilePictureUrl: r.employee.profilePictureUrl,
            // Department and section live here rather than in their own columns,
            // so six allowance columns fit without scrolling.
            subtitle: [r.employee.employeeCode, r.employee.departmentName, r.employee.sectionName]
              .filter(Boolean)
              .join(' · '),
          }}
        />
      ),
    },
    ...allowanceColumns.map((c: AllowanceColumn) => ({
      title: <span title={c.name}>{shortName(c.name)}</span>,
      key: c.code,
      align: 'right' as const,
      render: (_v: unknown, r: EmployeeAllowanceRow) =>
        r.amounts[c.code] ? (
          <div>
            <MoneyCell amount={r.amounts[c.code]} maximumFractionDigits={0} />
            {r.since?.[c.code] && (
              <Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
                since {formatDateNumeric(r.since?.[c.code])}
              </Text>
            )}
          </div>
        ) : (
          '—'
        ),
    })),
    {
      title: 'Total',
      key: 'total',
      align: 'right',
      render: (_v, r) => (
        <strong>
          <MoneyCell amount={r.total} maximumFractionDigits={0} />
        </strong>
      ),
    },
    { title: 'Last changed', key: 'lastChanged', render: (_v, r) => formatDateNumeric(r.lastChanged) },
    {
      title: '',
      key: 'actions',
      fixed: 'right',
      render: (_v, r) => (
        <Space size={4}>
          {canWrite && (
            <Button
              size="small"
              type="text"
              icon={<Pencil size={14} strokeWidth={1.75} />}
              onClick={() => onEdit(r)}
              aria-label="Change allowances"
            />
          )}
          <Button
            size="small"
            type="text"
            icon={<History size={14} strokeWidth={1.75} />}
            onClick={() => onHistory(r)}
            aria-label="History"
          />
        </Space>
      ),
    },
  ];

  return (
    <Section
      title="Current allowances"
      subtitle="What each employee is paid every month by the allowance run."
      actions={
        <Space wrap>
          <Input
            allowClear
            prefix={<Search size={14} strokeWidth={1.75} />}
            placeholder="Name, card no, department, section"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
            style={{ width: 280 }}
          />
          <Button icon={<Download size={14} strokeWidth={1.75} />} loading={exporting} onClick={exportCsv}>
            Export CSV
          </Button>
        </Space>
      }
    >
      <Table<EmployeeAllowanceRow>
        rowKey={(r) => r.employeeId}
        loading={rowsQuery.isFetching || columnsQuery.isLoading}
        dataSource={rowsQuery.data?.content ?? []}
        columns={columns}
        scroll={{ x: 'max-content' }}
        pagination={{
          current: page + 1,
          pageSize: PAGE_SIZE,
          total: rowsQuery.data?.totalElements ?? 0,
          showSizeChanger: false,
          showTotal: (t) => `${t} employees`,
          onChange: (p) => setPage(p - 1),
        }}
        locale={{
          emptyText: rowsQuery.isError ? getErrorMessage(rowsQuery.error) : 'No employees with allowances',
        }}
      />
    </Section>
  );
}
