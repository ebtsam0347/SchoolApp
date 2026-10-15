// Read-only detail drawer that opens when a manager clicks "Details" on a
// pending permanent (long-term) advance row — mirrors RequisitionDetailDrawer's
// pattern (facts + status up top, row-scoped actions pinned to the footer).
//
// No extra fetch: the list query already returns the full PermanentAdvanceDto,
// so this takes the row directly instead of re-querying by id.

import { Typography } from 'antd';
import type { ReactNode } from 'react';

import { ApprovalSteps } from '@/attendance/inbox';
import type { ApprovalStepDto, ApprovalStepStatus } from '@/attendance';
import { DetailDrawer, KvList, MoneyCell, Section, StatusPill } from '@/components';
import { ADVANCE_STATUS_LABEL, ADVANCE_STATUS_TONE, advanceStageLabel, formatDateShort } from '../format';
import type { PermanentAdvanceDto } from '../types';
import { PermanentAdvanceRowActions } from './PermanentAdvanceRowActions';

const { Text } = Typography;

export type PermanentAdvanceDetailDrawerProps = {
  row: PermanentAdvanceDto | null;
  onClose: () => void;
};

/**
 * Synthesizes a three-stage Manager -> HR -> Accounts chain from the
 * advance's own decision fields — there's no `steps[]` array on this DTO
 * like leave/overtime/regularization have. There's also no
 * `managerDecidedBy` / `managerDecidedAt` field (only HR and Accounts get
 * one), so the Manager step's status is inferred from `status`/`hrDecided`
 * rather than read directly: once the advance has moved past PENDING (or
 * HR/Accounts has already decided), the Manager step must have been
 * APPROVED — it's the only way later stages get reached.
 */
function permanentAdvanceSteps(row: PermanentAdvanceDto): ApprovalStepDto[] {
  const hrDecided = row.hrDecidedAt != null;
  const acctsDecided = row.accountsDecidedAt != null;
  const managerStatus: ApprovalStepStatus =
    row.status === 'PENDING'
      ? 'PENDING'
      : row.status === 'REJECTED' && !hrDecided && !acctsDecided
        ? 'REJECTED'
        : 'APPROVED';
  const hrStatus: ApprovalStepStatus = hrDecided
    ? row.status === 'REJECTED' && !acctsDecided
      ? 'REJECTED'
      : 'APPROVED'
    : row.status === 'MANAGER_APPROVED'
      ? 'PENDING'
      : 'NOT_YET';
  const acctsStatus: ApprovalStepStatus = acctsDecided
    ? row.status === 'REJECTED'
      ? 'REJECTED'
      : 'APPROVED'
    : row.status === 'HR_APPROVED'
      ? 'PENDING'
      : 'NOT_YET';

  return [
    {
      stageNo: 1,
      stageRole: 'Manager',
      assignedTo: row.currentStage?.stageNo === 1 ? row.currentStage.assignedTo : null,
      assignedToName: row.currentStage?.stageNo === 1 ? row.currentStage.assignedToName : null,
      status: managerStatus,
      decidedBy: null,
      decidedByName: null,
      decidedAt: null,
      note: managerStatus === 'REJECTED' ? row.rejectionReason : null,
    },
    {
      stageNo: 2,
      stageRole: 'HR',
      assignedTo: row.hrDecidedBy,
      assignedToName: null,
      status: hrStatus,
      decidedBy: row.hrDecidedBy,
      decidedByName: null,
      decidedAt: row.hrDecidedAt,
      note: hrStatus === 'REJECTED' ? row.rejectionReason : row.hrNote,
    },
    {
      stageNo: 3,
      stageRole: 'Accounts',
      assignedTo: row.accountsDecidedBy,
      assignedToName: null,
      status: acctsStatus,
      decidedBy: row.accountsDecidedBy,
      decidedByName: null,
      decidedAt: row.accountsDecidedAt,
      note: acctsStatus === 'REJECTED' ? row.rejectionReason : row.accountsNote,
    },
  ];
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <Text
        type="secondary"
        style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em' }}
      >
        {label}
      </Text>
      <div style={{ fontSize: 13, marginTop: 4 }}>{children}</div>
    </div>
  );
}

export function PermanentAdvanceDetailDrawer({
  row,
  onClose,
}: PermanentAdvanceDetailDrawerProps) {
  const open = row != null;

  return (
    <DetailDrawer
      title={row ? `Advance ${row.requestCode ?? `#${row.id}`}` : 'Advance'}
      open={open}
      onClose={onClose}
      extra={
        row ? (
          <StatusPill tone={ADVANCE_STATUS_TONE[row.status]} size="md">
            {ADVANCE_STATUS_LABEL[row.status]}
          </StatusPill>
        ) : undefined
      }
      footer={
        row ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <PermanentAdvanceRowActions row={row} />
          </div>
        ) : undefined
      }
    >
      {row ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          <KvList
            items={[
              { label: 'Amount', value: <MoneyCell amount={row.amount} /> },
              { label: 'Monthly deduction', value: <MoneyCell amount={row.monthlyDeduction} /> },
              {
                label: 'Remaining',
                value:
                  row.remainingBalance == null ? '—' : <MoneyCell amount={row.remainingBalance} />,
              },
              // Bonus / AL legs only when the request actually has them —
              // most advances are plain monthly-installment recoveries.
              ...(row.fiscalYearCode
                ? [{ label: 'Financial period', value: row.fiscalYearCode }]
                : []),
              ...(row.bonusOffset
                ? [
                    {
                      label: 'Bonus offset 1',
                      value: <MoneyCell amount={row.bonusOffset} />,
                    },
                  ]
                : []),
              ...(row.bonusOffset2
                ? [
                    {
                      label: 'Bonus offset 2',
                      value: <MoneyCell amount={row.bonusOffset2} />,
                    },
                  ]
                : []),
              ...(row.annualLeaveOffset
                ? [
                    {
                      label: row.annualLeaveMonth
                        ? `Annual leave deduction (${formatDateShort(row.annualLeaveMonth)})`
                        : 'Annual leave deduction',
                      value: <MoneyCell amount={row.annualLeaveOffset} />,
                    },
                  ]
                : []),
              { label: 'Stage', value: advanceStageLabel(row) ?? '—' },
              {
                label: 'Receiving date',
                value: row.receivingDate ? formatDateShort(row.receivingDate) : '—',
              },
              { label: 'Requested', value: formatDateShort(row.createdAt) },
            ]}
            minColumnWidth={140}
            gap={14}
          />

          {row.description ? <Fact label="Description">{row.description}</Fact> : null}
          {row.hrNote ? <Fact label="HR note">{row.hrNote}</Fact> : null}
          {row.accountsNote ? <Fact label="Accounts note">{row.accountsNote}</Fact> : null}
          {row.status === 'REJECTED' && row.rejectionReason ? (
            <Fact label="Rejection reason">{row.rejectionReason}</Fact>
          ) : null}

          <Section title="Approval chain" level={5}>
            <ApprovalSteps steps={permanentAdvanceSteps(row)} />
          </Section>
        </div>
      ) : null}
    </DetailDrawer>
  );
}
