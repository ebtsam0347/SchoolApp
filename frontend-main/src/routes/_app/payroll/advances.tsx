// /payroll/advances — long-term salary advances.
//
// ESS list of the caller's own advances + a "Request advance" button.
// Self-apply only — payroll-service takes an on-behalf `employeeId` from HR
// holding `payroll.write` and nobody else.
// HR / Accounts work company-wide balances and manual deductions from
// /payroll/advance-ledger.

import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { Button, Segmented, Space } from 'antd';
import {
  Banknote,
  CheckCircle2,
  Plus,
  Sparkles,
  Wallet,
  XCircle,
} from 'lucide-react';
import { useState } from 'react';

import { useAuth } from '@/auth';
import {
  EmptyState,
  KpiGrid,
  PageHeader,
  RequestState,
  Section,
  StatCard,
} from '@/components';
import {
  ADVANCE_STATUS_LABEL,
  permanentAdvancesApi,
  type AdvanceStatus,
} from '@/payroll';
import {
  ApplyPermanentAdvanceDrawer,
  PermanentAdvanceTable,
} from '@/payroll/advances';
import { employeesApi } from '@/people/api';

export const Route = createFileRoute('/_app/payroll/advances')({
  component: AdvancesPage,
});

type StatusFilter = AdvanceStatus | 'ALL';

const STATUS_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'PENDING', label: ADVANCE_STATUS_LABEL.PENDING },
  { value: 'MANAGER_APPROVED', label: ADVANCE_STATUS_LABEL.MANAGER_APPROVED },
  { value: 'HR_APPROVED', label: ADVANCE_STATUS_LABEL.HR_APPROVED },
  { value: 'APPROVED', label: ADVANCE_STATUS_LABEL.APPROVED },
  { value: 'REJECTED', label: ADVANCE_STATUS_LABEL.REJECTED },
];

function AdvancesPage() {
  const auth = useAuth();
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [openApply, setOpenApply] = useState(false);

  const meQuery = useQuery({
    queryKey: ['employees', 'me'],
    queryFn: ({ signal }) => employeesApi.me(signal),
    enabled: auth.user?.employeeLinked === true,
    staleTime: 5 * 60_000,
    retry: false,
  });
  const myEmployeeId = meQuery.data?.id ?? null;

  const mineQuery = useQuery({
    queryKey: [
      'payroll',
      'permanent-advances',
      'mine',
      myEmployeeId,
      statusFilter,
    ],
    queryFn: ({ signal }) =>
      permanentAdvancesApi.listForEmployee(
        myEmployeeId!,
        { status: statusFilter === 'ALL' ? undefined : statusFilter },
        signal,
      ),
    enabled: myEmployeeId != null,
  });

  const myRows = mineQuery.data?.content ?? [];
  const counts = {
    pending: myRows.filter(
      (r) =>
        r.status === 'PENDING' || r.status === 'MANAGER_APPROVED' || r.status === 'HR_APPROVED',
    ).length,
    approved: myRows.filter(
      (r) => r.status === 'APPROVED',
    ).length,
    rejected: myRows.filter((r) => r.status === 'REJECTED').length,
  };

  const outstanding = myRows
    .filter((r) => r.status === 'APPROVED' && r.remainingBalance != null)
    .reduce((s, r) => s + (r.remainingBalance ?? 0), 0);

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="Money"
        title="Salary advances"
        description="Long-term advances against future salary. Your manager, HR and Accounts review it; once approved, the monthly installment is deducted from each payroll run."
        actions={
          <Space>
            <Button
              type="primary"
              icon={<Plus size={14} />}
              onClick={() => setOpenApply(true)}
            >
              Request advance
            </Button>
          </Space>
        }
      />

      <KpiGrid minColumnWidth={200}>
        <StatCard
          variant="accent-radial"
          tone="warning"
          icon={Sparkles}
          accentCorner="tr"
          label="Pending"
          value={`${counts.pending}`}
          delta={
            counts.pending === 1 ? 'request in review' : 'requests in review'
          }
        />
        <StatCard
          variant="plain"
          tone="success"
          icon={CheckCircle2}
          label="Approved"
          value={`${counts.approved}`}
          delta="cleared HR + Accounts"
        />
        <StatCard
          variant="plain"
          tone={outstanding > 0 ? 'info' : 'neutral'}
          icon={Wallet}
          label="Outstanding"
          value={
            outstanding > 0
              ? `Rs ${outstanding.toLocaleString('en-PK')}`
              : 'Rs 0'
          }
          delta="being deducted monthly"
        />
        <StatCard
          variant="plain"
          tone={counts.rejected > 0 ? 'error' : 'neutral'}
          icon={XCircle}
          label="Rejected"
          value={`${counts.rejected}`}
        />
      </KpiGrid>

      <Section
        title="My advances"
        actions={
          <Segmented
            size="small"
            value={statusFilter}
            onChange={(v) => setStatusFilter(v as StatusFilter)}
            options={STATUS_OPTIONS}
          />
        }
      >
        <RequestState
          loading={meQuery.isLoading || mineQuery.isLoading}
          error={
            meQuery.isError
              ? meQuery.error
              : mineQuery.isError
                ? mineQuery.error
                : undefined
          }
          errorTitle="Couldn't load your advances"
          empty={!mineQuery.isLoading && myRows.length === 0}
          emptyFallback={
            <EmptyState
              icon={Banknote}
              title="No advances yet"
              description="Need a lump sum against future salary? Submit a request and HR will review it."
              action={
                <Button
                  type="primary"
                  icon={<Plus size={14} />}
                  onClick={() => setOpenApply(true)}
                >
                  Request advance
                </Button>
              }
            />
          }
        >
          <PermanentAdvanceTable data={myRows} loading={mineQuery.isLoading} />
        </RequestState>
      </Section>

      <ApplyPermanentAdvanceDrawer
        open={openApply}
        onClose={() => setOpenApply(false)}
      />
    </div>
  );
}
