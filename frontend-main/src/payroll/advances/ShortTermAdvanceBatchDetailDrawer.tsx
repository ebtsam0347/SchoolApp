// Read-only detail drawer that opens when a manager clicks "Details" (or a
// row) on a short-term advance batch — mirrors PermanentAdvanceDetailDrawer /
// RequisitionDetailDrawer's pattern.
//
// The list query returns the full ShortTermAdvanceBatchDto minus `lines`
// (the list endpoint omits it), so this drawer loads them with one
// `GET /short-term-advance-batches/{id}/lines` call. Each line carries its
// `employee` summary inlined server-side, so the caller needs neither
// `payroll.read` (the old `/{id}` re-fetch) nor `employee.read` (a per-line
// `GET /employees/{id}`) — the latter also drives the sidebar, so managers
// must not be granted it just to see names here.

import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { ReactNode } from 'react';

import { ApprovalSteps } from '@/attendance/inbox';
import type { ApprovalStepDto, ApprovalStepStatus } from '@/attendance';
import {
  DetailDrawer,
  KvList,
  MoneyCell,
  PersonCell,
  Section,
  StatusPill,
} from '@/components';
import type { EmployeeDto } from '@/people/types';
import { getErrorMessage } from '@/lib';
import { shortTermAdvancesApi } from '../api';
import {
  ADVANCE_STATUS_LABEL,
  ADVANCE_STATUS_TONE,
  advanceStageLabel,
  formatDateShort,
  formatPeriod,
} from '../format';
import type { ShortTermAdvanceBatchDto, ShortTermAdvanceLineDto } from '../types';
import { ShortTermAdvanceBatchRowActions } from './ShortTermAdvanceBatchRowActions';

const { Text } = Typography;

export type ShortTermAdvanceBatchDetailDrawerProps = {
  row: ShortTermAdvanceBatchDto | null;
  onClose: () => void;
  /** Self-serve view (the employee looking at their own request): the row
   *  already comes from `listForEmployee`, which includes `lines` rescoped
   *  to just the caller — skip the `/lines` fetch (it would return every
   *  batch-mate) and hide the HR/Accounts approve actions, which the caller
   *  can't use on their own request anyway. */
  selfServe?: boolean;
  /** The caller's own employee record — self-serve fallback for the name
   *  when a line comes back without its `employee` summary. */
  employee?: EmployeeDto | null;
};

/**
 * Synthesizes a three-stage Manager -> HR -> Accounts chain from the
 * batch's own decision fields — same shape as PermanentAdvanceDetailDrawer's
 * helper, since both advance types share the same fixed chain. There's no
 * `managerDecidedBy` / `managerDecidedAt` field on this DTO (only HR and
 * Accounts get one), so the Manager step's status is inferred from
 * `status`/`hrDecided` rather than read directly: once the batch has moved
 * past PENDING (or HR/Accounts has already decided), the Manager step must
 * have been APPROVED — it's the only way later stages get reached.
 */
function shortTermAdvanceSteps(row: ShortTermAdvanceBatchDto): ApprovalStepDto[] {
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

export function ShortTermAdvanceBatchDetailDrawer({
  row,
  onClose,
  selfServe,
  employee,
}: ShortTermAdvanceBatchDetailDrawerProps) {
  const open = row != null;

  // The list row doesn't include per-employee lines — load them (with each
  // employee's summary inlined) whenever the drawer opens. Skipped in
  // self-serve mode: `listForEmployee` already hands back the caller's own
  // line inline.
  const linesQuery = useQuery({
    queryKey: ['payroll', 'short-term-advance-batches', row?.id, 'lines'],
    queryFn: ({ signal }) => shortTermAdvancesApi.lines(row?.id ?? 0, signal),
    enabled: open && !selfServe,
  });
  const lines: ShortTermAdvanceLineDto[] = (selfServe ? row?.lines : linesQuery.data) ?? [];

  const employeeLineColumns: ColumnsType<ShortTermAdvanceLineDto> = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_v, line) => {
        const e = line.employee;
        // Self-serve: every line is the caller's own — fall back to their
        // record if the summary didn't resolve.
        const self = selfServe ? employee : null;
        return (
          <PersonCell
            size="compact"
            person={{
              fullName: e?.fullName ?? self?.fullName ?? `#${line.employeeId}`,
              employeeCode: e?.employeeCode ?? self?.employeeCode,
              profilePictureUrl: e?.profilePictureUrl ?? self?.profilePictureUrl,
              designation: e?.designationName,
            }}
          />
        );
      },
    },
    {
      title: 'Department',
      key: 'department',
      width: 180,
      render: (_v, line) =>
        line.employee?.departmentName ? (
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
            <Text>{line.employee.departmentName}</Text>
            {line.employee.sectionName ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {line.employee.sectionName}
              </Text>
            ) : null}
          </div>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: 'Amount',
      key: 'amount',
      width: 120,
      align: 'right',
      render: (_v, line) => <MoneyCell amount={line.amount} />,
    },
  ];

  return (
    <DetailDrawer
      title={row ? `Advance batch ${row.requestCode ?? `#${row.id}`}` : 'Advance batch'}
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
        row && !selfServe ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <ShortTermAdvanceBatchRowActions row={row} />
          </div>
        ) : undefined
      }
    >
      {row ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          <KvList
            items={[
              { label: 'Deduct in', value: formatPeriod(row.targetYear, row.targetMonth) },
              { label: 'Employees', value: row.totalEmployees },
              { label: 'Total', value: <MoneyCell amount={row.totalAmount} /> },
              { label: 'Stage', value: advanceStageLabel(row) ?? '—' },
              { label: 'Submitted', value: formatDateShort(row.createdAt) },
            ]}
            minColumnWidth={140}
            gap={14}
          />

          <Section title="Employees" level={5}>
            {!selfServe && linesQuery.isError ? (
              <Alert
                type="error"
                showIcon
                style={{ marginBottom: 12 }}
                title="Couldn't load employees"
                description={getErrorMessage(linesQuery.error)}
                action={
                  <Button size="small" onClick={() => void linesQuery.refetch()}>
                    Retry
                  </Button>
                }
              />
            ) : null}
            <Table<ShortTermAdvanceLineDto>
              rowKey="id"
              size="small"
              loading={!selfServe && linesQuery.isLoading}
              dataSource={lines}
              columns={employeeLineColumns}
              pagination={false}
              // Scroll wide columns inside the table, not the page.
              scroll={{ x: 'max-content' }}
              locale={{ emptyText: 'No lines on this batch.' }}
            />
          </Section>

          {row.notes ? <Fact label="Notes">{row.notes}</Fact> : null}
          {row.hrNote ? <Fact label="HR note">{row.hrNote}</Fact> : null}
          {row.accountsNote ? <Fact label="Accounts note">{row.accountsNote}</Fact> : null}
          {row.status === 'REJECTED' && row.rejectionReason ? (
            <Fact label="Rejection reason">{row.rejectionReason}</Fact>
          ) : null}

          <Section title="Approval chain" level={5}>
            <ApprovalSteps steps={shortTermAdvanceSteps(row)} />
          </Section>
        </div>
      ) : null}
    </DetailDrawer>
  );
}
