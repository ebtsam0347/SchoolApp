// /time/team-requests — file requests for colleagues.
//
// Two gates, both keyed off TEAM_REQUEST_GENERATION, and they must agree:
//   1. The sidebar row (`requiresRole` in nav-config.ts).
//   2. `beforeLoad` below, so typing the URL isn't a way around the sidebar.
// Both call the same `hasAnyRole` from @/auth. Neither has an admin bypass —
// managers and admins do not get this page unless they hold the role.
//
// Within the page, each request type is gated on its own granular permission
// (`team.request.generation.<type>`), so a partial grant shows a partial tab
// strip. The auth-service permission verbs are camelCase (`shortLeave`,
// `longTermAdvance`); the CASL builder registers an unrecognized verb as-is,
// so `can('shortLeave', 'team.request.generation')` is the check.
//
// Approvals are NOT here. Filing for a colleague opens a fully PENDING chain
// that lands in that person's own manager's inbox — a request generator is not
// an approver. /inbox owns that queue.
//
// Roster: `useDepartmentRoster` (src/people/department-roster.ts) reads
// `GET /employees/me/department/employees` — permission-free, department
// resolved server-side from the caller. Every tab shares that one list.
//
// The first tab, Profile, is the directory view: the same columns and cards
// as /people/directory (catalog ids resolved client-side by
// `useDepartmentDirectoryRows`), but opening a colleague shows
// `DepartmentEmployeeDrawer` instead of linking to /people/:id, which needs
// `employee.read`. It carries no granular permission of its own — holding
// the role is the gate, same as reaching the page at all. The request tabs
// after it pick a person from the same list and file for them.
//
// The request tabs (Leave, Short leave, Overtime, Regularization, Short-term
// and Long-term advance) use the same table / cards with a basic column set;
// opening a colleague there opens that tab's request drawer. Regularization
// opens the colleague's month calendar first (`ColleagueMonthDrawer`, same
// look as the manager's /time/team Full month view); clicking a day files it.
//
// Caveat on the request tabs: attendance-service resolves `onBehalfOf`
// through employee-service `getByIdOptional` on the caller's token, so
// POST /leaves currently answers 502 UPSTREAM_EMPLOYEE_FAILED for a caller
// without `employee.read`. The roster endpoint does not change that — it's a
// separate fix on the attendance side.

//
// Tabs live in `?type=` (and the table/cards toggle in
// `?view=`) so a view is linkable and survives refresh.

import { useContext, useMemo, useState } from 'react';
import {
  createFileRoute,
  redirect,
  useNavigate,
  useSearch,
} from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Input,
  Pagination,
  Segmented,
  Space,
  Table,
  theme as antdTheme,
} from 'antd';
import {
  Banknote,
  CalendarCheck2,
  CalendarPlus,
  Coins,
  IdCard,
  LayoutGrid,
  Rows3,
  Search,
  Timer,
  Clock,
  Users,
  type LucideIcon,
} from 'lucide-react';

import {
  ColumnsToggle,
  EmptyState,
  PageHeader,
  RequestState,
  Section,
  useMobileRowExpandable,
} from '@/components';
import { AbilityContext, hasAnyRole, type AppAbility } from '@/auth';
import { readSnapshot, useDebounced, useTableColumns } from '@/lib';
import { ApplyLeaveDrawer } from '@/attendance/leaves';
import { ApplyShortLeaveDrawer } from '@/attendance/short-leaves';
import { ApplyOvertimeDrawer, BulkApplyOvertimeDrawer } from '@/attendance/overtime';
import { ColleagueMonthDrawer } from '@/attendance/month';
import {
  ApplyPermanentAdvanceDrawer,
  BulkApplyShortTermAdvanceDrawer,
} from '@/payroll/advances';
import { peopleBff } from '@/people';
import { DepartmentEmployeeDrawer } from '@/people/DepartmentEmployeeDrawer';
import {
  useDepartmentDirectoryRows,
  useDepartmentRoster,
} from '@/people/department-roster';
import {
  DIRECTORY_COLUMN_KEYS,
  DIRECTORY_DEFAULT_HIDDEN,
  DirectoryCardGrid,
  buildDirectoryColumns,
  isView,
  type DirectoryView,
} from '@/people/directory';
import type { EmployeeDirectoryRow, EmployeeDto } from '@/people/types';

const { useToken } = antdTheme;

/** Role that owns this page. Mirrors nav-config's `requiresRole`. */
const TEAM_REQUEST_ROLE = 'TEAM_REQUEST_GENERATION';
/** Subject half of every `team.request.generation.<verb>` permission. */
const TRG_SUBJECT = 'team.request.generation';

/* ------------------------------ tabs ------------------------------ */

type RequestTab =
  | 'profile'
  | 'leave'
  | 'short-leave'
  | 'overtime'
  | 'regularization'
  | 'short-term-advance'
  | 'long-term-advance';

type TabDef = {
  key: RequestTab;
  label: string;
  icon: LucideIcon;
  /**
   * Verb half of this tab's permission, as auth-service spells it. The tab
   * is hidden unless the caller holds `team.request.generation.<permission>`.
   * Null means the tab needs no permission beyond the role that guards the
   * whole page.
   */
  permission: string | null;
  /** Blurb under the section title. */
  blurb: string;
  /**
   * Null when the type is wired end-to-end. Otherwise the reason it isn't —
   * shown in place of the roster so nobody clicks a button that would 403,
   * and so the remaining work is visible rather than silently missing.
   * Every reason below was checked against the live service contracts at
   * `/v3/api-docs/*`, not the committed snapshots.
   */
  blockedReason: string | null;
  /** Row action label, when not blocked. */
  actionLabel?: string;
  /**
   * Label of the header button for this type's bulk (multi-select) flow.
   * Absent when there is none.
   */
  bulkLabel?: string;
};

const TABS: TabDef[] = [
  {
    key: 'profile',
    label: 'Profile',
    icon: IdCard,
    permission: null,
    blurb: 'Everyone in your department. Open a row to see their details.',
    blockedReason: null,
    actionLabel: 'View profile',
  },
  {
    key: 'leave',
    label: 'Leave',
    icon: CalendarPlus,
    permission: 'leave',
    blurb:
      'Apply leave for a colleague — open a row to start. It goes to their own manager for approval.',
    blockedReason: null,
    actionLabel: 'Apply leave',
  },
  {
    key: 'short-leave',
    label: 'Short leave',
    icon: Clock,
    permission: 'shortLeave',
    // attendance-service routes a team-request filing to the *filer's* own
    // manager (OnBehalfResolver TEAM_ON_BEHALF), nothing auto-approved.
    blurb:
      'Apply a short leave (a few hours off on one date) for a colleague — open a row to start. It goes to your manager for approval.',
    blockedReason: null,
    actionLabel: 'Apply short leave',
  },
  {
    key: 'overtime',
    label: 'Overtime',
    icon: Timer,
    permission: 'overtime',
    blurb:
      'Log overtime for one person — open a row to start — or use Bulk apply to file the same shift for several at once.',
    // Bulk apply opens the same drawer a manager gets from /time/overtime,
    // in `ownDepartment` scope — the manager roll-call it normally lists
    // (/bff/attendance/team/today) is empty for a filer who manages nobody.
    // NB: POST /overtime/bulk is documented as `attendance.overtime.bulk`;
    // it must also accept `team.request.generation.overtime` for this to
    // submit.
    blockedReason: null,
    actionLabel: 'Apply overtime',
    bulkLabel: 'Bulk apply',
  },
  {
    key: 'regularization',
    label: 'Regularization',
    icon: CalendarCheck2,
    permission: 'regularization',
    // Opening a row shows that colleague's month (the department-scoped
    // composite, not the manager-only BFF month); a day click files the
    // regularization. attendance-service resolves it as TEAM_ON_BEHALF, so
    // it goes to the filer's manager first, then the usual chain.
    blurb:
      "Regularize a colleague's missed check-in or check-out — open a row to see their month, then click a day. It goes to your manager for approval.",
    blockedReason: null,
    actionLabel: 'Open month',
  },
  {
    key: 'short-term-advance',
    label: 'Short-term advance',
    icon: Coins,
    permission: 'shortTermAdvance',
    blurb:
      'Raise a short-term advance for a colleague — open a row to start — or use New batch for several at once.',
    // Same batch drawer a manager gets from /payroll/short-term-advances'
    // New batch, drawing from the permission-free department roster.
    // NB: the live payroll contract still documents POST
    // /short-term-advance-batches as `payroll.write` or self-only; it must
    // also accept `team.request.generation.shortTermAdvance` for this to
    // submit.
    blockedReason: null,
    actionLabel: 'Apply advance',
    bulkLabel: 'New batch',
  },
  {
    key: 'long-term-advance',
    label: 'Salary advances',
    icon: Banknote,
    permission: 'longTermAdvance',
    blurb:
      'Request a salary advance for a colleague — open a row to start.',
    // Same drawer as /payroll/advances' Request advance, in on-behalf mode.
    // NB: the live payroll contract still documents POST /permanent-advances
    // for anyone but the caller as `payroll.write` only; it must also accept
    // `team.request.generation.longTermAdvance` for this to submit.
    blockedReason: null,
    actionLabel: 'Request advance',
  },
];

function isRequestTab(v: unknown): v is RequestTab {
  return TABS.some((t) => t.key === v);
}

/** Tabs the caller holds the matching granular permission for. */
function visibleTabs(ability: AppAbility): TabDef[] {
  return TABS.filter((t) => t.permission == null || ability.can(t.permission, TRG_SUBJECT));
}

type TeamRequestSearch = { type?: RequestTab; view?: DirectoryView };

export const Route = createFileRoute('/_app/time/team-requests')({
  // Gate (2) — see the header note. `_app` has already bounced guests, so a
  // missing snapshot here means the session is mid-bootstrap: let the page
  // render and leave the redirect to `_app`.
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap?.user) return;
    if (!hasAnyRole([TEAM_REQUEST_ROLE], snap.user.roles)) {
      throw redirect({ to: '/' });
    }
  },
  validateSearch: (search): TeamRequestSearch => ({
    type: isRequestTab(search.type) ? search.type : undefined,
    view: isView(search.view) ? search.view : undefined,
  }),
  component: TeamRequestsPage,
});

/* ------------------------------ page ------------------------------ */

function TeamRequestsPage() {
  const { token } = useToken();
  const ability = useContext(AbilityContext);
  const navigate = useNavigate({ from: '/time/team-requests' });
  const search = useSearch({ from: '/_app/time/team-requests' });

  const tabs = useMemo(() => visibleTabs(ability), [ability]);
  // A `?type=` the caller can't see (link from a colleague with a wider
  // grant, or a permission revoked since the link was made) falls back to
  // the first tab they do hold rather than rendering an empty shell.
  const tab =
    tabs.find((t) => t.key === search.type) ?? tabs[0] ?? null;

  const [q, setQ] = useState('');
  const debouncedQ = useDebounced(q.trim(), 300);
  const [target, setTarget] = useState<{
    kind: RequestTab;
    employee: EmployeeDto;
  } | null>(null);
  const [bulkOpen, setBulkOpen] = useState(false);
  /** Profile tab: the colleague whose details are open. */
  const [profileOf, setProfileOf] = useState<EmployeeDto | null>(null);

  const needsRoster = !!tab && !tab.blockedReason;

  // One department list, shared by every tab.
  const roster = useDepartmentRoster({ q: debouncedQ, enabled: needsRoster });
  const rows = roster.rows;

  // Department name for the subtitle. Same key + staleTime as the sidebar's
  // copy, so it's a cache read in practice; failure is silent.
  const profileQuery = useQuery({
    queryKey: ['account', 'profile'],
    queryFn: ({ signal }) => peopleBff.accountProfile(signal),
    staleTime: 60_000,
    retry: false,
    enabled: needsRoster,
  });
  const department = profileQuery.data?.resolved?.department ?? null;
  const myDepartment = department?.name ?? null;
  const view: DirectoryView = search.view ?? 'table';

  const directoryRows = useDepartmentDirectoryRows(rows, department, {
    enabled: needsRoster,
  });

  if (!tab) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--app-space-lg)' }}>
        <PageHeader eyebrow="Time" title="Team requests" />
        <EmptyState
          icon={Users}
          title="Nothing is enabled for you here"
          description="Your account has the team-request role but none of the per-type permissions. Ask an administrator to grant the request types you need."
        />
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--app-space-lg)' }}>
      <PageHeader
        eyebrow="Time"
        title="Team requests"
        description="File requests for your colleagues. Each one still goes to that person's own manager for approval."
      />

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
              onClick={() => {
                setQ('');
                navigate({
                  search: (prev) => ({ type: key, view: prev.view }),
                  replace: true,
                });
              }}
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

      <Section
        title={tab.label}
        subtitle={
          !tab.blockedReason && myDepartment
            ? `${tab.blurb} Showing ${myDepartment}${
                roster.total ? ` · ${roster.total} people` : ''
              }.`
            : tab.blurb
        }
        actions={
          tab.blockedReason ? undefined : (
            <Space size={8}>
              <Input
                allowClear
                size="small"
                placeholder="Search by name or card no"
                prefix={<Search size={13} strokeWidth={1.75} />}
                value={q}
                onChange={(e) => setQ(e.target.value)}
                style={{ width: 240 }}
              />
              <Segmented<DirectoryView>
                size="small"
                value={view}
                onChange={(v) =>
                  navigate({
                    search: (prev) => ({ ...prev, view: v === 'table' ? undefined : v }),
                    replace: true,
                  })
                }
                options={[
                  {
                    value: 'table',
                    icon: <Rows3 size={14} strokeWidth={1.75} />,
                    label: 'Table',
                  },
                  {
                    value: 'card',
                    icon: <LayoutGrid size={14} strokeWidth={1.75} />,
                    label: 'Cards',
                  },
                ]}
              />
              {tab.bulkLabel ? (
                <Button size="small" type="primary" onClick={() => setBulkOpen(true)}>
                  {tab.bulkLabel}
                </Button>
              ) : null}
            </Space>
          )
        }
      >
        {tab.blockedReason ? (
          <Alert
            type="info"
            showIcon
            title={`${tab.label} isn't available on this page yet`}
            description={tab.blockedReason}
          />
        ) : (
          <>
            <RequestState
              loading={roster.isLoading}
              error={roster.error ?? undefined}
              empty={!roster.isLoading && rows.length === 0}
              errorTitle="Couldn't load your department"
              emptyFallback={
                <EmptyState
                  icon={Users}
                  title={q ? 'No one matches that search' : 'Nobody in your department'}
                  description={
                    q
                      ? 'Try a different name or card number.'
                      : `No active employee is assigned to ${myDepartment ?? 'your department'}. Ask HR to check the department assignments.`
                  }
                />
              }
            >
            {tab.key === 'profile' ? (
              <DepartmentDirectory
                key={tab.key}
                rows={directoryRows}
                view={view}
                onOpen={setProfileOf}
                allColumns={PROFILE_COLUMNS}
                storageKey="team-requests.profile"
                defaultHidden={DIRECTORY_DEFAULT_HIDDEN}
              />
            ) : (
              <DepartmentDirectory
                key={tab.key}
                rows={directoryRows}
                view={view}
                onOpen={(employee) => setTarget({ kind: tab.key, employee })}
                allColumns={REQUEST_COLUMNS}
                storageKey={`team-requests.${tab.key}`}
                defaultHidden={NONE_HIDDEN}
                action={{ label: tab.actionLabel ?? 'File', icon: tab.icon }}
              />
            )}
            </RequestState>
          </>
        )}
      </Section>

      <ApplyLeaveDrawer
        open={target?.kind === 'leave'}
        onClose={() => setTarget(null)}
        onBehalfOf={target?.kind === 'leave' ? toTarget(target.employee) : null}
      />
      <ApplyShortLeaveDrawer
        open={target?.kind === 'short-leave'}
        onClose={() => setTarget(null)}
        onBehalfOf={target?.kind === 'short-leave' ? toTarget(target.employee) : null}
      />
      <ApplyOvertimeDrawer
        open={target?.kind === 'overtime'}
        onClose={() => setTarget(null)}
        onBehalfOf={target?.kind === 'overtime' ? toTarget(target.employee) : null}
      />
      {/* Department scope, not the manager roll-call: a request generator
          manages nobody, so /bff/attendance/team/today would list no one. */}
      <BulkApplyOvertimeDrawer
        open={bulkOpen && tab.key === 'overtime'}
        onClose={() => setBulkOpen(false)}
        scope="ownDepartment"
      />
      <ColleagueMonthDrawer
        employee={
          target?.kind === 'regularization'
            ? toMonthTarget(target.employee)
            : null
        }
        onClose={() => setTarget(null)}
      />
      <ApplyPermanentAdvanceDrawer
        open={target?.kind === 'long-term-advance'}
        onClose={() => setTarget(null)}
        onBehalfOf={
          target?.kind === 'long-term-advance' ? toTarget(target.employee) : null
        }
      />
      {/* One drawer for both entry points: a row (that colleague pre-added)
          and the New batch button (empty). */}
      <BulkApplyShortTermAdvanceDrawer
        open={
          target?.kind === 'short-term-advance' ||
          (bulkOpen && tab.key === 'short-term-advance')
        }
        onClose={() => {
          setTarget(null);
          setBulkOpen(false);
        }}
        scope="ownDepartment"
        initialEmployeeIds={
          target?.kind === 'short-term-advance' && target.employee.id != null
            ? [target.employee.id]
            : undefined
        }
      />
      <DepartmentEmployeeDrawer
        open={profileOf != null}
        employee={profileOf}
        departmentName={myDepartment}
        onClose={() => setProfileOf(null)}
      />
    </div>
  );
}

/* ------------------------- department directory ------------------------- */

/** Unlinked Directory columns (no server sort to round-trip) — built once. */
const PROFILE_COLUMNS = buildDirectoryColumns(undefined, {
  linkProfiles: false,
  sortable: false,
});

/** Request tabs (Leave, Overtime, …): just enough to pick the right person. */
const REQUEST_COLUMN_KEYS: ReadonlyArray<string> = [
  DIRECTORY_COLUMN_KEYS.fullName,
  DIRECTORY_COLUMN_KEYS.designation,
  DIRECTORY_COLUMN_KEYS.org,
  DIRECTORY_COLUMN_KEYS.grade,
  DIRECTORY_COLUMN_KEYS.status,
  DIRECTORY_COLUMN_KEYS.joiningDate,
];
const REQUEST_COLUMNS = PROFILE_COLUMNS.filter((c) =>
  REQUEST_COLUMN_KEYS.includes(String(c.key)),
);
const NONE_HIDDEN: ReadonlyArray<string> = [];

/**
 * Directory-style body for the tabs that list the department roster:
 * /people/directory's table and cards, with a row / card click calling
 * `onOpen` (profile drawer, leave drawer, …). The roster arrives as one
 * list, so paging is client-side.
 */
function DepartmentDirectory({
  rows,
  view,
  onOpen,
  allColumns,
  storageKey,
  defaultHidden,
  action,
}: {
  rows: EmployeeDirectoryRow[];
  view: DirectoryView;
  onOpen: (e: EmployeeDto) => void;
  /** Toggleable columns, in display order. */
  allColumns: typeof PROFILE_COLUMNS;
  /** `useTableColumns` key for this tab's hidden-column prefs. */
  storageKey: string;
  defaultHidden: ReadonlyArray<string>;
  /** Trailing row button doing the same as a row click. Not toggleable. */
  action?: { label: string; icon: LucideIcon };
}) {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const {
    columns: visibleColumns,
    options: columnOptions,
    toggle: toggleColumn,
    reset: resetColumns,
  } = useTableColumns(storageKey, allColumns, { defaultHidden });
  const mobileExpandable = useMobileRowExpandable(allColumns, {
    skipKeys: ['fullName'],
  });
  const ActionIcon = action?.icon;
  const columns: typeof PROFILE_COLUMNS = action
    ? [
        ...visibleColumns,
        {
          key: 'action',
          title: '',
          width: 150,
          align: 'right',
          render: (_v, row) => (
            <Button
              size="small"
              icon={ActionIcon ? <ActionIcon size={13} strokeWidth={1.75} /> : undefined}
              onClick={() => open(row)}
            >
              {action.label}
            </Button>
          ),
        },
      ]
    : visibleColumns;

  // A search that shrinks the list can leave `page` past the end.
  const lastPage = Math.max(1, Math.ceil(rows.length / size));
  const current = Math.min(page, lastPage);
  // The id is what every request drawer files against — `toTarget` returns
  // null without one, which would hand the drawer a null `onBehalfOf` and
  // quietly file the request against the person doing the filing. Better to
  // not open at all.
  const open = (row: EmployeeDirectoryRow) => {
    if (row.employee?.id != null) onOpen(row.employee);
  };
  const showTotal = (n: number, [from, to]: [number, number]) =>
    `${from}-${to} of ${n.toLocaleString('en-PK')}`;
  const changePage = (next: number, nextSize: number) => {
    setPage(next);
    setSize(nextSize);
  };

  if (view === 'card') {
    const pageRows = rows.slice((current - 1) * size, current * size);
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <DirectoryCardGrid rows={pageRows} loading={false} onOpen={open} />
        {rows.length > size ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Pagination
              current={current}
              pageSize={size}
              total={rows.length}
              showSizeChanger
              pageSizeOptions={['12', '24', '48', '96']}
              showTotal={showTotal}
              onChange={changePage}
            />
          </div>
        ) : null}
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <ColumnsToggle
          size="small"
          options={columnOptions}
          onToggle={toggleColumn}
          onReset={resetColumns}
        />
      </div>
      <Table<EmployeeDirectoryRow>
        rowKey={(row) => String(row.employee?.id)}
        dataSource={rows}
        columns={columns}
        expandable={mobileExpandable}
        size="middle"
        scroll={{ x: 'max-content' }}
        pagination={{
          current,
          pageSize: size,
          total: rows.length,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal,
          hideOnSinglePage: true,
          onChange: changePage,
        }}
        onRow={(row) => ({
          onClick: (e) => {
            if ((e.target as HTMLElement).closest('a, button')) return;
            open(row);
          },
          style: { cursor: 'pointer' },
        })}
      />
    </div>
  );
}

/** `EmployeeDto` types every field as optional; the drawers need a concrete
 *  id and name. A row with no id can't be filed for at all. */
function toTarget(
  e: EmployeeDto,
): { id: number; fullName: string; employeeCode?: string | null } | null {
  if (e.id == null) return null;
  return {
    id: e.id,
    fullName: e.fullName ?? `#${e.id}`,
    employeeCode: e.employeeCode ?? null,
  };
}

/** `toTarget` plus the avatar, for the Regularization month drawer's header. */
function toMonthTarget(e: EmployeeDto) {
  const t = toTarget(e);
  return t ? { ...t, profilePictureUrl: e.profilePictureUrl ?? null } : null;
}
