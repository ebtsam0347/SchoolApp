// /time/generated-requests — "Generated team requests": every request in the
// caller's department, with where each one is in its approval chain — the
// team-request generator's counterpart of a manager seeing their reports'
// requests. Read-only — approvals happen in /inbox.
//
// Same gates as /time/team-requests, and they must agree:
//   1. The sidebar row (`requiresRole` in nav-config.ts).
//   2. `beforeLoad` below.
// Both key off TEAM_REQUEST_GENERATION with no admin bypass. Within the page,
// each type tab is gated on the same `team.request.generation.<verb>`
// permission as its filing tab, so the two pages always show the same types.
//
// Two scopes, switched by the "Whole department / Filed by me" control:
//   department (default)  GET /<type>/department — every request for anyone
//                         in the caller's department, whoever filed it (the
//                         employee, their manager, or a request generator).
//                         Department resolved server-side; each list gated
//                         on its `team.request.generation.<verb>`. Takes an
//                         optional `employeeId` (the employee filter).
//   mine                  GET /<type>/filed-by-me — only what the caller
//                         filed for colleagues (self-submissions excluded).
// <type> is leaves, short-leaves, overtime, regularizations,
// short-term-advance-batches and permanent-advances. The attendance rows carry
// the target's employee summary; the payroll ones don't, so their names come
// from the department roster (which also feeds the employee filter).
//
// Each row shows its chain as a compact stage tracker (Manager → HR → …,
// with the stage it's sitting at ringed), plus a one-line "With HR · name" /
// "Approved" / "Rejected at Manager". History opens the full timeline with
// who decided each step, when, and their note.
//
// Filters: the type tabs pick the request type; a filed-on date range
// (`filedFrom` / `filedTo`, inclusive) is applied server-side on every tab;
// status is server-side on a type tab and a phase filter on the All tab;
// the department view adds an employee filter.
//
// The All tab merges the latest ALL_PER_TYPE of every type the caller holds
// within the date range, newest first, filtered by phase (in progress /
// approved / rejected / cancelled) client-side. A type whose list fails to
// load is named in a notice rather than blanking the whole view — and an
// API build that doesn't serve that list yet (400/404) is called out as
// such, not as a failure.
//
// Tab, scope, employee, status, date range and page live in the URL so a
// view is linkable.

import { useContext, useMemo, useState } from 'react';
import { createFileRoute, Link, redirect, useNavigate, useSearch } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  DatePicker,
  Segmented,
  Select,
  Space,
  Table,
  Tooltip,
  Typography,
  theme as antdTheme,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  Banknote,
  CalendarCheck2,
  CalendarPlus,
  Clock,
  Coins,
  FilterX,
  History,
  ListChecks,
  RefreshCcw,
  Send,
  Timer,
  type LucideIcon,
} from 'lucide-react';

import {
  EmptyState,
  PageHeader,
  PersonCell,
  RequestState,
  Section,
  StatusPill,
} from '@/components';
import { AbilityContext, hasAnyRole, type AppAbility } from '@/auth';
import { isApiError, readSnapshot } from '@/lib';
import {
  LEAVE_STATUS_LABEL,
  LEAVE_STATUS_TONE,
  OVERTIME_STATUS_LABEL,
  OVERTIME_STATUS_TONE,
  OVERTIME_TYPE_LABEL,
  REGULARIZATION_STATUS_LABEL,
  REGULARIZATION_STATUS_TONE,
  SHORT_LEAVE_STATUS_LABEL,
  SHORT_LEAVE_STATUS_TONE,
  attendanceApi,
  formatClock,
  formatDateShort,
  formatDateTime,
  formatMinutes,
  prettyLeaveCode,
  type ApprovalStepDto,
  type OvertimeType,
  type EmployeeSummary,
  type OvertimeListRow,
  type TeamLeaveRow,
  type TeamShortLeaveRow,
} from '@/attendance';
import { ShortLeaveTable } from '@/attendance/short-leaves';
import { LeaveTable } from '@/attendance/leaves';
import { OvertimeTable } from '@/attendance/overtime';
import {
  PHASE_LABEL,
  RequestHistoryDrawer,
  StageTrack,
  phaseOf,
  type Phase,
  type TrackedPerson,
  type TrackedRequestRow,
} from '@/attendance/approval-track';
import {
  ADVANCE_STATUS_LABEL,
  ADVANCE_STATUS_TONE,
  permanentAdvancesApi,
  shortTermAdvancesApi,
} from '@/payroll';
import { formatPeriod } from '@/payroll/format';
import { useDepartmentRoster } from '@/people/department-roster';
import type { EmployeeDto } from '@/people/types';

const { Text } = Typography;
const { useToken } = antdTheme;

/** Role that owns this page. Mirrors nav-config's `requiresRole`. */
const TEAM_REQUEST_ROLE = 'TEAM_REQUEST_GENERATION';
/** Subject half of every `team.request.generation.<verb>` permission. */
const TRG_SUBJECT = 'team.request.generation';

/* ------------------------------ row model ------------------------------ */

type Person = TrackedPerson;

/** One request, whatever its type — what the table and drawer render. */
type GeneratedRow = TrackedRequestRow & {
  key: string;
  /** Set on the All tab, where rows of every type mix. */
  typeLabel?: string;
  /** Short-leave rows: the request itself, for the Short leave tab's own
   *  table (same columns as /time/short-leave). */
  shortLeave?: TeamShortLeaveRow;
  /** Leave rows: the request itself, for the Leave tab's own table (same
   *  columns as /time/leaves). */
  leave?: TeamLeaveRow;
  /** Overtime rows: the request itself (with its punch-derived minutes),
   *  for the Overtime tab's own table (same columns as /time/overtime). */
  overtime?: OvertimeListRow;
};

type Page = {
  rows: GeneratedRow[];
  total: number;
  /** All tab: the types whose list failed to load. */
  failed?: { label: string; unavailable: boolean }[];
};

/**
 * The server doesn't serve this list yet — an API build without the
 * `…/filed-by-me` route reads the segment as an `{id}` (400) or has no match
 * at all (404 / 405). Told apart from a real failure so the page can say
 * "needs a backend deploy" instead of a bare error.
 */
function isUnavailable(err: unknown): boolean {
  return isApiError(err) && [400, 404, 405].includes(err.status);
}

/** Every DTO here carries the backend's real chain, even where the FE type
 *  (advances) doesn't declare it yet. */
type WithSteps = { steps?: ApprovalStepDto[] | null };

/** Short-leave times come as a bare `HH:mm[:ss]` — not the datetime
 *  `formatClock` expects. */
const hhmm = (t: string | null | undefined) => (t ? t.slice(0, 5) : '—');

/** The wire field is `overtimeType`; the FE type still says `type` (same
 *  fallback as the overtime inbox). */
const overtimeTypeLabel = (r: { type?: OvertimeType; overtimeType?: OvertimeType }) => {
  const t = r.overtimeType ?? r.type;
  return t ? OVERTIME_TYPE_LABEL[t] : 'Overtime';
};

const money = (n: number) => `Rs ${Number(n).toLocaleString('en-PK')}`;

const summaryPerson = (e: EmployeeSummary | null, id: number): Person => ({
  fullName: e?.fullName ?? `#${id}`,
  employeeCode: e?.employeeCode ?? null,
  profilePictureUrl: e?.profilePictureUrl ?? null,
});

const rosterPerson = (roster: Map<number, EmployeeDto>, id: number): Person => {
  const e = roster.get(id);
  return {
    fullName: e?.fullName ?? `Employee #${id}`,
    employeeCode: e?.employeeCode ?? null,
    profilePictureUrl: e?.profilePictureUrl ?? null,
  };
};

/* ------------------------------ tabs ------------------------------ */

type GeneratedTab =
  | 'all'
  | 'leave'
  | 'short-leave'
  | 'overtime'
  | 'regularization'
  | 'short-term-advance'
  | 'long-term-advance';

type StatusOption = { value: string; label: string };

/** Whose requests: everyone in the caller's department, or only the ones
 *  the caller filed for colleagues. */
type Scope = 'department' | 'mine';

type FetchArgs = {
  scope: Scope;
  /** Department scope only: one colleague. */
  employeeId?: number;
  status?: string;
  /** Filed on or after / before, YYYY-MM-DD inclusive. */
  filedFrom?: string;
  filedTo?: string;
  page: number;
  size: number;
  roster: Map<number, EmployeeDto>;
};

type TabDef = {
  key: GeneratedTab;
  label: string;
  icon: LucideIcon;
  /** Same verb as the matching /time/team-requests tab. Null for the All
   *  tab, which shows whichever type tabs the caller holds. */
  permission: string | null;
  statuses: StatusOption[];
  /** Advances have no target summary on the row — names need the roster. */
  needsRoster?: boolean;
  fetch: (args: FetchArgs, signal: AbortSignal) => Promise<Page>;
};

/** The employee filter only exists on the department lists. */
const scoped = (scope: Scope, employeeId?: number) =>
  scope === 'department' && employeeId != null ? { employeeId } : {};

const options = (labels: Record<string, string>): StatusOption[] =>
  Object.entries(labels).map(([value, label]) => ({ value, label }));

const TABS: TabDef[] = [
  {
    key: 'leave',
    label: 'Leave',
    icon: CalendarPlus,
    permission: 'leave',
    statuses: options(LEAVE_STATUS_LABEL),
    fetch: async ({ scope, employeeId, status, filedFrom, filedTo, page, size }, signal) => {
      const res = await (
        scope === 'department' ? attendanceApi.leavesDepartment : attendanceApi.leavesFiledByMe
      )(
        { status: status as never, filedFrom, filedTo, page, size, ...scoped(scope, employeeId) },
        signal,
      );
      return {
        total: res.totalElements,
        rows: res.content.map(({ request: r, employee }) => ({
          key: `leave-${r.id}`,
          leave: { ...r, employee },
          code: r.requestCode,
          person: summaryPerson(employee, r.employeeId),
          detail: `${prettyLeaveCode(r.leaveTypeCode)} · ${formatDateShort(r.fromDate)}${
            r.toDate !== r.fromDate ? ` – ${formatDateShort(r.toDate)}` : ''
          } · ${r.days} ${r.days === 1 ? 'day' : 'days'}`,
          statusLabel: LEAVE_STATUS_LABEL[r.status],
          statusTone: LEAVE_STATUS_TONE[r.status],
          rawStatus: r.status,
          currentStage: r.currentStage,
          steps: r.steps,
          createdAt: r.createdAt,
        })),
      };
    },
  },
  {
    key: 'short-leave',
    label: 'Short leave',
    icon: Clock,
    permission: 'shortLeave',
    statuses: options(SHORT_LEAVE_STATUS_LABEL),
    fetch: async ({ scope, employeeId, status, filedFrom, filedTo, page, size }, signal) => {
      const res = await (
        scope === 'department'
          ? attendanceApi.shortLeavesDepartment
          : attendanceApi.shortLeavesFiledByMe
      )(
        { status: status as never, filedFrom, filedTo, page, size, ...scoped(scope, employeeId) },
        signal,
      );
      return {
        total: res.totalElements,
        rows: res.content.map(({ request: r, employee }) => ({
          key: `short-leave-${r.id}`,
          shortLeave: { ...r, employee },
          code: r.requestCode,
          person: summaryPerson(employee, r.employeeId),
          detail: `${formatDateShort(r.leaveDate)} · ${hhmm(r.startTime)} – ${hhmm(
            r.endTime,
          )} · ${formatMinutes(r.totalMinutes)}`,
          statusLabel: SHORT_LEAVE_STATUS_LABEL[r.status],
          statusTone: SHORT_LEAVE_STATUS_TONE[r.status],
          rawStatus: r.status,
          currentStage: r.currentStage,
          steps: r.steps,
          createdAt: r.createdAt,
        })),
      };
    },
  },
  {
    key: 'overtime',
    label: 'Overtime',
    icon: Timer,
    permission: 'overtime',
    statuses: options(OVERTIME_STATUS_LABEL),
    fetch: async ({ scope, employeeId, status, filedFrom, filedTo, page, size }, signal) => {
      const res = await (
        scope === 'department' ? attendanceApi.overtimeDepartment : attendanceApi.overtimeFiledByMe
      )(
        { status: status as never, filedFrom, filedTo, page, size, ...scoped(scope, employeeId) },
        signal,
      );
      return {
        total: res.totalElements,
        rows: res.content.map(({ request: r, employee, minutes }) => ({
          key: `overtime-${r.id}`,
          overtime: { ...r, employee, minutes: minutes ?? undefined },
          code: r.requestCode,
          person: summaryPerson(employee, r.employeeId),
          detail: `${formatDateShort(r.workDate)} · ${overtimeTypeLabel(r)}${
            r.factoryCode ? ` · ${r.factoryCode}` : ''
          }${r.bulkBatchId ? ' · bulk' : ''}`,
          statusLabel: OVERTIME_STATUS_LABEL[r.status],
          statusTone: OVERTIME_STATUS_TONE[r.status],
          rawStatus: r.status,
          currentStage: r.currentStage,
          steps: r.steps,
          createdAt: r.createdAt,
        })),
      };
    },
  },
  {
    key: 'regularization',
    label: 'Regularization',
    icon: CalendarCheck2,
    permission: 'regularization',
    statuses: options(REGULARIZATION_STATUS_LABEL),
    fetch: async ({ scope, employeeId, status, filedFrom, filedTo, page, size }, signal) => {
      const res = await (
        scope === 'department'
          ? attendanceApi.regularizationsDepartment
          : attendanceApi.regularizationsFiledByMe
      )(
        { status: status as never, filedFrom, filedTo, page, size, ...scoped(scope, employeeId) },
        signal,
      );
      return {
        total: res.totalElements,
        rows: res.content.map(({ request: r, employee }) => ({
          key: `regularization-${r.id}`,
          code: r.requestCode,
          person: summaryPerson(employee, r.employeeId),
          detail: `${formatDateShort(r.workDate)} · in ${formatClock(
            r.requestedCheckIn,
          )} · out ${formatClock(r.requestedCheckOut)}`,
          statusLabel: REGULARIZATION_STATUS_LABEL[r.status],
          statusTone: REGULARIZATION_STATUS_TONE[r.status],
          rawStatus: r.status,
          currentStage: r.currentStage,
          steps: r.steps,
          createdAt: r.createdAt,
        })),
      };
    },
  },
  {
    key: 'short-term-advance',
    label: 'Short-term advance',
    icon: Coins,
    permission: 'shortTermAdvance',
    statuses: options(ADVANCE_STATUS_LABEL),
    needsRoster: true,
    fetch: async (
      { scope, employeeId, status, filedFrom, filedTo, page, size, roster },
      signal,
    ) => {
      const res = await (
        scope === 'department' ? shortTermAdvancesApi.department : shortTermAdvancesApi.filedByMe
      )(
        { status: status as never, filedFrom, filedTo, page, size, ...scoped(scope, employeeId) },
        signal,
      );
      return {
        total: res.totalElements,
        rows: res.content.map((b) => {
          const lines = b.lines ?? [];
          const only = lines.length === 1 ? lines[0]! : null;
          return {
            key: `short-term-advance-${b.id}`,
            code: b.requestCode ?? `#${b.id}`,
            // One line reads like any other request; a batch lists names.
            person: only ? rosterPerson(roster, only.employeeId) : null,
            people: only
              ? undefined
              : lines.map((l) => rosterPerson(roster, l.employeeId).fullName).join(', ') ||
                `${b.totalEmployees} employees`,
            detail: `${money(b.totalAmount)} · ${b.totalEmployees} ${
              b.totalEmployees === 1 ? 'employee' : 'employees'
            } · deduct ${formatPeriod(b.targetYear, b.targetMonth)}`,
            statusLabel: ADVANCE_STATUS_LABEL[b.status],
            statusTone: ADVANCE_STATUS_TONE[b.status],
            rawStatus: b.status,
            currentStage: b.currentStage,
            steps: (b as typeof b & WithSteps).steps ?? [],
            createdAt: b.createdAt,
          };
        }),
      };
    },
  },
  {
    key: 'long-term-advance',
    label: 'Salary advances',
    icon: Banknote,
    permission: 'longTermAdvance',
    statuses: options(ADVANCE_STATUS_LABEL),
    needsRoster: true,
    fetch: async (
      { scope, employeeId, status, filedFrom, filedTo, page, size, roster },
      signal,
    ) => {
      const res = await (
        scope === 'department' ? permanentAdvancesApi.department : permanentAdvancesApi.filedByMe
      )(
        { status: status as never, filedFrom, filedTo, page, size, ...scoped(scope, employeeId) },
        signal,
      );
      return {
        total: res.totalElements,
        rows: res.content.map((a) => ({
          key: `long-term-advance-${a.id}`,
          code: a.requestCode ?? `#${a.id}`,
          person: rosterPerson(roster, a.employeeId),
          detail: `${money(a.amount)} · ${money(a.monthlyDeduction)} / month`,
          statusLabel: ADVANCE_STATUS_LABEL[a.status],
          statusTone: ADVANCE_STATUS_TONE[a.status],
          rawStatus: a.status,
          currentStage: a.currentStage,
          steps: (a as typeof a & WithSteps).steps ?? [],
          createdAt: a.createdAt,
        })),
      };
    },
  },
];

/** Rows per type the All tab pulls before merging. */
const ALL_PER_TYPE = 50;

/** The All tab over `sources` — the type tabs the caller holds. */
function allTab(sources: TabDef[]): TabDef {
  return {
    key: 'all',
    label: 'All',
    icon: ListChecks,
    permission: null,
    statuses: (Object.keys(PHASE_LABEL) as Phase[]).map((value) => ({
      value,
      label: PHASE_LABEL[value],
    })),
    needsRoster: sources.some((t) => t.needsRoster),
    fetch: async ({ scope, employeeId, filedFrom, filedTo, roster }, signal) => {
      const settled = await Promise.allSettled(
        sources.map((t) =>
          t
            .fetch(
              { scope, employeeId, filedFrom, filedTo, page: 0, size: ALL_PER_TYPE, roster },
              signal,
            )
            .then((res) => res.rows.map((r) => ({ ...r, typeLabel: t.label }))),
        ),
      );
      const firstFailure = settled.find((r) => r.status === 'rejected');
      if (firstFailure && settled.every((r) => r.status === 'rejected')) {
        throw firstFailure.reason;
      }
      const failed = sources.flatMap((t, i) => {
        const r = settled[i]!;
        return r.status === 'rejected'
          ? [{ label: t.label, unavailable: isUnavailable(r.reason) }]
          : [];
      });
      const rows = settled
        .flatMap((r) => (r.status === 'fulfilled' ? r.value : []))
        .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
      return { rows, total: rows.length, failed };
    },
  };
}

function isTab(v: unknown): v is GeneratedTab {
  return v === 'all' || TABS.some((t) => t.key === v);
}

function visibleTabs(ability: AppAbility): TabDef[] {
  const types = TABS.filter((t) => t.permission != null && ability.can(t.permission, TRG_SUBJECT));
  // A single type has nothing to merge, so no All tab then.
  return types.length > 1 ? [allTab(types), ...types] : types;
}

type GeneratedSearch = {
  type?: GeneratedTab;
  status?: string;
  /** Filed-on range, YYYY-MM-DD inclusive. */
  from?: string;
  to?: string;
  /** Absent = the whole department (the default). */
  scope?: 'mine';
  /** Department scope: one colleague. */
  employee?: number;
  page?: number;
};

const isIsoDate = (v: unknown): v is string =>
  typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) && dayjs(v).isValid();

export const Route = createFileRoute('/_app/time/generated-requests')({
  // Gate (2). `_app` has already bounced guests, so a missing snapshot here
  // means the session is mid-bootstrap: let the page render.
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    if (!hasAnyRole([TEAM_REQUEST_ROLE], snap.user.roles)) {
      throw redirect({ to: '/' });
    }
  },
  validateSearch: (search): GeneratedSearch => ({
    type: isTab(search.type) ? search.type : undefined,
    status: typeof search.status === 'string' && search.status ? search.status : undefined,
    from: isIsoDate(search.from) ? search.from : undefined,
    to: isIsoDate(search.to) ? search.to : undefined,
    scope: search.scope === 'mine' ? 'mine' : undefined,
    employee:
      typeof search.employee === 'number' && Number.isInteger(search.employee)
        ? search.employee
        : undefined,
    page: typeof search.page === 'number' && search.page > 1 ? search.page : undefined,
  }),
  component: GeneratedRequestsPage,
});

/* ------------------------------ page ------------------------------ */

const PAGE_SIZE = 20;

function GeneratedRequestsPage() {
  const { token } = useToken();
  const ability = useContext(AbilityContext);
  const navigate = useNavigate({ from: '/time/generated-requests' });
  const search = useSearch({ from: '/_app/time/generated-requests' });

  const tabs = useMemo(() => visibleTabs(ability), [ability]);
  const tab = tabs.find((t) => t.key === search.type) ?? tabs[0] ?? null;
  // A status from another tab's vocabulary (stale link) is ignored.
  const status = tab?.statuses.some((s) => s.value === search.status) ? search.status : undefined;
  const page = search.page ?? 1;

  const [historyOf, setHistoryOf] = useState<GeneratedRow | null>(null);

  const scope: Scope = search.scope === 'mine' ? 'mine' : 'department';
  const employeeId = scope === 'department' ? search.employee : undefined;

  // Advance rows name people by id only — resolve them from the department.
  // The department view also lists it in the employee filter.
  const roster = useDepartmentRoster({ enabled: !!tab?.needsRoster || scope === 'department' });
  const rosterById = useMemo(() => {
    const m = new Map<number, EmployeeDto>();
    for (const e of roster.rows) if (e.id != null) m.set(e.id, e);
    return m;
  }, [roster.rows]);
  const rosterReady = !tab?.needsRoster || !roster.isLoading;

  const isAll = tab?.key === 'all';
  const { from: filedFrom, to: filedTo } = search;
  const listQuery = useQuery({
    // The date range is applied server-side on every tab. The All tab
    // filters by stage and pages client-side, so neither is in its key.
    queryKey: [
      'team-requests',
      'generated',
      tab?.key,
      scope,
      employeeId ?? null,
      filedFrom ?? null,
      filedTo ?? null,
      isAll ? null : status,
      isAll ? null : page,
      rosterById.size,
    ],
    queryFn: ({ signal }) =>
      tab!.fetch(
        {
          scope,
          employeeId,
          status,
          filedFrom,
          filedTo,
          page: page - 1,
          size: PAGE_SIZE,
          roster: rosterById,
        },
        signal,
      ),
    enabled: !!tab && rosterReady,
    // Keep the old page on screen while paging / filtering within a tab, but
    // never show one type's rows under another type's columns.
    placeholderData: (prev, prevQuery) => (prevQuery?.queryKey[2] === tab?.key ? prev : undefined),
  });
  const fetched = listQuery.data?.rows ?? [];
  const filtered = isAll && status ? fetched.filter((r) => phaseOf(r) === status) : fetched;
  const rows = isAll ? filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE) : filtered;
  const total = isAll ? filtered.length : (listQuery.data?.total ?? 0);
  const failed = listQuery.data?.failed ?? [];
  const unavailable = failed.filter((f) => f.unavailable).map((f) => f.label);
  const broken = failed.filter((f) => !f.unavailable).map((f) => f.label);

  // All tab: how many sit in each stage, shown on the stage filter.
  const phaseCounts = useMemo(() => {
    const counts: Record<Phase, number> = {
      'in-progress': 0,
      approved: 0,
      rejected: 0,
      cancelled: 0,
    };
    if (isAll) for (const r of listQuery.data?.rows ?? []) counts[phaseOf(r)] += 1;
    return counts;
  }, [isAll, listQuery.data]);
  const statusOptions =
    tab && isAll
      ? tab.statuses.map((o) => ({
          ...o,
          label: `${o.label} (${phaseCounts[o.value as Phase] ?? 0})`,
        }))
      : (tab?.statuses ?? []);

  const filterActive = !!(status || filedFrom || filedTo || employeeId);
  /** Changing any filter goes back to page 1; the type tab and scope are kept. */
  const setFilters = (
    next: Partial<Pick<GeneratedSearch, 'status' | 'from' | 'to' | 'employee' | 'scope'>>,
  ) =>
    navigate({
      search: (prev) => ({
        type: prev.type,
        status: prev.status,
        from: prev.from,
        to: prev.to,
        scope: prev.scope,
        employee: prev.employee,
        ...next,
      }),
      replace: true,
    });
  const clearFilters = () =>
    navigate({ search: (prev) => ({ type: prev.type, scope: prev.scope }), replace: true });

  const employeeOptions = useMemo(
    () =>
      roster.rows
        .filter((e) => e.id != null)
        .map((e) => ({
          value: e.id!,
          label: e.employeeCode
            ? `${e.fullName ?? ''} · ${e.employeeCode}`
            : (e.fullName ?? `#${e.id}`),
        }))
        .sort((a, b) => a.label.localeCompare(b.label)),
    [roster.rows],
  );

  if (!tab) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--app-space-lg)' }}>
        <PageHeader eyebrow="Time" title="Generated team requests" />
        <EmptyState
          icon={ListChecks}
          title="Nothing is enabled for you here"
          description="Your account has the team-request role but none of the per-type permissions. Ask an administrator to grant the request types you need."
        />
      </div>
    );
  }

  const pagination = {
    current: page,
    pageSize: PAGE_SIZE,
    total,
    hideOnSinglePage: true,
    showSizeChanger: false,
    showTotal: (n: number, [from, to]: [number, number]) =>
      `${from}-${to} of ${n.toLocaleString('en-PK')}`,
    onChange: (next: number) =>
      navigate({
        search: (prev) => ({ ...prev, page: next > 1 ? next : undefined }),
        replace: true,
      }),
  };

  const columns: TableColumnsType<GeneratedRow> = [
    {
      title: 'Request',
      key: 'code',
      width: 150,
      render: (_v, r) => (
        <Space orientation="vertical" size={0}>
          <Text strong style={{ fontVariantNumeric: 'tabular-nums' }}>
            {r.code}
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {r.createdAt ? formatDateTime(r.createdAt) : '—'}
          </Text>
        </Space>
      ),
    },
    ...(isAll
      ? [
          {
            title: 'Type',
            key: 'type',
            width: 140,
            render: (_v: unknown, r: GeneratedRow) => <Text>{r.typeLabel}</Text>,
          },
        ]
      : []),
    {
      title: 'For',
      key: 'person',
      width: 240,
      render: (_v, r) =>
        r.person ? (
          <PersonCell
            person={{
              fullName: r.person.fullName,
              employeeCode: r.person.employeeCode ?? null,
              profilePictureUrl: r.person.profilePictureUrl ?? undefined,
            }}
          />
        ) : (
          <Tooltip title={r.people}>
            <Text style={{ maxWidth: 220 }} ellipsis>
              {r.people}
            </Text>
          </Tooltip>
        ),
    },
    { title: 'Details', key: 'detail', render: (_v, r) => r.detail },
    {
      title: 'Status',
      key: 'status',
      width: 140,
      render: (_v, r) => <StatusPill tone={r.statusTone}>{r.statusLabel}</StatusPill>,
    },
    {
      title: 'Approval progress',
      key: 'stage',
      width: 300,
      render: (_v, r) => <StageTrack row={r} />,
    },
    {
      key: 'history',
      title: '',
      width: 110,
      align: 'right',
      render: (_v, r) => (
        <Button size="small" icon={<History size={13} />} onClick={() => setHistoryOf(r)}>
          History
        </Button>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--app-space-lg)' }}>
      <PageHeader
        eyebrow="Time"
        title="Generated team requests"
        description="Every request in your department — leave, short leave, overtime, regularization and advances — and where each one is in its approval chain. Approvers act on them from their own inbox."
        actions={
          <Space>
            <Link to="/time/team-requests">
              <Button icon={<Send size={14} />}>File a request</Button>
            </Link>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => listQuery.refetch()}
                loading={listQuery.isFetching}
                aria-label="Refresh"
              />
            </Tooltip>
          </Space>
        }
      />

      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
        }}
      >
        <nav
          aria-label="Request types"
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 4,
            padding: 4,
            background: token.colorFillQuaternary,
            borderRadius: 12,
            width: 'fit-content',
            maxWidth: '100%',
          }}
        >
          {tabs.map(({ key, label, icon: Icon }) => {
            const active = key === tab.key;
            return (
              <button
                key={key}
                type="button"
                aria-current={active ? 'page' : undefined}
                // Switching type keeps the date range; statuses differ per type.
                onClick={() =>
                  navigate({
                    search: (prev) => ({
                      type: key,
                      from: prev.from,
                      to: prev.to,
                      scope: prev.scope,
                      employee: prev.employee,
                    }),
                    replace: true,
                  })
                }
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '8px 14px',
                  fontSize: 13,
                  fontWeight: 500,
                  borderRadius: 8,
                  border: 'none',
                  cursor: 'pointer',
                  color: active ? token.colorText : token.colorTextSecondary,
                  background: active ? token.colorBgContainer : 'transparent',
                  boxShadow: active
                    ? `0 1px 0 ${token.colorBorderSecondary}, 0 4px 12px color-mix(in srgb, ${token.colorText} 6%, transparent)`
                    : 'none',
                  transition: 'background 160ms ease, color 160ms ease',
                  whiteSpace: 'nowrap',
                }}
              >
                <Icon size={15} strokeWidth={1.75} />
                {label}
              </button>
            );
          })}
        </nav>
        <Segmented<Scope>
          value={scope}
          options={[
            { value: 'department', label: 'Whole department' },
            { value: 'mine', label: 'Filed by me' },
          ]}
          // The employee filter only exists on the department view.
          onChange={(v) =>
            setFilters({ scope: v === 'mine' ? 'mine' : undefined, employee: undefined })
          }
        />
      </div>

      <Section
        title={tab.label}
        subtitle={`${
          isAll
            ? `${scope === 'department' ? 'Every request in your department' : 'Everything you filed for colleagues'}, newest first — the latest ${ALL_PER_TYPE} of each type in the range.`
            : scope === 'department'
              ? `Every ${tab.label.toLowerCase()} request in your department, whoever filed it.`
              : `${tab.label} requests you filed for colleagues.`
        } Open History to see every approval stage.`}
        actions={
          <Space size={8} wrap>
            {scope === 'department' ? (
              <Select
                size="small"
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="All employees"
                loading={roster.isLoading}
                value={employeeId}
                options={employeeOptions}
                onChange={(v?: number) => setFilters({ employee: v ?? undefined })}
                style={{ width: 220 }}
              />
            ) : null}
            <DatePicker.RangePicker
              size="small"
              allowClear
              format="DD MMM YYYY"
              placeholder={['Filed from', 'Filed to']}
              value={
                filedFrom || filedTo
                  ? [filedFrom ? dayjs(filedFrom) : null, filedTo ? dayjs(filedTo) : null]
                  : null
              }
              allowEmpty={[true, true]}
              disabledDate={(d: Dayjs) => d.isAfter(dayjs(), 'day')}
              presets={[
                { label: 'Today', value: [dayjs(), dayjs()] },
                { label: 'Last 7 days', value: [dayjs().subtract(6, 'day'), dayjs()] },
                { label: 'This month', value: [dayjs().startOf('month'), dayjs()] },
                {
                  label: 'Last month',
                  value: [
                    dayjs().subtract(1, 'month').startOf('month'),
                    dayjs().subtract(1, 'month').endOf('month'),
                  ],
                },
                { label: 'Last 3 months', value: [dayjs().subtract(3, 'month'), dayjs()] },
                { label: 'This year', value: [dayjs().startOf('year'), dayjs()] },
              ]}
              onChange={(range) =>
                setFilters({
                  from: range?.[0]?.format('YYYY-MM-DD'),
                  to: range?.[1]?.format('YYYY-MM-DD'),
                })
              }
            />
            <Select
              size="small"
              allowClear
              placeholder={isAll ? 'Any stage' : 'All statuses'}
              value={status}
              options={statusOptions}
              onChange={(v) => setFilters({ status: v || undefined })}
              style={{ width: 180 }}
            />
            {filterActive ? (
              <Button size="small" type="text" icon={<FilterX size={14} />} onClick={clearFilters}>
                Clear
              </Button>
            ) : null}
          </Space>
        }
      >
        {unavailable.length > 0 ? (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            title={`${unavailable.join(', ')} can't be listed on this server yet`}
            description="The API this app is connected to doesn't have these lists yet — they appear once the latest backend is deployed. Everything else is shown below."
          />
        ) : null}
        {broken.length > 0 ? (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            title={`Couldn't load ${broken.join(', ')}`}
            description="Everything else is shown below. Refresh, or open that type's tab to retry."
          />
        ) : null}
        <RequestState
          loading={listQuery.isLoading || !rosterReady}
          error={listQuery.error ?? undefined}
          empty={!listQuery.isLoading && rosterReady && rows.length === 0}
          errorTitle="Couldn't load your requests"
          onRetry={() => listQuery.refetch()}
          errorFallback={
            isUnavailable(listQuery.error)
              ? () => (
                  <EmptyState
                    icon={tab.icon}
                    title={`${isAll ? 'These lists aren’t' : `${tab.label} isn’t`} available on this server yet`}
                    description="The API this app is connected to doesn't serve the list of requests you filed yet. It appears once the latest backend is deployed."
                  />
                )
              : undefined
          }
          emptyFallback={
            <EmptyState
              icon={tab.icon}
              title={
                filterActive
                  ? 'Nothing matches these filters'
                  : isAll
                    ? 'No requests yet'
                    : `No ${tab.label.toLowerCase()} requests yet`
              }
              description={
                filterActive
                  ? 'Try another date range or status, or clear the filters.'
                  : `${isAll ? 'Requests' : `${tab.label} requests`} you file for colleagues from Team requests will show up here.`
              }
              action={
                filterActive ? (
                  <Button icon={<FilterX size={14} />} onClick={clearFilters}>
                    Clear filters
                  </Button>
                ) : undefined
              }
            />
          }
        >
          {tab.key === 'leave' ? (
            <LeaveTable<TeamLeaveRow>
              data={rows.flatMap((r) => (r.leave ? [r.leave] : []))}
              loading={listQuery.isFetching && !listQuery.isLoading}
              employeeOf={(r) => ({
                fullName: r.employee?.fullName ?? `#${r.employeeId}`,
                employeeCode: r.employee?.employeeCode ?? null,
                profilePictureUrl: r.employee?.profilePictureUrl ?? null,
              })}
              pagination={pagination}
            />
          ) : tab.key === 'overtime' ? (
            <OvertimeTable<OvertimeListRow>
              data={rows.flatMap((r) => (r.overtime ? [r.overtime] : []))}
              loading={listQuery.isFetching && !listQuery.isLoading}
              showMinutes
              employeeOf={(r) => ({
                fullName: r.employee?.fullName ?? `#${r.employeeId}`,
                employeeCode: r.employee?.employeeCode ?? null,
                profilePictureUrl: r.employee?.profilePictureUrl ?? null,
              })}
              pagination={pagination}
            />
          ) : tab.key === 'short-leave' ? (
            <ShortLeaveTable<TeamShortLeaveRow>
              data={rows.flatMap((r) => (r.shortLeave ? [r.shortLeave] : []))}
              loading={listQuery.isFetching && !listQuery.isLoading}
              employeeOf={(r) => ({
                fullName: r.employee?.fullName ?? `#${r.employeeId}`,
                employeeCode: r.employee?.employeeCode ?? null,
                profilePictureUrl: r.employee?.profilePictureUrl ?? null,
              })}
              pagination={pagination}
            />
          ) : (
            <Table<GeneratedRow>
              rowKey="key"
              dataSource={rows}
              columns={columns}
              size="middle"
              scroll={{ x: 'max-content' }}
              loading={listQuery.isFetching && !listQuery.isLoading}
              pagination={pagination}
              onRow={(r) => ({
                onClick: (e) => {
                  if ((e.target as HTMLElement).closest('a, button')) return;
                  setHistoryOf(r);
                },
                style: { cursor: 'pointer' },
              })}
            />
          )}
        </RequestState>
      </Section>

      <RequestHistoryDrawer
        row={historyOf}
        typeLabel={historyOf?.typeLabel ?? tab.label}
        activity={
          historyOf?.shortLeave
            ? { kind: 'short-leave', requestId: historyOf.shortLeave.id }
            : historyOf?.leave
              ? { kind: 'leave', requestId: historyOf.leave.id }
              : historyOf?.overtime
                ? { kind: 'overtime', requestId: historyOf.overtime.id }
                : null
        }
        onClose={() => setHistoryOf(null)}
      />
    </div>
  );
}
