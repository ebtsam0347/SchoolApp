// Shared payslip detail drawer.
//
// Renders one payslip as a readable salary slip: who it belongs to and
// where the net lands, the attendance basis for the period, earnings and
// deductions side by side (each with its own total), a gross → deductions
// → net recap, and the leave position for the period.
//
// Two data sources, normalized to one `PayslipView` in ./payslip-view:
//   • the run-detail page hands the drawer the row it already has from
//     `GET /payroll/runs/{id}/detail` — the rich payload carrying identity,
//     bank, and leaves;
//   • every other surface (ESS /payroll/payslips, manager /payroll/team,
//     the disbursement queue) passes just an id and the drawer falls back
//     to `payslipsApi.get(id)`, whose flat DTO has no employee / bank /
//     leave blocks — those sections are simply omitted there.
//
// `payslipsApi.pdfUrl(id)` backs the signed download link. The same access
// rules apply at the API: HR / ACCOUNTS / owner / manager-of-report pass;
// everyone else gets 404.

import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Button,
  Drawer,
  Space,
  Typography,
  theme as antdTheme,
} from 'antd';
import { Download, Receipt } from 'lucide-react';
import type { ReactNode } from 'react';

import { MoneyCell, PersonCell, StatusPill } from '@/components';
import { getErrorMessage, isApiError, useIsDesktop } from '@/lib';
import { payslipsApi } from './api';
import {
  PAYSLIP_STATUS_LABEL,
  PAYSLIP_STATUS_TONE,
  formatDateShort,
  formatPeriodLong,
} from './format';
import {
  payslipViewFromDto,
  payslipViewFromRunRow,
  splitLeaves,
  type PayslipView,
  type PayslipViewLine,
} from './payslip-view';
import type { PayslipWithEmployee } from './composites';

const { Text } = Typography;
const { useToken } = antdTheme;

export interface PayslipDetailDrawerProps {
  open: boolean;
  id: number | null;
  onClose: () => void;
  /**
   * Run-detail composite row for this payslip, when the caller already has
   * it. Supplying it skips the `GET /payslips/{id}` round-trip *and* is the
   * only way to get the identity / bank / leave sections, which that
   * endpoint doesn't return.
   */
  row?: PayslipWithEmployee | null;
}

export function PayslipDetailDrawer({
  open,
  id,
  onClose,
  row,
}: PayslipDetailDrawerProps) {
  const isDesktop = useIsDesktop();
  const { message } = AntdApp.useApp();

  const detailQuery = useQuery({
    queryKey: ['payroll', 'payslip', id],
    queryFn: ({ signal }) => payslipsApi.get(id!, signal),
    // The caller-supplied row already carries strictly more than the
    // endpoint would return, so don't fetch over it.
    enabled: open && id != null && !row,
  });

  const view: PayslipView | undefined = row
    ? payslipViewFromRunRow(row)
    : detailQuery.data
      ? payslipViewFromDto(detailQuery.data)
      : undefined;

  async function downloadPdf() {
    if (!id) return;
    try {
      const { url } = await payslipsApi.pdfUrl(id);
      window.open(url, '_blank', 'noopener');
    } catch (e) {
      message.error(getErrorMessage(e));
    }
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      destroyOnHidden
      // Two money columns side by side need room; phones fall back to the
      // full viewport and the columns stack (see LineColumns).
      size={isDesktop ? 'min(880px, 100vw)' : '100%'}
      title={
        view ? (
          <Space size={10}>
            <Receipt size={16} strokeWidth={1.75} />
            {formatPeriodLong(view.periodYear, view.periodMonth)} payslip
            <StatusPill tone={PAYSLIP_STATUS_TONE[view.status]}>
              {PAYSLIP_STATUS_LABEL[view.status]}
            </StatusPill>
          </Space>
        ) : (
          'Payslip'
        )
      }
      extra={
        view?.pdfAvailable ? (
          <Button
            type="primary"
            icon={<Download size={14} strokeWidth={1.75} />}
            onClick={downloadPdf}
          >
            Download PDF
          </Button>
        ) : null
      }
    >
      {detailQuery.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load payslip"
          description={
            isApiError(detailQuery.error)
              ? (detailQuery.error.problem.detail ?? detailQuery.error.message)
              : 'Try refreshing in a moment.'
          }
        />
      ) : !view ? (
        <Text type="secondary">Loading…</Text>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          {view.status === 'HELD' && (
            <Alert
              type="warning"
              showIcon
              title="This payslip is held"
              description={
                view.heldReason
                  ? `Reason: ${view.heldReason}`
                  : 'HR has paused this payslip.'
              }
            />
          )}
          {view.status === 'RELEASE_PENDING' && (
            <Alert
              type="info"
              showIcon
              title="Release queued"
              description="The held amount will appear on the next payslip as a separate line."
            />
          )}

          <IdentityCard view={view} />
          <AttendanceGrid view={view} />

          <LineColumns
            earnings={view.earnings}
            deductions={view.deductions}
            grossSalary={view.grossSalary}
            totalDeduction={view.totalDeduction}
          />

          <NetRecap view={view} />
          <LeaveSection view={view} />

          {view.pessiEmployer > 0 && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              Plus PESSI employer contribution of{' '}
              <MoneyCell amount={view.pessiEmployer} /> — booked against
              employer cost, not deducted from net.
            </Text>
          )}
        </div>
      )}
    </Drawer>
  );
}

/* ---------------------------- Identity ------------------------------- */

/** Who the slip belongs to and which account the net lands in. Collapses to
 *  a plain employee-code line on the flat-DTO path. */
function IdentityCard({ view }: { view: PayslipView }) {
  const { token } = useToken();
  const e = view.employee;

  const facts: { label: string; value: ReactNode }[] = [
    { label: 'Employee code', value: e?.employeeCode ?? view.employeeCode ?? '—' },
  ];
  // Only the run-detail payload carries these. On the flat-DTO path they'd
  // be a row of em dashes, so leave them out rather than render blanks.
  if (e) {
    facts.push(
      { label: 'Father name', value: e.fatherName || '—' },
      { label: 'Department', value: e.department || '—' },
      { label: 'Grade', value: e.grade || '—' },
      {
        label: 'Date of joining',
        value: e.dateOfJoining ? formatDateShort(e.dateOfJoining) : '—',
      },
    );
  }
  facts.push({ label: 'Fiscal year', value: view.fiscalYearCode || '—' });
  if (view.bank) {
    facts.push(
      {
        label: 'Bank',
        value: [view.bank.bankName, view.bank.branch].filter(Boolean).join(' · ') || '—',
      },
      { label: 'Account no.', value: view.bank.accountNumber || '—' },
    );
  }
  facts.push({
    label: 'Generated',
    value: view.generatedAt ? formatDateShort(view.generatedAt) : '—',
  });
  if (view.permanentAdvanceBalance != null) {
    facts.push({
      label: 'Advance balance',
      value: <MoneyCell amount={view.permanentAdvanceBalance} />,
    });
  }

  return (
    <Panel>
      {e && (
        <div
          style={{
            padding: 14,
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <PersonCell
            size="default"
            person={{
              fullName: e.fullName,
              employeeCode: e.employeeCode,
              designation: e.designation,
              profilePictureUrl: e.profilePictureUrl,
            }}
          />
        </div>
      )}
      <FactGrid facts={facts} />
    </Panel>
  );
}

/* --------------------------- Attendance ------------------------------ */

/** The attendance basis the slip was computed from — the "why" behind any
 *  LOP the earnings column shows. */
function AttendanceGrid({ view }: { view: PayslipView }) {
  const a = view.attendance;
  return (
    <Section title="Attendance">
      <Panel>
        <FactGrid
          facts={[
            { label: 'Working days', value: days(a.workingDays) },
            { label: 'Days worked', value: days(a.daysWorked) },
            { label: 'Absent days', value: days(a.absentDays) },
            { label: 'LOP days', value: days(a.lopDays) },
            { label: 'Holidays', value: days(a.holidaysInPeriod) },
            { label: 'Weekends', value: days(a.weekendsInPeriod) },
            {
              label: 'Overtime',
              value:
                a.otHours > 0 ? (
                  <span>
                    {a.otHours.toFixed(2)} hrs · <MoneyCell amount={a.otAmount} />
                  </span>
                ) : (
                  '—'
                ),
            },
          ]}
        />
      </Panel>
    </Section>
  );
}

/* ----------------------- Earnings / deductions ----------------------- */

/**
 * The two halves of the slip, side by side: everything added on the left,
 * everything taken off on the right, each footed with its own total. They
 * stack on narrow viewports (auto-fit grid), and the shorter column is
 * padded so both totals sit on the same baseline.
 */
function LineColumns({
  earnings,
  deductions,
  grossSalary,
  totalDeduction,
}: {
  earnings: PayslipViewLine[];
  deductions: PayslipViewLine[];
  grossSalary: number;
  totalDeduction: number;
}) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: 16,
        alignItems: 'stretch',
      }}
    >
      <LineColumn
        title="Earnings"
        tone="success"
        rows={earnings}
        totalLabel="Gross earnings"
        total={grossSalary}
      />
      <LineColumn
        title="Deductions"
        tone="warning"
        rows={deductions}
        totalLabel="Total deductions"
        total={totalDeduction}
        negative
      />
    </div>
  );
}

function LineColumn({
  title,
  tone,
  rows,
  totalLabel,
  total,
  negative = false,
}: {
  title: string;
  tone: 'success' | 'warning';
  rows: PayslipViewLine[];
  totalLabel: string;
  total: number;
  negative?: boolean;
}) {
  const { token } = useToken();
  const accent = tone === 'success' ? token.colorSuccess : token.colorWarning;

  return (
    <Section title={title} accent={accent}>
      <Panel
        style={{
          // Stretch so the two columns' total bars line up even when one
          // side has fewer rows.
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
        }}
      >
        <div style={{ flex: 1 }}>
          {rows.length === 0 ? (
            <div style={{ padding: '12px 14px' }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                No {title.toLowerCase()} on this payslip.
              </Text>
            </div>
          ) : (
            rows.map((r, i) => (
              <LineRow
                key={`${r.code}-${i}`}
                label={r.name}
                sub={r.sub}
                amount={r.amount}
                divider={i < rows.length - 1}
              />
            ))
          )}
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            gap: 12,
            padding: '10px 14px',
            background: token.colorFillQuaternary,
            fontWeight: 600,
            borderTop: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <span>{totalLabel}</span>
          <span style={{ whiteSpace: 'nowrap' }}>
            {negative && total > 0 ? '− ' : ''}
            <MoneyCell amount={total} />
          </span>
        </div>
      </Panel>
    </Section>
  );
}

function LineRow({
  label,
  sub,
  amount,
  divider,
}: {
  label: string;
  sub?: string;
  amount: number;
  divider: boolean;
}) {
  const { token } = useToken();
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'baseline',
        gap: 12,
        padding: '10px 14px',
        borderBottom: divider ? `1px solid ${token.colorBorderSecondary}` : undefined,
      }}
    >
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, color: token.colorText }}>{label}</div>
        {sub && sub !== label && (
          <div style={{ fontSize: 11, color: token.colorTextTertiary }}>{sub}</div>
        )}
      </div>
      <MoneyCell
        amount={amount}
        style={{ color: amount === 0 ? token.colorTextTertiary : undefined }}
      />
    </div>
  );
}

/* ------------------------------ Net ---------------------------------- */

/** Gross − total deductions = net, straight off the payload so the recap
 *  always matches what was actually paid. */
function NetRecap({ view }: { view: PayslipView }) {
  const { token } = useToken();

  return (
    <div
      style={{
        border: `1px solid ${token.colorBorder}`,
        borderRadius: token.borderRadiusLG,
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          padding: '10px 16px',
          color: token.colorTextSecondary,
        }}
      >
        <span>Gross earnings</span>
        <MoneyCell amount={view.grossSalary} />
      </div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          padding: '10px 16px',
          color: token.colorTextSecondary,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <span>Total deductions</span>
        <span>
          − <MoneyCell amount={view.totalDeduction} />
        </span>
      </div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '14px 16px',
          background: `color-mix(in srgb, ${token.colorPrimary} 6%, transparent)`,
        }}
      >
        <span style={{ fontWeight: 600 }}>Net payable</span>
        <MoneyCell
          amount={view.netSalary}
          style={{ fontWeight: 700, fontSize: 18 }}
        />
      </div>
    </div>
  );
}

/* ----------------------------- Leaves -------------------------------- */

/**
 * Leave position for the period. Annual leave reports against an
 * entitlement (it can be encashed), so it gets its own tile row; the
 * remaining types report usage only. Casual and medical draw on one pooled
 * entitlement, so they're grouped with a combined total — everything else
 * stays on its own row.
 */
function LeaveSection({ view }: { view: PayslipView }) {
  const { token } = useToken();
  const { casualMedical, casualMedicalTotal, other } = splitLeaves(view.leaves);

  if (!view.annualLeave && view.leaves.length === 0) return null;

  return (
    <Section title="Leaves">
      <Panel>
        {view.annualLeave && (
          <>
            <SubHeading>Annual leave</SubHeading>
            <FactGrid
              facts={[
                { label: 'Entitled', value: days(view.annualLeave.total) },
                { label: 'Availed', value: days(view.annualLeave.availed) },
                { label: 'Encashed', value: days(view.annualLeave.paid) },
                {
                  label: 'Balance',
                  value: (
                    <span
                      style={{
                        fontWeight: 600,
                        color:
                          view.annualLeave.balance > 0
                            ? token.colorSuccess
                            : token.colorTextTertiary,
                      }}
                    >
                      {days(view.annualLeave.balance)}
                    </span>
                  ),
                },
              ]}
              columns={4}
            />
          </>
        )}

        {view.leaves.length > 0 && (
          <>
            <SubHeading divider={!!view.annualLeave}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  gap: 12,
                }}
              >
                <span>Leave type</span>
                <span style={{ display: 'flex', gap: 24 }}>
                  <span style={{ width: 86, textAlign: 'right' }}>This month</span>
                  <span style={{ width: 86, textAlign: 'right' }}>This cycle</span>
                </span>
              </div>
            </SubHeading>
            {casualMedical.map((l) => (
              <LeaveRow key={l.code} line={l} />
            ))}
            {casualMedicalTotal && casualMedical.length > 1 && (
              <LeaveRow
                line={{
                  code: 'CASUAL_MEDICAL',
                  name: 'Casual + Medical (combined)',
                  ...casualMedicalTotal,
                }}
                emphasis
              />
            )}
            {other.map((l) => (
              <LeaveRow key={l.code} line={l} />
            ))}
          </>
        )}
      </Panel>
    </Section>
  );
}

function LeaveRow({
  line,
  emphasis = false,
}: {
  line: { code: string; name: string; availedThisMonth: number; availedThisCycle: number };
  emphasis?: boolean;
}) {
  const { token } = useToken();
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'baseline',
        gap: 12,
        padding: '9px 14px',
        borderTop: `1px solid ${token.colorBorderSecondary}`,
        background: emphasis ? token.colorFillQuaternary : undefined,
        fontWeight: emphasis ? 600 : undefined,
      }}
    >
      <span style={{ fontSize: 13, color: token.colorText }}>{line.name}</span>
      <span
        style={{
          display: 'flex',
          gap: 24,
          fontVariantNumeric: 'tabular-nums',
          fontSize: 13,
        }}
      >
        <span style={{ width: 86, textAlign: 'right' }}>
          {days(line.availedThisMonth)}
        </span>
        <span style={{ width: 86, textAlign: 'right' }}>
          {days(line.availedThisCycle)}
        </span>
      </span>
    </div>
  );
}

/* ---------------------------- Primitives ------------------------------ */

function Section({
  title,
  accent,
  children,
}: {
  title: string;
  accent?: string;
  children: ReactNode;
}) {
  const { token } = useToken();
  return (
    <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
      <div
        style={{
          fontSize: 13,
          fontWeight: 600,
          color: accent ?? token.colorTextSecondary,
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          marginBottom: 8,
        }}
      >
        {title}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>{children}</div>
    </div>
  );
}

function Panel({
  children,
  style,
}: {
  children: ReactNode;
  style?: React.CSSProperties;
}) {
  const { token } = useToken();
  return (
    <div
      style={{
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadiusLG,
        overflow: 'hidden',
        ...style,
      }}
    >
      {children}
    </div>
  );
}

function SubHeading({
  children,
  divider = false,
}: {
  children: ReactNode;
  divider?: boolean;
}) {
  const { token } = useToken();
  return (
    <div
      style={{
        padding: '8px 14px',
        fontSize: 11,
        textTransform: 'uppercase',
        letterSpacing: '0.04em',
        color: token.colorTextTertiary,
        background: token.colorFillQuaternary,
        borderTop: divider ? `1px solid ${token.colorBorderSecondary}` : undefined,
      }}
    >
      {children}
    </div>
  );
}

function FactGrid({
  facts,
  columns = 2,
}: {
  facts: { label: string; value: ReactNode }[];
  columns?: number;
}) {
  const { token } = useToken();
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(auto-fit, minmax(${columns >= 4 ? 120 : 150}px, 1fr))`,
        gap: 12,
        padding: 14,
      }}
    >
      {facts.map((f) => (
        <div key={f.label} style={{ minWidth: 0 }}>
          <div
            style={{
              fontSize: 11,
              color: token.colorTextTertiary,
              textTransform: 'uppercase',
              letterSpacing: '0.04em',
            }}
          >
            {f.label}
          </div>
          <div
            style={{
              fontSize: 14,
              color: token.colorText,
              overflowWrap: 'anywhere',
            }}
          >
            {f.value}
          </div>
        </div>
      ))}
    </div>
  );
}

/** Day counts come back fractional (half-days); drop the ".0" on whole
 *  numbers so a full-attendance slip doesn't read "27.0". */
function days(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}
