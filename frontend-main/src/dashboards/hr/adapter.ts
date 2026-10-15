/**
 * Adapter: BFF `HrDashboard` → view-model `HrDashboardDto`.
 *
 * Three of the four queues (onboarding stuck, contracts expiring,
 * disciplinary open) come back from the BFF as a uniform
 * `QueueItem{kind, employee, summary, due}`. We expand each into the
 * row shape the layout already expects, deriving `daysLeft` /
 * `ageDays` from `due` so the layout's date math stays the same.
 */
import { toIsoDate, daysUntil, daysSince } from '@/lib/dates';

import type {
  BffHrDashboard,
  BffHrQueueItem,
  BffTimelineEntryView,
} from '../bff-types';
import type {
  AuditAnomalyRow,
  ContractExpiryRow,
  DisciplinaryRow,
  HrDashboardDto,
  LeaveHotspotRow,
  OnboardingStuckRow,
} from '../types';

export function toHrDashboardDto(src: BffHrDashboard): HrDashboardDto {
  return {
    orgPulse: src.orgPulse,
    attendance30d: src.attendance30d,
    headcount12m: src.headcount12m,
    queues: {
      onboardingStuck: {
        count: src.queues.onboardingStuck.count,
        items: src.queues.onboardingStuck.items.map(toOnboardingRow),
      },
      contractsExpiring: {
        count: src.queues.contractsExpiring.count,
        items: src.queues.contractsExpiring.items.map(toContractExpiryRow),
      },
      disciplinaryOpen: {
        count: src.queues.disciplinaryOpen.count,
        items: src.queues.disciplinaryOpen.items.map(toDisciplinaryRow),
      },
      leaveHotspots: {
        count: src.queues.leaveHotspots.count,
        items: src.queues.leaveHotspots.items.map<LeaveHotspotRow>((r) => ({
          personId: r.personId,
          personName: r.name,
          departmentName: r.departmentName ?? '',
          annualUsedPct: r.annualUsedPct,
          remainingDays: r.remainingDays,
          href: '/people/employment',
        })),
      },
    },
    departmentLeave30d: src.departmentLeave30d.map((d) => ({
      departmentId: d.departmentId,
      name: d.name,
      leaveDays: d.leaveDays,
    })),
    recentAuditAnomalies: src.recentAuditAnomalies.map(toAuditAnomalyRow),
  };
}

/* —————— row mappers —————— */

function toOnboardingRow(row: BffHrQueueItem): OnboardingStuckRow {
  return {
    id: String(row.id),
    personName: row.employee.fullName,
    stage: row.summary,
    ageDays: row.due ? daysSince(row.due) : 0,
    href: '/people/queues/onboard',
  };
}

function toContractExpiryRow(row: BffHrQueueItem): ContractExpiryRow {
  return {
    id: String(row.id),
    personName: row.employee.fullName,
    contractType: row.summary,
    expiresOn: row.due ?? toIsoDate(new Date()),
    daysLeft: row.due ? daysUntil(row.due) : 0,
    href: '/people/employment',
  };
}

function toDisciplinaryRow(row: BffHrQueueItem): DisciplinaryRow {
  return {
    id: String(row.id),
    personName: row.employee.fullName,
    level: row.summary,
    status: 'Open',
    openedOn: row.due ?? toIsoDate(new Date()),
    href: '/people/queues',
  };
}

function toAuditAnomalyRow(row: BffTimelineEntryView): AuditAnomalyRow {
  return {
    id: String(row.id),
    occurredAt: row.occurredAt,
    summary: row.message ?? `${row.action} event`,
    href: '/admin/audit',
  };
}
