/**
 * Adapter: BFF `PayrollDashboard` → view-model `PayrollDashboardDto`.
 *
 * BFF returns uniform `QueueItem{id, employee, summary, reason,
 * occurredAt}` rows for all 4 queues; we expand to category-specific
 * row types (anomaly / off-cycle / salary change / payslip hold) so
 * the layout can keep its targeted rendering. variancePct on
 * anomalies is joined from the scatter points by employeeId — the BFF
 * surfaces both, we just stitch them locally.
 */
import type { BffPayrollDashboard } from '../bff-types';
import type {
  OffCycleApprovalRow,
  PayrollAnomalyRow,
  PayrollDashboardDto,
  PayslipHoldRow,
  SalaryChangeRow,
} from '../types';

export function toPayrollDashboardDto(
  src: BffPayrollDashboard,
): PayrollDashboardDto {
  // Build a quick employeeId → variancePct lookup for the anomaly join.
  // The backend may not have computed scatter yet — when null, the
  // map stays empty and queue rows get `variancePct: null` so the
  // layout renders a generic "Anomaly" pill instead of "+0%".
  const variance = new Map<number, number>();
  if (src.anomalyScatter) {
    for (const p of src.anomalyScatter.points) {
      variance.set(p.employeeId, p.variancePct);
    }
  }

  return {
    currentRun: {
      id: src.currentRun.id,
      fiscalYear: src.currentRun.fiscalYear,
      period: src.currentRun.period,
      status: src.currentRun.status,
      cutoffDate: src.currentRun.cutoffDate,
      daysToCutoff: src.currentRun.daysToCutoff,
      rollup: src.currentRun.rollup,
    },
    runs6m: {
      runs: src.runs6m.runs.map((r) => ({
        id: r.id,
        period: r.period,
        gross: r.gross,
        deductions: r.deductions,
        net: r.net,
      })),
    },
    needsYou: {
      anomaliesOnRun: {
        count: src.needsYou.anomaliesOnRun.count,
        items: src.needsYou.anomaliesOnRun.items.map<PayrollAnomalyRow>((q) => ({
          id: String(q.id),
          personName: q.employee.fullName,
          departmentName: q.employee.departmentName ?? '',
          summary: q.summary,
          variancePct: variance.has(q.employee.id)
            ? variance.get(q.employee.id)!
            : null,
          href: '/payroll/runs',
        })),
      },
      offCyclePending: {
        count: src.needsYou.offCyclePending.count,
        items: src.needsYou.offCyclePending.items.map<OffCycleApprovalRow>(
          (q) => ({
            id: String(q.id),
            personName: q.employee.fullName,
            category: q.summary,
            amount: null,
            submittedAt: q.occurredAt,
            href: '/payroll/off-cycle',
          }),
        ),
      },
      salaryChangesPending: {
        count: src.needsYou.salaryChangesPending.count,
        items: src.needsYou.salaryChangesPending.items.map<SalaryChangeRow>(
          (q) => ({
            id: String(q.id),
            personName: q.employee.fullName,
            effectiveOn: q.occurredAt.slice(0, 10),
            summary: q.summary,
            href: '/payroll/increments',
          }),
        ),
      },
      payslipsHeld: {
        count: src.needsYou.payslipsHeld.count,
        items: src.needsYou.payslipsHeld.items.map<PayslipHoldRow>((q) => ({
          id: String(q.id),
          personName: q.employee.fullName,
          runPeriod: extractRunPeriod(q.summary, q.reason ?? ''),
          reason: q.reason ?? q.summary,
          amount: null,
          href: '/payroll/queue',
        })),
      },
    },
    anomalyScatter: src.anomalyScatter
      ? {
          runId: src.anomalyScatter.runId,
          comparedTo: src.anomalyScatter.comparedTo,
          points: src.anomalyScatter.points.map((p) => ({
            employeeId: p.employeeId,
            name: p.name,
            departmentName: p.departmentName ?? '',
            priorNet: p.priorNet,
            currentNet: p.currentNet,
            variancePct: p.variancePct,
          })),
        }
      : { runId: src.currentRun.id, comparedTo: 0, points: [] },
    bonusByDepartment90d: src.bonusByDepartment90d.map((b) => ({
      departmentId: b.departmentId,
      name: b.name,
      total: b.total,
    })),
    netYoYTrend12m: src.netYoYTrend12m,
  };
}

/**
 * BFF doesn't denormalize the run period onto held-payslip rows. The
 * summary or reason often carries it (e.g. "Apr 2026" or "2026-04").
 * Try to extract a YYYY-MM; otherwise return null and the layout
 * gracefully drops the period prefix.
 */
function extractRunPeriod(...candidates: string[]): string | null {
  for (const c of candidates) {
    const ym = /(\d{4})-(\d{2})/.exec(c);
    if (ym) return `${ym[1]}-${ym[2]}`;
  }
  return null;
}
