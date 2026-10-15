/**
 * Adapter: BFF `AdminDashboard` → view-model `AdminDashboardDto`.
 *
 * Three queues collapse to a uniform `AdminQueueRow{label, summary,
 * retryHref}` shape on the BFF side; the view-model mirrors that
 * one-to-one. AuditEventView from `recentRoleChanges` is summarised
 * into `RoleChangeRow` (actor + summary + occurredAt).
 */
import type {
  BffAdminDashboard,
  BffAdminQueueItem,
  BffAdminSagaHealth,
  BffAuditEventView,
} from '../bff-types';
import type {
  AdminDashboardDto,
  AdminQueueRow,
  RoleChangeRow,
  SagaHealthRow,
  SagaHealthStatus,
  SystemHealthOverall,
} from '../types';

export function toAdminDashboardDto(src: BffAdminDashboard): AdminDashboardDto {
  return {
    systemHealth: {
      overall: toOverall(src.systemHealth.overall),
      sagas: {
        running: src.systemHealth.sagas.running,
        failed24h: src.systemHealth.sagas.failed24h,
        list: src.systemHealth.sagas.list.map(toSagaRow),
      },
      auditReplayQueue: src.systemHealth.auditReplayQueue,
    },
    auditVolume7d: src.auditVolume7d,
    needsYou: {
      failedSagas: {
        count: src.needsYou.failedSagas.count,
        items: src.needsYou.failedSagas.items.map(toAdminQueueRow),
      },
      permissionChanges24h: {
        count: src.needsYou.permissionChanges24h.count,
        items: src.needsYou.permissionChanges24h.items.map(toAdminQueueRow),
      },
      lockedAccounts: {
        count: src.needsYou.lockedAccounts.count,
        items: src.needsYou.lockedAccounts.items.map(toAdminQueueRow),
      },
    },
    authEventsByHour: src.authEventsByHour,
    recentRoleChanges: src.recentRoleChanges.map(toRoleChangeRow),
  };
}

function toAdminQueueRow(row: BffAdminQueueItem): AdminQueueRow {
  return {
    id: String(row.id),
    label: row.label,
    summary: row.summary,
    retryHref: row.retryHref,
  };
}

function toSagaRow(row: BffAdminSagaHealth): SagaHealthRow {
  return {
    id: row.id,
    name: row.name,
    status: toSagaStatus(row.status),
    failureRate24h: row.failureRate24h,
  };
}

function toRoleChangeRow(row: BffAuditEventView): RoleChangeRow {
  const actor = row.actorLabel ?? row.actorUsername ?? 'system';
  // Subject is encoded in entityType/entityId pairs on the BFF; skip the
  // free-form name unless we wire a resolver later.
  // System events (entity CRUD) often lack a `message` — synthesize one
  // from action + entity so the row reads as something rather than blank.
  const summary = row.message ?? deriveSummary(row);
  return {
    id: String(row.id),
    actorName: actor,
    subjectName: null,
    summary,
    occurredAt: row.occurredAt,
    href: '/admin/audit',
  };
}

function deriveSummary(row: BffAuditEventView): string {
  const action = row.action.toLowerCase();
  const entity = row.entityType ?? 'record';
  const id = row.entityId ? ` (#${row.entityId})` : '';
  return `${capitalise(action)} ${entity}${id}`;
}

function capitalise(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function toOverall(s: string): SystemHealthOverall {
  switch (s) {
    case 'OK':
    case 'DEGRADED':
    case 'INCIDENT':
      return s;
    default:
      return 'OK';
  }
}

function toSagaStatus(s: string): SagaHealthStatus {
  switch (s) {
    case 'OK':
    case 'DEGRADED':
    case 'INCIDENT':
    case 'UNKNOWN':
      return s;
    default:
      return 'UNKNOWN';
  }
}
