// /payroll/short-term-advances — bulk one-month advance batches.
//
// HR creates a batch (N employees, one target month, per-employee amount),
// Manager, HR, then Accounts approve. Each approved line is deducted in
// full from the target month's payroll run. This page is the
// list + create surface; per-batch detail lives behind a future route.

import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { Alert, Button, Segmented, Space } from 'antd';
import { Banknote, Boxes, CheckCircle2, Plus, Sparkles } from 'lucide-react';
import { useContext, useState } from 'react';

import { AbilityContext, useAuth } from '@/auth';
import { isForbidden } from '@/lib';
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
  shortTermAdvancesApi,
  type AdvanceStatus,
} from '@/payroll';
import {
  ApplyShortTermAdvanceDrawer,
  BulkApplyShortTermAdvanceDrawer,
  ShortTermAdvanceBatchDetailDrawer,
  ShortTermAdvanceBatchTable,
} from '@/payroll/advances';
import type { ShortTermAdvanceBatchDto } from '@/payroll';
import { employeesApi } from '@/people/api';

export const Route = createFileRoute('/_app/payroll/short-term-advances')({
  component: ShortTermAdvancesPage,
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

function ShortTermAdvancesPage() {
  const auth = useAuth();
  const ability = useContext(AbilityContext);
  const canViewAll = ability.can('read', 'payroll');

  // Raising a bulk advance is tied to the MANAGER role and nothing else — a
  // manager raises short-term advances for their own subordinates, so the
  // capability follows the role wherever it's granted and consults no payroll
  // permission. The drawer scopes the roster to match: direct reports for a
  // manager, the whole department for payroll / HR (see `scope` below).
  //
  // Role codes come back bare or ROLE_-prefixed depending on the issuer, so
  // normalise the same way the topbar's primaryRoleLabel does.
  const isManager =
    auth.user?.roles.some(
      (r) => r.replace(/^ROLE_/, '').toUpperCase() === 'MANAGER',
    ) ?? false;
  const canBulk = isManager;

  // Reading the batch list is a separate question from raising one. Payroll /
  // HR read every batch via `payroll.read`; a manager reaches the same list
  // only if the backend allows it, and falls back gracefully when it 403s.
  const canSeeList = canViewAll || canBulk;

  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [openBulk, setOpenBulk] = useState(false);
  const [openApply, setOpenApply] = useState(false);
  const [detailRow, setDetailRow] = useState<ShortTermAdvanceBatchDto | null>(null);

  const meQuery = useQuery({
    queryKey: ['employees', 'me'],
    queryFn: ({ signal }) => employeesApi.me(signal),
    enabled: auth.user?.employeeLinked === true,
    staleTime: 5 * 60_000,
    retry: false,
  });
  const myEmployeeId = meQuery.data?.id ?? null;

  const mineQuery = useQuery({
    queryKey: ['payroll', 'short-term-advance-batches', 'mine', myEmployeeId],
    queryFn: ({ signal }) =>
      shortTermAdvancesApi.listForEmployee(myEmployeeId!, {}, signal),
    enabled: myEmployeeId != null,
  });
  const myRows = mineQuery.data?.content ?? [];

  const listQuery = useQuery({
    queryKey: ['payroll', 'short-term-advance-batches', statusFilter],
    queryFn: ({ signal }) =>
      shortTermAdvancesApi.list(
        {
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          size: 100,
        },
        signal,
      ),
    enabled: canSeeList,
  });

  // `GET /short-term-advance-batches` requires `payroll.read`. A manager who
  // can raise a batch but can't list them gets a 403 here — that's an
  // expected outcome, not a failure worth a red error card, so fall back to
  // the self-serve view instead.
  const listForbidden = isForbidden(listQuery.error);
  const showBatches = canSeeList && !listForbidden;

  const rows = listQuery.data?.content ?? [];
  const counts = {
    pending: rows.filter(
      (r) =>
        r.status === 'PENDING' || r.status === 'MANAGER_APPROVED' || r.status === 'HR_APPROVED',
    ).length,
    approved: rows.filter(
      (r) => r.status === 'APPROVED',
    ).length,
    employees: rows.reduce((s, r) => s + r.totalEmployees, 0),
  };

  const pendingTotal = rows
    .filter((r) => r.status !== 'REJECTED' && r.status !== 'CANCELLED')
    .reduce((s, r) => s + r.totalAmount, 0);

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
        title={canSeeList ? 'Short-term advances' : 'Request an advance'}
        description={
          canSeeList
            ? 'Bulk advances paid out of the next payroll run. One batch, one target month — every employee on the batch has the full amount deducted from that month.'
            : 'A one-off advance deducted in full from a single payroll month. HR reviews it, then Accounts approves it.'
        }
        actions={
          <Space>
            <Button
              icon={<Plus size={14} />}
              onClick={() => setOpenApply(true)}
            >
              Request advance
            </Button>
            {canBulk ? (
              <Button
                type="primary"
                icon={<Plus size={14} />}
                onClick={() => setOpenBulk(true)}
              >
                New batch
              </Button>
            ) : null}
          </Space>
        }
      />

      {showBatches ? (
        <>
          <KpiGrid minColumnWidth={200}>
            <StatCard
              variant="accent-radial"
              tone="warning"
              icon={Sparkles}
              accentCorner="tr"
              label="In review"
              value={`${counts.pending}`}
              delta="batches awaiting approval"
            />
            <StatCard
              variant="plain"
              tone="success"
              icon={CheckCircle2}
              label="Cleared"
              value={`${counts.approved}`}
              delta="approved"
            />
            <StatCard
              variant="plain"
              icon={Boxes}
              tone="info"
              label="Employees covered"
              value={`${counts.employees}`}
            />
            <StatCard
              variant="plain"
              icon={Banknote}
              tone={pendingTotal > 0 ? 'info' : 'neutral'}
              label="Outstanding total"
              value={
                pendingTotal > 0
                  ? `Rs ${pendingTotal.toLocaleString('en-PK')}`
                  : 'Rs 0'
              }
            />
          </KpiGrid>

          <Section
            title="Batches"
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
              loading={listQuery.isLoading}
              error={listQuery.isError ? listQuery.error : undefined}
              errorTitle="Couldn't load short-term advance batches"
              empty={!listQuery.isLoading && rows.length === 0}
              emptyFallback={
                <EmptyState
                  icon={Boxes}
                  title="No batches yet"
                  description="Group a one-off advance for several employees and deduct it from a single payroll month."
                  action={
                    <Button
                      type="primary"
                      icon={<Plus size={14} />}
                      onClick={() => setOpenBulk(true)}
                    >
                      New batch
                    </Button>
                  }
                />
              }
            >
              <ShortTermAdvanceBatchTable
                data={rows}
                loading={listQuery.isLoading}
                onRowClick={setDetailRow}
              />
            </RequestState>
          </Section>
        </>
      ) : (
        <Section title="My requests">
          {canBulk && listForbidden ? (
            <Alert
              type="info"
              showIcon
              title="Batches you raise go to HR for review"
              description="You can start a batch for your department, but you don't have access to the full batch list — submitted batches won't appear below. Only advances where you're one of the listed employees show here."
              style={{ marginBottom: 16 }}
            />
          ) : null}
          <RequestState
            loading={meQuery.isLoading || mineQuery.isLoading}
            error={
              meQuery.isError
                ? meQuery.error
                : mineQuery.isError
                  ? mineQuery.error
                  : undefined
            }
            errorTitle="Couldn't load your advance requests"
            empty={!mineQuery.isLoading && myRows.length === 0}
            emptyFallback={
              <EmptyState
                icon={Boxes}
                title="Request a short-term advance"
                description="Submit a request for a single payroll month and HR will review it, then Accounts approves it."
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
            <ShortTermAdvanceBatchTable
              data={myRows}
              loading={mineQuery.isLoading}
              onRowClick={setDetailRow}
            />
          </RequestState>
        </Section>
      )}

      {canBulk ? (
        <BulkApplyShortTermAdvanceDrawer
          open={openBulk}
          onClose={() => setOpenBulk(false)}
          // Payroll / HR raise department-wide batches. A manager without
          // `payroll.read` is limited to their own direct reports.
          scope={canViewAll ? 'department' : 'directReports'}
        />
      ) : null}

      <ApplyShortTermAdvanceDrawer
        open={openApply}
        onClose={() => setOpenApply(false)}
      />

      <ShortTermAdvanceBatchDetailDrawer
        row={detailRow}
        onClose={() => setDetailRow(null)}
        selfServe={!showBatches}
        employee={meQuery.data}
      />
    </div>
  );
}
