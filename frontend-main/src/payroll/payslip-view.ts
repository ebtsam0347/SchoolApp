// View model the payslip drawer renders, plus adapters from the two
// payloads that can feed it.
//
// Why a view model: the run-detail composite
// (`GET /payroll/runs/{id}/detail`) returns a full salary slip — identity,
// bank, earnings/deductions blocks, attendance basis, leave position —
// while the service-direct `GET /payslips/{id}` still returns the older
// flat `PayslipDto` (no employee name, no bank, no leaves). Rather than
// branch all through the drawer, both are normalized here and the drawer
// renders one shape, degrading gracefully on the blocks the flat DTO
// can't supply (`employee` / `bank` / `leaves` come back null).

import type {
  PayslipAmountLine,
  PayslipAnnualLeave,
  PayslipAttendanceBlock,
  PayslipBankDetail,
  PayslipEmployeeDetail,
  PayslipLeaveLine,
  PayslipWithEmployee,
} from './composites';
import type { PayslipDto, PayslipStatus } from './types';

/** An earnings / deductions row. `sub` carries the per-day working when the
 *  source payload has one (flat DTO only); otherwise it's the code. */
export type PayslipViewLine = PayslipAmountLine & { sub: string };

export type PayslipView = {
  id: number;
  periodYear: number;
  periodMonth: number;
  fiscalYearCode: string;
  status: PayslipStatus;
  heldReason: string | null;
  generatedAt: string | null;
  /** True when a PDF is (or should be) downloadable for this slip. */
  pdfAvailable: boolean;

  employee: PayslipEmployeeDetail | null;
  /** Fallback identity for sources with no employee block. */
  employeeCode: string | null;
  bank: PayslipBankDetail | null;

  earnings: PayslipViewLine[];
  deductions: PayslipViewLine[];
  /** Foot of the earnings column — from the payload, not re-summed. */
  grossSalary: number;
  /** Foot of the deductions column — from the payload, not re-summed. */
  totalDeduction: number;
  netSalary: number;
  /** Employer-side PESSI. Shown as a footnote, never deducted from net. */
  pessiEmployer: number;
  permanentAdvanceBalance: number | null;

  attendance: PayslipAttendanceBlock;
  annualLeave: PayslipAnnualLeave | null;
  leaves: PayslipLeaveLine[];
};

/** Run-detail composite row → view. Everything is already in the shape the
 *  drawer wants; only the line `sub` labels are derived. */
export function payslipViewFromRunRow(row: PayslipWithEmployee): PayslipView {
  return {
    id: row.id,
    periodYear: row.periodYear,
    periodMonth: row.periodMonth,
    fiscalYearCode: row.fiscalYearCode,
    status: row.status,
    heldReason: row.heldReason,
    generatedAt: row.generatedAt,
    // The composite doesn't carry `pdfObjectKey`. A DRAFT slip is never
    // posted so it can't have one; for anything past DRAFT, offer the
    // download and let the signed-URL call report if it isn't ready.
    pdfAvailable: row.status !== 'DRAFT',

    employee: row.employee ?? null,
    employeeCode: row.employee?.employeeCode ?? null,
    bank: row.bank,

    earnings: withSub(row.earnings?.lines),
    deductions: withSub(row.deductions?.lines),
    grossSalary: row.earnings?.grossSalary ?? row.salary.grossSalary,
    totalDeduction: row.deductions?.totalDeduction ?? row.salary.totalDeduction,
    netSalary: row.salary.netSalary,
    pessiEmployer: row.salary.pessiEmployer,
    permanentAdvanceBalance: row.salary.permanentAdvanceBalance,

    attendance: row.attendance,
    annualLeave: row.leaves?.annualLeave ?? null,
    leaves: row.leaves?.leaves ?? [],
  };
}

/**
 * Flat `PayslipDto` → view, for the ESS / team / queue surfaces that only
 * have `GET /payslips/{id}`.
 *
 * Two wrinkles this smooths over:
 *  - EOBI arrives as its own `eobi` field, not as a DEDUCTION line, so it
 *    is appended as one (guarded, in case the backend starts emitting it).
 *  - `dto.deductions` excludes EOBI, so `gross − net` is the honest total
 *    for the deductions column; that keeps the column foot equal to the
 *    net recap below it.
 */
export function payslipViewFromDto(ps: PayslipDto): PayslipView {
  const lines = ps.lines ?? [];
  const deductionLines = lines.filter((l) => l.type === 'DEDUCTION');
  const hasEobiLine = deductionLines.some(
    (l) => /eobi/i.test(l.code) || /eobi/i.test(l.name),
  );

  const deductions: PayslipViewLine[] = deductionLines.map(toViewLine);
  if (ps.eobi > 0 && !hasEobiLine) {
    deductions.push({
      code: 'EOBI',
      name: "Employees' Old-Age Benefits",
      amount: ps.eobi,
      sub: 'EOBI',
    });
  }

  return {
    id: ps.id,
    periodYear: ps.periodYear,
    periodMonth: ps.periodMonth,
    fiscalYearCode: ps.fiscalYearCode,
    status: ps.status,
    heldReason: ps.heldReason,
    generatedAt: ps.generatedAt,
    pdfAvailable: ps.pdfObjectKey != null,

    employee: null,
    employeeCode: ps.employeeCode,
    bank: null,

    earnings: lines.filter((l) => l.type === 'EARNING').map(toViewLine),
    deductions,
    grossSalary: ps.gross,
    totalDeduction: ps.gross - ps.net,
    netSalary: ps.net,
    pessiEmployer: ps.pessiEmployer,
    permanentAdvanceBalance: null,

    attendance: {
      workingDays: ps.workingDays,
      daysWorked: ps.paidDays,
      absentDays: ps.absentDays,
      holidaysInPeriod: ps.holidaysInPeriod,
      weekendsInPeriod: ps.weekendsInPeriod,
      lopDays: ps.lopDays,
      otHours: ps.otHours,
      otAmount: ps.otAmount,
    },
    annualLeave: null,
    leaves: [],
  };
}

function withSub(lines: PayslipAmountLine[] | undefined): PayslipViewLine[] {
  return (lines ?? []).map((l) => ({ ...l, sub: l.code }));
}

function toViewLine(l: {
  code: string;
  name: string;
  amount: number;
  calcType?: string | null;
  rate?: number | null;
  days?: number | null;
}): PayslipViewLine {
  const perDay =
    l.calcType === 'PER_DAY' && l.rate != null && l.days != null
      ? `Rs ${l.rate.toLocaleString()} × ${l.days} days`
      : null;
  return { code: l.code, name: l.name, amount: l.amount, sub: perDay ?? l.code };
}

/* ----------------------------- Leave split ---------------------------- */

/**
 * Casual and medical leave share one pooled entitlement, so HR reads them
 * together — the slip shows each on its own row plus a combined total.
 * Matches `CASUAL`, `MEDICAL_M1` and any future `MEDICAL_*` / `CASUAL_*`
 * codes; everything else (special leave, factory duty, …) stays separate.
 */
export function isCasualOrMedical(code: string): boolean {
  return /^(CASUAL|MEDICAL)/i.test(code);
}

export type LeaveSplit = {
  /** Casual + medical rows, in payload order. */
  casualMedical: PayslipLeaveLine[];
  /** Their combined usage — null when neither type is on the slip. */
  casualMedicalTotal: { availedThisMonth: number; availedThisCycle: number } | null;
  /** Every other leave type on the slip. */
  other: PayslipLeaveLine[];
};

export function splitLeaves(leaves: PayslipLeaveLine[]): LeaveSplit {
  const casualMedical = leaves.filter((l) => isCasualOrMedical(l.code));
  const other = leaves.filter((l) => !isCasualOrMedical(l.code));
  return {
    casualMedical,
    casualMedicalTotal: casualMedical.length
      ? {
          availedThisMonth: sum(casualMedical, (l) => l.availedThisMonth),
          availedThisCycle: sum(casualMedical, (l) => l.availedThisCycle),
        }
      : null,
    other,
  };
}

function sum<T>(rows: T[], pick: (row: T) => number): number {
  return rows.reduce((acc, row) => acc + (pick(row) || 0), 0);
}
