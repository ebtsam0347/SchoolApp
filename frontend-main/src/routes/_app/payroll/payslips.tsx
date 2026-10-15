// /payroll/payslips — my payslips.
//
// ESS list: every payslip on file in a calendar year for the signed-in user.
// Click a row to open the detail drawer with the full line breakdown and
// "Download PDF" (signed MinIO URL). Held / released payslips render with
// the hold reason inline.
//
// Backed by GET /payslips/me?year=YYYY (P1). The payroll service resolves
// the caller's employee id server-side, denormalizes (periodYear, periodMonth),
// and stamps an idempotent signed pdfUrl on each row when generated. Detail
// (line breakdown) still uses the service-direct PayslipDto endpoint.

import { createFileRoute } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import {
  Alert,
  Button,
  Empty,
  Segmented,
  Table,
  Tooltip,
  type TableColumnsType,
} from 'antd';
import { Download, Receipt, RefreshCcw, Wallet } from 'lucide-react';

import {
  EmptyState,
  KpiGrid,
  MoneyCell,
  PageHeader,
  Section,
  StatCard,
  StatusPill,
} from '@/components';
import { useAuth } from '@/auth';
import { getErrorMessage } from '@/lib';
import {
  PAYSLIP_STATUS_LABEL,
  PAYSLIP_STATUS_TONE,
  PayslipDetailDrawer,
  formatPeriod,
  payrollComposites,
  type MyPayslipRow,
} from '@/payroll';

export const Route = createFileRoute('/_app/payroll/payslips')({
  component: MyPayslipsPage,
});

const NOW = new Date();
const CURRENT_YEAR = NOW.getFullYear();
const YEARS = [CURRENT_YEAR, CURRENT_YEAR - 1, CURRENT_YEAR - 2, CURRENT_YEAR - 3];

function MyPayslipsPage() {
  const auth = useAuth();
  const me = auth.user;
  const [year, setYear] = useState<number>(CURRENT_YEAR);
  const [openId, setOpenId] = useState<number | null>(null);

  const listQuery = useQuery({
    queryKey: ['payroll', 'payslips', 'mine', year],
    queryFn: ({ signal }) => payrollComposites.payslipsMine(year, signal),
    enabled: !!me?.employeeLinked,
  });

  const rows = listQuery.data?.payslips.content ?? [];
  const ytd = rows.reduce(
    (acc, r) => ({
      gross: acc.gross + r.gross,
      net: acc.net + r.net,
      tax: acc.tax + r.tax,
    }),
    { gross: 0, net: 0, tax: 0 },
  );
  const lastPaid = rows.find((r) => r.status === 'PAID' || r.status === 'RELEASED');

  const columns: TableColumnsType<MyPayslipRow> = [
    {
      title: 'Period',
      key: 'period',
      render: (_v, row) => (
        <span style={{ fontWeight: 500 }}>{rowPeriod(row)}</span>
      ),
    },
    {
      title: 'Gross',
      dataIndex: 'gross',
      key: 'gross',
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} />,
    },
    {
      title: 'Deductions',
      key: 'totalDed',
      align: 'right',
      render: (_v, r) => (
        <MoneyCell amount={r.deductions + r.tax + r.eobi} />
      ),
    },
    {
      title: 'Net',
      dataIndex: 'net',
      key: 'net',
      align: 'right',
      render: (v: number) => (
        <MoneyCell amount={v} style={{ fontWeight: 600 }} />
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: MyPayslipRow['status']) => (
        <StatusPill tone={PAYSLIP_STATUS_TONE[s]}>
          {PAYSLIP_STATUS_LABEL[s]}
        </StatusPill>
      ),
    },
    {
      title: '',
      key: 'pdf',
      width: 56,
      align: 'right',
      render: (_v, r) =>
        r.pdfUrl ? (
          <Tooltip title="Download PDF">
            <Button
              type="text"
              size="small"
              icon={<Download size={14} strokeWidth={1.75} />}
              onClick={(e) => {
                e.stopPropagation();
                window.open(r.pdfUrl!, '_blank', 'noopener');
              }}
            />
          </Tooltip>
        ) : null,
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <PageHeader
        eyebrow="Money"
        title="My Payslips"
        description="Every payslip on file for the calendar year. Click a row to see the line-by-line breakdown and download the PDF."
        actions={
          me?.employeeLinked ? (
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => listQuery.refetch()}
                loading={listQuery.isFetching}
                aria-label="Refresh"
              />
            </Tooltip>
          ) : null
        }
      />

      <KpiGrid>
        <StatCard
          label="YTD gross"
          value={<MoneyCell amount={ytd.gross} compact />}
          icon={Wallet}
          tone="primary"
          variant="accent-radial"
        />
        <StatCard
          label="YTD net"
          value={<MoneyCell amount={ytd.net} compact />}
          icon={Receipt}
          tone="success"
        />
        <StatCard
          label="YTD tax"
          value={<MoneyCell amount={ytd.tax} compact />}
          tone="warning"
        />
        <StatCard
          label="Last paid period"
          value={lastPaid ? rowPeriod(lastPaid) : '—'}
          tone="info"
        />
      </KpiGrid>

      <Section
        title={`${year} payslips`}
        actions={
          <Segmented
            value={year}
            onChange={(v) => setYear(Number(v))}
            options={YEARS.map((y) => ({ label: String(y), value: y }))}
          />
        }
      >
        {!me?.employeeLinked ? (
          <EmptyState
            icon={Receipt}
            title="No employee record linked to your account"
            description="Ask HR to link your user to an employee profile before payslips can be served."
          />
        ) : listQuery.isError ? (
          <Alert
            type="error"
            showIcon
            title="Couldn't load payslips"
            description={getErrorMessage(listQuery.error)}
          />
        ) : rows.length === 0 && !listQuery.isLoading ? (
          <EmptyState
            icon={Receipt}
            title={`No payslips for ${year}`}
            description="Payslips appear here once HR posts the monthly payroll run."
          />
        ) : (
          <Table<MyPayslipRow>
            rowKey="id"
            loading={listQuery.isLoading}
            dataSource={rows}
            columns={columns}
            pagination={false}
            // Payslips are read on phones more than anything else here —
            // scroll the columns inside the table, not the page.
            scroll={{ x: 'max-content' }}
            onRow={(r) => ({
              onClick: () => setOpenId(r.id),
              style: { cursor: 'pointer' },
            })}
            locale={{ emptyText: <Empty description="No payslips" /> }}
          />
        )}
      </Section>

      <PayslipDetailDrawer
        open={openId != null}
        id={openId}
        onClose={() => setOpenId(null)}
      />
    </div>
  );
}

function rowPeriod(p: { periodYear: number; periodMonth: number }): string {
  return formatPeriod(p.periodYear, p.periodMonth);
}
