// /people/queues/hr-lta-print — HR's long-term advance (LTA) print queue.
//
// Lists advances HR has approved that Accounts hasn't yet (status
// HR_APPROVED), from GET /permanent-advances/hr-approved-receiving-list,
// and prints each one as the "Permanent Advance Receiving List" slip (see
// payroll/advances/receiving-list-pdf.ts). Optional employee filter maps to
// the endpoint's `employeeId`. Requires `payroll.advance.hr`.

import { useQuery } from '@tanstack/react-query';
import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router';
import { Alert, Button, Space, Tooltip, theme as antdTheme } from 'antd';
import type { TableColumnsType } from 'antd';
import { FileDown, Printer, RefreshCcw } from 'lucide-react';

import type { EmployeeSummary } from '@/attendance';
import { EmptyState, PageHeader, RequestState, Section } from '@/components';
import { EmployeePicker } from '@/people';
import { permanentAdvancesApi, type PermanentAdvanceDto } from '@/payroll';
import {
  PermanentAdvanceTable,
  printReceivingList,
  receivingListPalette,
} from '@/payroll/advances';
import { useTheme } from '@/theme';

type Search = {
  employeeId?: number;
  page?: number;
  size?: number;
};

function numOrUndef(v: unknown): number | undefined {
  const n = Number(v);
  return typeof v !== 'undefined' && v !== '' && !Number.isNaN(n) ? n : undefined;
}

export const Route = createFileRoute('/_app/people/queues/hr-lta-print')({
  validateSearch: (search): Search => ({
    employeeId: numOrUndef(search.employeeId),
    page: numOrUndef(search.page),
    size: numOrUndef(search.size),
  }),
  component: RouteComponent,
});

function RouteComponent() {
  const navigate = useNavigate({ from: '/people/queues/hr-lta-print' });
  const search = useSearch({ from: '/_app/people/queues/hr-lta-print' });
  const page = search.page ?? 1;
  const size = search.size ?? 20;
  // The slip takes its colours from the active theme.
  const { token } = antdTheme.useToken();
  const { theme } = useTheme();
  const palette = receivingListPalette(token, theme.surface);

  const listQuery = useQuery({
    queryKey: ['payroll', 'permanent-advances', 'hr-approved-receiving-list', search],
    queryFn: ({ signal }) =>
      permanentAdvancesApi.hrApprovedReceivingList(
        { employeeId: search.employeeId, page: page - 1, size },
        signal,
      ),
  });
  const rows = listQuery.data?.content ?? [];
  const totalElements = listQuery.data?.totalElements ?? 0;

  const updateSearch = (patch: Partial<Search>) =>
    navigate({ search: (prev) => ({ ...prev, ...patch }), replace: true });

  // Same columns as the Approvals Inbox's long-term advance table; the
  // employee summaries come inlined on each row.
  const employees = new Map<number, EmployeeSummary>();
  for (const r of rows) if (r.employee) employees.set(r.employeeId, r.employee);
  const rowById = new Map(rows.map((r) => [r.id, r]));

  const printColumn: TableColumnsType<PermanentAdvanceDto> = [
    {
      title: '',
      key: 'pdf',
      width: 90,
      align: 'right',
      fixed: 'right',
      render: (_v, r) => (
        <Button
          size="small"
          icon={<FileDown size={14} strokeWidth={1.75} />}
          onClick={() => printReceivingList([rowById.get(r.id) ?? r], palette)}
        >
          Print
        </Button>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, width: '100%' }}>
      <PageHeader
        eyebrow="People"
        title="HR LTA Print"
        description="Print the receiving list slip for long-term advances approved by HR."
        actions={
          <Space>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => listQuery.refetch()}
                loading={listQuery.isFetching}
                aria-label="Refresh"
              />
            </Tooltip>
            <Button onClick={() => navigate({ to: '/people' })}>Back to People</Button>
          </Space>
        }
      />

      <Alert
        type="info"
        showIcon
        title="Only advances approved at the HR stage and still pending at the Accounts stage are shown here."
        description="Once Accounts approves or rejects an advance, it leaves this list."
      />

      <Section
        title="Long-term advances"
        subtitle={`${totalElements.toLocaleString('en-PK')} awaiting Accounts`}
        styles={{ root: { minWidth: 0 } }}
        actions={
          <EmployeePicker
            allowClear
            placeholder="Filter by employee"
            style={{ width: 'clamp(180px, 50vw, 260px)' }}
            value={search.employeeId}
            onChange={(v) => updateSearch({ employeeId: v, page: 1 })}
          />
        }
      >
        <RequestState
          loading={listQuery.isLoading}
          variant="table"
          error={listQuery.isError ? listQuery.error : undefined}
          errorTitle="Couldn't load advances"
          onRetry={() => listQuery.refetch()}
          empty={!listQuery.isLoading && rows.length === 0}
          filterActive={search.employeeId != null}
          onClearFilters={() => updateSearch({ employeeId: undefined, page: 1 })}
          emptyFallback={
            <EmptyState
              icon={Printer}
              title="Nothing to print"
              description="No long-term advances are waiting on Accounts after HR approval."
            />
          }
        >
          <PermanentAdvanceTable
            data={rows}
            loading={listQuery.isFetching}
            inboxLayout
            showEmployee
            employees={employees}
            hideStage
            statusLabels={{ HR_APPROVED: 'HR Approved' }}
            extraColumns={printColumn}
            pagination={{
              current: page,
              pageSize: size,
              total: totalElements,
              showSizeChanger: true,
              pageSizeOptions: ['10', '20', '50', '100'],
              showTotal: (n, [from, to]) => `${from}-${to} of ${n.toLocaleString('en-PK')}`,
            }}
            onChange={(pagination) =>
              updateSearch({ page: pagination.current, size: pagination.pageSize })
            }
          />
        </RequestState>
      </Section>
    </div>
  );
}
