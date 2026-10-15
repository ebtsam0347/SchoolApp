// /payroll/advance-ledger — one employee's long-term advance ledger.
//
// Pick an employee, then read their account the way the backend balances
// it: every APPROVED permanent advance is a debit, every deduction (salary,
// bonus, annual leave, settlement, manual) is a credit, and the balance is
// approved − deducted. Accounts (`payroll.advance.accounts`) can record a
// manual deduction from here.
//
// No dedicated backend endpoint: advances come from
// GET /permanent-advances/employees/{id}?status=APPROVED and the deductions
// from GET /permanent-advances/{anyApprovedId}/deductions, which returns the
// whole employee ledger whichever approved advance it's keyed on.

import { useQuery } from '@tanstack/react-query';
import { createFileRoute, redirect } from '@tanstack/react-router';
import { Button, Table, Typography } from 'antd';
import { BookOpen, CheckCircle2, MinusCircle, Plus, Wallet } from 'lucide-react';
import { useContext, useState } from 'react';

import { AbilityContext, buildAbility } from '@/auth';
import {
  EmptyState,
  KpiGrid,
  MoneyCell,
  PageHeader,
  RequestState,
  Section,
  StatCard,
} from '@/components';
import { readSnapshot } from '@/lib';
import {
  formatDateShort,
  PERMANENT_ADVANCE_DEDUCTION_SOURCE_LABEL,
  permanentAdvancesApi,
  type PermanentAdvanceDeductionDto,
  type PermanentAdvanceDto,
} from '@/payroll';
import { RecordPermanentAdvanceDeductionModal } from '@/payroll/advances';
import { EmployeePicker } from '@/people';

const { Text } = Typography;

export const Route = createFileRoute('/_app/payroll/advance-ledger')({
  // Both reads behind this page require `payroll.read`.
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    if (!buildAbility(snap.user).can('read', 'payroll')) {
      throw redirect({ to: '/' });
    }
  },
  component: AdvanceLedgerPage,
});

type LedgerLine = {
  key: string;
  date: string;
  particulars: string;
  reference: string | null;
  debit: number | null;
  credit: number | null;
  balance: number;
};

/** The date an approved advance hits the ledger — when the cash went out. */
function advanceDate(a: PermanentAdvanceDto): string {
  return a.receivingDate ?? a.disbursedAt ?? a.accountsDecidedAt ?? a.createdAt;
}

/**
 * Advances (debit) and deductions (credit) merged oldest-first with a running
 * balance. On a same-day tie the advance goes first, so the balance never
 * dips below zero on a day the cash went out and came back.
 */
function buildLedger(
  advances: PermanentAdvanceDto[],
  deductions: PermanentAdvanceDeductionDto[],
): LedgerLine[] {
  const entries = [
    ...advances.map((a) => ({
      sortDate: advanceDate(a).slice(0, 10),
      order: 0,
      id: a.id,
      line: {
        key: `a-${a.id}`,
        date: advanceDate(a),
        particulars: 'Advance approved',
        reference: a.requestCode ?? `#${a.id}`,
        debit: a.amount,
        credit: null,
      },
    })),
    ...deductions.map((d) => ({
      sortDate: d.transactionDate.slice(0, 10),
      order: 1,
      id: d.id,
      line: {
        key: `d-${d.id}`,
        date: d.transactionDate,
        particulars:
          d.description ||
          `${PERMANENT_ADVANCE_DEDUCTION_SOURCE_LABEL[d.source]} deduction`,
        reference: PERMANENT_ADVANCE_DEDUCTION_SOURCE_LABEL[d.source],
        debit: null,
        credit: d.deductedAmount,
      },
    })),
  ].sort(
    (x, y) =>
      x.sortDate.localeCompare(y.sortDate) || x.order - y.order || x.id - y.id,
  );

  let balance = 0;
  return entries.map(({ line }) => {
    balance += (line.debit ?? 0) - (line.credit ?? 0);
    return { ...line, balance };
  });
}

const rupees = (n: number) => `Rs ${n.toLocaleString('en-PK')}`;

function AdvanceLedgerPage() {
  const ability = useContext(AbilityContext);
  const canRecord = ability.can('accounts', 'payroll.advance');
  const [employeeId, setEmployeeId] = useState<number | undefined>(undefined);
  const [recording, setRecording] = useState(false);

  const advancesQuery = useQuery({
    queryKey: ['payroll', 'permanent-advances', 'ledger', employeeId, 'approved'],
    queryFn: ({ signal }) =>
      permanentAdvancesApi.listForEmployee(
        employeeId!,
        { status: 'APPROVED', size: 100 },
        signal,
      ),
    enabled: employeeId != null,
  });
  const approved = advancesQuery.data?.content ?? [];
  // Any approved advance keys the employee-wide ledger and the record call.
  const anchor = approved[0] ?? null;

  const deductionsQuery = useQuery({
    queryKey: ['payroll', 'permanent-advances', 'deductions', anchor?.id],
    queryFn: ({ signal }) => permanentAdvancesApi.deductions(anchor?.id ?? 0, signal),
    enabled: anchor != null,
  });
  const deductions = deductionsQuery.data ?? [];

  const totalApproved = approved.reduce((s, a) => s + a.amount, 0);
  const totalDeducted = deductions.reduce((s, d) => s + d.deductedAmount, 0);
  const remaining = Math.max(0, totalApproved - totalDeducted);

  const ledger = buildLedger(approved, deductions);

  const loading =
    advancesQuery.isLoading || (anchor != null && deductionsQuery.isLoading);
  const error = advancesQuery.isError
    ? advancesQuery.error
    : deductionsQuery.isError
      ? deductionsQuery.error
      : undefined;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, width: '100%' }}>
      <PageHeader
        eyebrow="Money"
        title="Advance recovery"
        description="An employee's long-term advance account: every approved advance against every recovery — salary, bonus, annual leave, settlement and manual."
        actions={
          canRecord && anchor ? (
            <Button
              type="primary"
              icon={<Plus size={14} />}
              onClick={() => setRecording(true)}
              disabled={remaining <= 0}
            >
              Manual deduction
            </Button>
          ) : undefined
        }
      />

      <Section title="Employee">
        <EmployeePicker
          value={employeeId}
          onChange={setEmployeeId}
          style={{ width: '100%', maxWidth: 420 }}
        />
      </Section>

      {employeeId == null ? (
        <EmptyState
          icon={BookOpen}
          title="Pick an employee"
          description="Their approved advances and every deduction against them show up here as a ledger."
        />
      ) : (
        <RequestState
          loading={loading}
          error={error}
          errorTitle="Couldn't load the ledger"
          empty={!loading && approved.length === 0}
          emptyFallback={
            <EmptyState
              icon={Wallet}
              title="No approved advances"
              description="This employee has no approved long-term advance, so there's nothing to deduct against."
            />
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
            <KpiGrid minColumnWidth={200}>
              <StatCard
                variant="plain"
                tone="info"
                icon={CheckCircle2}
                label="Total approved"
                value={rupees(totalApproved)}
                delta={`${approved.length} ${approved.length === 1 ? 'advance' : 'advances'}`}
              />
              <StatCard
                variant="plain"
                tone="success"
                icon={MinusCircle}
                label="Total deducted"
                value={rupees(totalDeducted)}
                delta={`${deductions.length} ${deductions.length === 1 ? 'entry' : 'entries'}`}
              />
              <StatCard
                variant="accent-radial"
                tone={remaining > 0 ? 'warning' : 'neutral'}
                icon={Wallet}
                accentCorner="tr"
                label="Remaining balance"
                value={rupees(remaining)}
                delta={remaining > 0 ? 'still to recover' : 'fully recovered'}
              />
            </KpiGrid>

            <Section title="Ledger" subtitle="Oldest first. Debit = advance paid out, credit = amount recovered.">
              <Table<LedgerLine>
                rowKey="key"
                size="small"
                dataSource={ledger}
                pagination={ledger.length > 20 ? { pageSize: 20 } : false}
                scroll={{ x: 'max-content' }}
                columns={[
                  {
                    title: 'Date',
                    dataIndex: 'date',
                    render: (v: string) => formatDateShort(v),
                  },
                  {
                    title: 'Particulars',
                    dataIndex: 'particulars',
                    render: (v: string) => <Text style={{ fontSize: 13 }}>{v}</Text>,
                  },
                  {
                    title: 'Ref',
                    dataIndex: 'reference',
                    render: (v: string | null) =>
                      v ? <Text type="secondary" style={{ fontSize: 12 }}>{v}</Text> : '—',
                  },
                  {
                    title: 'Debit',
                    dataIndex: 'debit',
                    align: 'right',
                    render: (v: number | null) => (v == null ? '' : <MoneyCell amount={v} />),
                  },
                  {
                    title: 'Credit',
                    dataIndex: 'credit',
                    align: 'right',
                    render: (v: number | null) => (v == null ? '' : <MoneyCell amount={v} />),
                  },
                  {
                    title: 'Balance',
                    dataIndex: 'balance',
                    align: 'right',
                    render: (v: number) => (
                      <span style={{ fontWeight: 500 }}>
                        <MoneyCell amount={v} />
                      </span>
                    ),
                  },
                ]}
                summary={() => (
                  <Table.Summary.Row>
                    <Table.Summary.Cell index={0} colSpan={3}>
                      <Text strong>Total</Text>
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={3} align="right">
                      <Text strong><MoneyCell amount={totalApproved} /></Text>
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={4} align="right">
                      <Text strong><MoneyCell amount={totalDeducted} /></Text>
                    </Table.Summary.Cell>
                    <Table.Summary.Cell index={5} align="right">
                      <Text strong><MoneyCell amount={remaining} /></Text>
                    </Table.Summary.Cell>
                  </Table.Summary.Row>
                )}
              />
            </Section>
          </div>
        </RequestState>
      )}

      <RecordPermanentAdvanceDeductionModal
        row={recording ? anchor : null}
        title="Manual deduction"
        onClose={() => setRecording(false)}
      />
    </div>
  );
}
