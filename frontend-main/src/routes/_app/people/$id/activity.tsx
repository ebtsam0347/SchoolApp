// /people/$id/activity — chronological feed of every event the BFF
// merges for this employee (employee_history rows + audit-service events
// from employee/attendance/payroll keyed by either employeeId or userId).

import { createFileRoute, Link, useParams } from '@tanstack/react-router';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Skeleton, Typography, theme as antdTheme } from 'antd';
import {
  BadgeCheck,
  Briefcase,
  Building2,
  CalendarClock,
  Camera,
  ChevronDown,
  CircleDot,
  Gavel,
  KeyRound,
  Layers,
  Lock,
  LogIn,
  LogOut,
  Network,
  PenLine,
  RefreshCcw,
  Shield,
  ShieldAlert,
  ShieldCheck,
  ShieldX,
  TrendingUp,
  Undo2,
  Unlock,
  UserMinus,
  UserPlus,
  type LucideIcon,
} from 'lucide-react';

import { TimelineItem, type StatusPillTone } from '@/components';
import { getErrorMessage } from '@/lib';
import {
  peopleBff,
  type EmployeeDetail,
  type EmployeeHistoryRow,
  type TimelineEntryView,
} from '@/people';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/people/$id/activity')({
  component: ActivityTab,
});

function ActivityTab() {
  const { id } = useParams({ from: '/_app/people/$id/activity' });
  const employeeId = Number(id);
  const { token } = useToken();
  const qc = useQueryClient();

  const query = useInfiniteQuery({
    queryKey: ['people', 'timeline', employeeId],
    queryFn: ({ signal, pageParam }) =>
      peopleBff.timeline(
        employeeId,
        { cursor: pageParam || undefined, limit: 25 },
        signal,
      ),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: Number.isFinite(employeeId),
  });

  if (query.isLoading) return <Skeleton active paragraph={{ rows: 6 }} />;

  if (query.isError) {
    return (
      <Alert
        type="error"
        showIcon
        title="Couldn't load activity"
        description={getErrorMessage(query.error)}
      />
    );
  }

  const events = query.data?.pages.flatMap((p) => p.content ?? []) ?? [];

  if (events.length === 0) {
    // Fall back to the assignment-history preview that the Overview tab
    // also surfaces. The Activity feed endpoint (audit-service merge) and
    // the composite detail's recentHistory (employee_history rows) come
    // from different queries today, so an empty Activity page coexists
    // with a non-empty Overview "Recent changes" card. Surfacing the same
    // data here keeps the two views consistent.
    const cached = qc.getQueryData<EmployeeDetail>([
      'people',
      'detail',
      employeeId,
    ]);
    const recentHistory = cached?.recentHistory ?? [];
    const recentTimeline = cached?.recentTimeline ?? [];
    if (recentHistory.length > 0 || recentTimeline.length > 0) {
      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <Alert
            type="info"
            showIcon
            title="No live audit events for this employee"
            description={
              <>
                Activity here is built from the audit feed, which only goes
                back to when this employee started using the portal. Older
                changes — including assignment history pre-migration —
                appear on the{' '}
                <Link
                  to="/people/$id/employment"
                  params={{ id: String(employeeId) }}
                >
                  Employment tab
                </Link>
                . The entries below are the most recent assignment changes
                we have on file.
              </>
            }
          />
          {recentTimeline.map((event, i) => (
            <TimelineRow
              key={`tl-${event.id ?? i}`}
              event={event}
              isLast={
                i === recentTimeline.length - 1 && recentHistory.length === 0
              }
            />
          ))}
          {recentHistory.map((row, i) => (
            <HistoryFallbackRow
              key={`h-${row.id ?? i}`}
              row={row}
              isLast={i === recentHistory.length - 1}
            />
          ))}
        </div>
      );
    }
    return (
      <div
        style={{
          padding: 32,
          textAlign: 'center',
          background: token.colorFillQuaternary,
          borderRadius: 12,
        }}
      >
        <Text type="secondary">
          No recorded activity yet. Activity will appear here as the
          employee's profile, contract, attendance, or salary changes.
        </Text>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {events.map((event, i) => (
        <TimelineRow
          key={event.id ?? i}
          event={event}
          isLast={i === events.length - 1 && !query.hasNextPage}
        />
      ))}
      {query.hasNextPage ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '12px 0' }}>
          <Button
            icon={<ChevronDown size={14} />}
            loading={query.isFetchingNextPage}
            onClick={() => query.fetchNextPage()}
          >
            Load older events
          </Button>
        </div>
      ) : null}
    </div>
  );
}

function TimelineRow({
  event,
  isLast,
}: {
  event: TimelineEntryView;
  isLast: boolean;
}) {
  const { icon, tone, title } = decorate(event);
  return (
    <TimelineItem
      title={title}
      timestamp={event.occurredAt ? new Date(event.occurredAt) : undefined}
      author={
        event.actorLabel
          ? { name: event.actorLabel }
          : event.actorUsername
            ? { name: event.actorUsername }
            : undefined
      }
      description={event.message ?? undefined}
      icon={icon}
      tone={tone}
      isLast={isLast}
    />
  );
}

/* --------------------- assignment-history fallback --------------------- */

function HistoryFallbackRow({
  row,
  isLast,
}: {
  row: EmployeeHistoryRow;
  isLast: boolean;
}) {
  const summary = (() => {
    const parts: string[] = [];
    if (row.designation?.title) parts.push(row.designation.title);
    if (row.grade?.name) parts.push(row.grade.name);
    if (row.department?.name) parts.push(row.department.name);
    if (row.section?.name) parts.push(`/ ${row.section.name}`);
    if (row.manager?.fullName) parts.push(`under ${row.manager.fullName}`);
    return parts.join(' · ') || 'Updated';
  })();
  const reason = row.changeReason
    ? row.changeReason
        .replace(/_/g, ' ')
        .toLowerCase()
        .replace(/^(.)/, (c) => c.toUpperCase())
    : 'Assignment';
  return (
    <TimelineItem
      title={`${reason} · ${summary}`}
      timestamp={row.effectiveFrom ? new Date(row.effectiveFrom) : undefined}
      description={
        row.notes
          ? row.notes.includes('Migrated from WebHRMS')
            ? 'Imported from the previous HR system; older history not available.'
            : row.notes
          : undefined
      }
      icon={CircleDot}
      tone="neutral"
      isLast={isLast}
    />
  );
}

/* --------------------- action → icon/tone/title decoration --------------------- */

// Action strings come from `AuditEmitter.emit(entityType, entityId, action, …)`
// — bare strings, not entityType-prefixed. The BFF merges two streams:
//   • entityType="employee" — every employee-service mutation against this id
//   • entityType="user" — when ESS is provisioned, the linked user's auth-side
//     events are folded in (logins, password changes, role assignments, …).
// Both arrive on this timeline; both need decorations here or they fall through
// to the generic neutral row.
const DECORATIONS: Record<
  string,
  { icon: LucideIcon; tone: StatusPillTone; title: string }
> = {
  // ----- employee lifecycle -----
  onboarded: { icon: UserPlus, tone: 'success', title: 'Employee created' },
  updated: { icon: PenLine, tone: 'info', title: 'Profile updated' },
  self_updated: { icon: PenLine, tone: 'info', title: 'Self-service update' },
  profile_picture_updated: {
    icon: Camera,
    tone: 'info',
    title: 'Profile picture updated',
  },
  confirmed: { icon: BadgeCheck, tone: 'success', title: 'Employee confirmed' },
  probation_extended: {
    icon: CalendarClock,
    tone: 'warning',
    title: 'Probation extended',
  },

  // ----- assignment / org changes -----
  manager_changed: { icon: Network, tone: 'info', title: 'Manager changed' },
  department_changed: {
    icon: Building2,
    tone: 'info',
    title: 'Department changed',
  },
  section_changed: { icon: Layers, tone: 'info', title: 'Section changed' },
  designation_changed: {
    icon: Briefcase,
    tone: 'info',
    title: 'Designation changed',
  },
  grade_changed: { icon: TrendingUp, tone: 'info', title: 'Grade changed' },

  // ----- discipline / suspension -----
  suspended: { icon: ShieldX, tone: 'error', title: 'Suspended' },
  suspension_lifted: {
    icon: ShieldCheck,
    tone: 'success',
    title: 'Suspension lifted',
  },
  disciplinary_issued: {
    icon: ShieldAlert,
    tone: 'warning',
    title: 'Warning issued',
  },
  disciplinary_decided: {
    icon: Gavel,
    tone: 'info',
    title: 'Warning decided',
  },

  // ----- exit -----
  resignation_submitted: {
    icon: LogOut,
    tone: 'warning',
    title: 'Resignation submitted',
  },
  resignation_withdrawn: {
    icon: Undo2,
    tone: 'info',
    title: 'Resignation withdrawn',
  },
  terminated: { icon: UserMinus, tone: 'error', title: 'Terminated' },

  // ----- ESS provisioning -----
  'ess.provisioned': {
    icon: KeyRound,
    tone: 'success',
    title: 'ESS account provisioned',
  },
  ess_granted: {
    icon: KeyRound,
    tone: 'success',
    title: 'ESS access granted',
  },
  ess_revoked: {
    icon: KeyRound,
    tone: 'warning',
    title: 'ESS access revoked',
  },

  // ----- linked user (auth) — surface only when employee has ESS -----
  'login.success': { icon: LogIn, tone: 'info', title: 'Signed in' },
  'login.failed': {
    icon: ShieldAlert,
    tone: 'warning',
    title: 'Sign-in failed',
  },
  'user.locked': { icon: Lock, tone: 'error', title: 'Account locked' },
  unlocked: { icon: Unlock, tone: 'success', title: 'Account unlocked' },
  'password.changed': {
    icon: KeyRound,
    tone: 'info',
    title: 'Password changed',
  },
  'password.reset_by_admin': {
    icon: KeyRound,
    tone: 'warning',
    title: 'Password reset by admin',
  },
  'role.assigned': { icon: Shield, tone: 'info', title: 'Role assigned' },
  'role.revoked': { icon: Shield, tone: 'warning', title: 'Role revoked' },
  'full_name.synced': {
    icon: RefreshCcw,
    tone: 'info',
    title: 'Name synced from HR',
  },
  deactivated: {
    icon: UserMinus,
    tone: 'warning',
    title: 'Account deactivated',
  },
};

function decorate(event: TimelineEntryView): {
  icon: LucideIcon;
  tone: StatusPillTone;
  title: string;
} {
  const action = event.action ?? '';
  const decoration = DECORATIONS[action];
  if (decoration) return decoration;
  // Fallback — humanized action key, generic dot. We reach this when a new
  // backend action lands before the map gets updated; it stays readable.
  return {
    icon: CircleDot,
    tone: 'neutral',
    title: humanizeAction(action),
  };
}

function humanizeAction(action: string): string {
  if (!action) return 'Event';
  return action
    .split('.')
    .pop()!
    .replace(/_/g, ' ')
    .replace(/^./, (c) => c.toUpperCase());
}
