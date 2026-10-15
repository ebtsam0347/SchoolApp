// History tab — every version of one employee's allowance set, newest
// first. Each save is one version (several a day included); the latest is
// Active, earlier ones Inactive.

import { useQuery } from '@tanstack/react-query';
import { Table, type TableColumnsType } from 'antd';
import { History } from 'lucide-react';

import { EmptyState, MoneyCell, Section, StatusPill } from '@/components';
import { formatDateTimeNumeric, getErrorMessage } from '@/lib';
import { EmployeePicker } from '@/people/EmployeePicker';

import { AllowanceEmployeeCard } from './AllowanceEmployeeCard';
import { allowancesApi } from './allowances-api';
import type { AllowanceEmployee, AllowanceHistoryEntry } from './allowances-types';

export type AllowanceHistoryTabProps = {
  employee: AllowanceEmployee | undefined;
  onEmployeeChange: (employee: AllowanceEmployee | undefined) => void;
};

export function AllowanceHistoryTab({ employee, onEmployeeChange }: AllowanceHistoryTabProps) {
  const columnsQuery = useQuery({
    queryKey: ['allowances', 'columns'],
    queryFn: ({ signal }) => allowancesApi.columns(signal),
    staleTime: 5 * 60_000,
  });
  const historyQuery = useQuery({
    queryKey: ['allowances', 'history', employee?.id],
    queryFn: ({ signal }) => allowancesApi.history(employee!.id, signal),
    enabled: !!employee,
  });

  const columns: TableColumnsType<AllowanceHistoryEntry> = [
    ...(columnsQuery.data ?? []).map((c) => ({
      title: c.name,
      key: c.code,
      align: 'right' as const,
      render: (_v: unknown, r: AllowanceHistoryEntry) =>
        r.amounts[c.code] ? <MoneyCell amount={r.amounts[c.code]} maximumFractionDigits={0} /> : '—',
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
    {
      title: 'Status',
      key: 'status',
      render: (_v, r) =>
        r.effectiveTo === null ? (
          <StatusPill tone="success">Active</StatusPill>
        ) : (
          <StatusPill tone="neutral">Inactive</StatusPill>
        ),
    },
    {
      title: 'Created',
      key: 'created',
      render: (_v, r) => (
        <>
          {r.createdBy ?? '—'}
          <div style={{ fontSize: 12, opacity: 0.65 }}>{formatDateTimeNumeric(r.createdAt)}</div>
        </>
      ),
    },
    {
      title: 'Closed',
      key: 'updated',
      render: (_v, r) =>
        r.updatedBy ? (
          <>
            {r.updatedBy}
            <div style={{ fontSize: 12, opacity: 0.65 }}>{formatDateTimeNumeric(r.updatedAt)}</div>
          </>
        ) : (
          '—'
        ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Section title="Employee">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ maxWidth: 360 }}>
            <EmployeePicker
              value={employee?.id}
              staticCandidates={employee ? [{ ...employee }] : undefined}
              onChange={(id) => {
                if (id === undefined) onEmployeeChange(undefined);
              }}
              onSelectCandidate={(c) => {
                if (!c) return;
                const picked: AllowanceEmployee = {
                  id: c.id,
                  fullName: c.fullName,
                  employeeCode: c.employeeCode,
                  departmentName: c.departmentName,
                  sectionName: c.sectionName ?? null,
                  designationName: c.designationName,
                  profilePictureUrl: c.profilePictureUrl ?? null,
                };
                onEmployeeChange(picked);
              }}
              style={{ width: '100%' }}
            />
          </div>
          {employee && (
            <AllowanceEmployeeCard employee={employee} />
          )}
        </div>
      </Section>

      {!employee ? (
        <EmptyState
          icon={History}
          title="Pick an employee"
          description="Every change to their allowances is kept here, newest first."
        />
      ) : (
        <Section title="Versions" subtitle="Newest first. Only the Active version is paid.">
          <Table<AllowanceHistoryEntry>
            rowKey={(r) => r.createdAt}
            loading={historyQuery.isLoading}
            dataSource={historyQuery.data ?? []}
            columns={columns}
            pagination={false}
            scroll={{ x: 'max-content' }}
            locale={{
              emptyText: historyQuery.isError
                ? getErrorMessage(historyQuery.error)
                : 'No allowances recorded for this employee',
            }}
          />
        </Section>
      )}
    </div>
  );
}
