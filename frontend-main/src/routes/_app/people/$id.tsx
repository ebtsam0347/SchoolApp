// /people/$id — employee detail layout.
//
// Single composite read against /bff/employee/{id}: employee + resolved
// labels + active contract + active suspension + exit + warning counts +
// direct-reports preview + last-20 timeline. Child tabs share the cached
// payload via the same queryKey — they don't fan out for facts that landed
// on the strip.
//
// The strip is sticky and collapses to a slim row once scrolled past, so
// the "who am I looking at" anchor stays without eating screen space on
// long tabs. The avatar's tone-tinted ring + an optional CTA next to
// Actions surface the most urgent decision (suspension, exit, probation
// due) so the page drives action, not just shows metadata.

import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createFileRoute,
  Link,
  Outlet,
  useLocation,
  useParams,
} from '@tanstack/react-router';
import {
  Alert,
  theme as antdTheme,
  Button,
  Dropdown,
  Skeleton,
  Tooltip,
  type MenuProps
} from 'antd';
import {
  Activity as ActivityIcon,
  ArrowRight,
  Briefcase,
  CalendarClock,
  Camera,
  FileText,
  IdCard,
  KeyRound,
  LogOut,
  MoreHorizontal,
  Network,
  Package,
  ShieldAlert,
  ShieldCheck,
  ShieldX,
  Undo2,
  User,
  UserMinus,
  UserPlus,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import { useContext, useState } from 'react';

import { AbilityContext, Can, useAuth } from '@/auth';
import { BelongingIssuanceFormDrawer } from '@/belongings';
import { getErrorMessage, isForbidden, isNotFound } from '@/lib';
import {
  detailQueryKey,
  EmployeeHero,
  invalidateEmployeeReads,
  invalidateMyProfilePicture,
  peopleBff,
  ProfilePictureUploader,
  useMyEmployee,
  type EmployeeDetail
} from '@/people';
import {
  EmployeeActionModal,
  type EmployeeActionKey,
} from '@/people/action-modals';
import { IssueWarningModal } from '@/people/disciplinary/WarningsPanel';
import { EmployeePrintMenu } from '@/reports/employee';

const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/people/$id')({
  component: EmployeeDetailLayout,
  staticData: { navKey: 'directory' },
});

type TabDef = {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Optional small count rendered next to the label. */
  countKey?: 'documents';
};

// 7 tabs after the May-2026 consolidation:
//   Profile absorbs identity + background (qualifications/skills/certs/experience).
//   Employment absorbs disciplinary (warnings).
//   Activity is the read-only audit timeline.
const TABS: TabDef[] = [
  { to: '/people/$id', label: 'Overview', icon: IdCard },
  { to: '/people/$id/profile', label: 'Profile', icon: User },
  { to: '/people/$id/employment', label: 'Employment', icon: Briefcase },
  { to: '/people/$id/attendance', label: 'Attendance', icon: CalendarClock },
  { to: '/people/$id/salary', label: 'Salary', icon: Wallet },
  { to: '/people/$id/documents', label: 'Documents', icon: FileText, countKey: 'documents' },
  { to: '/people/$id/activity', label: 'Activity', icon: ActivityIcon },
];

function EmployeeDetailLayout() {
  const { id } = useParams({ from: '/_app/people/$id' });
  const employeeId = Number(id);
  const [activeAction, setActiveAction] = useState<EmployeeActionKey | null>(null);

  const detailQuery = useQuery({
    queryKey: detailQueryKey(employeeId),
    queryFn: ({ signal }) => peopleBff.detail(employeeId, signal),
    enabled: Number.isFinite(employeeId),
  });

  if (detailQuery.isLoading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <Skeleton active avatar paragraph={{ rows: 3 }} />
      </div>
    );
  }

  if (detailQuery.isError) {
    const forbidden = isForbidden(detailQuery.error);
    const notFound = isNotFound(detailQuery.error);
    return (
      <Alert
        type={forbidden ? 'info' : 'error'}
        showIcon
        title={
          notFound
            ? 'Employee not found'
            : forbidden
              ? "You don't have permission to view this employee."
              : "Couldn't load employee"
        }
        description={getErrorMessage(detailQuery.error)}
      />
    );
  }

  const detail = detailQuery.data;
  if (!detail) return null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <DetailStrip detail={detail} onAction={setActiveAction} />
      <TabNav detail={detail} />
      <Outlet />
      {detail.employee ? (
        <EmployeeActionModal
          action={activeAction}
          employee={detail.employee}
          onClose={() => setActiveAction(null)}
        />
      ) : null}
    </div>
  );
}

/* --------------------- Strip --------------------- */

function DetailStrip({
  detail,
  onAction,
}: {
  detail: EmployeeDetail;
  onAction: (action: EmployeeActionKey) => void;
}) {
  const { token } = useToken();
  const { id } = useParams({ from: '/_app/people/$id' });
  const qc = useQueryClient();
  const ability = useContext(AbilityContext);
  const { employeeId: myEmployeeId } = useMyEmployee();
  const [issueOpen, setIssueOpen] = useState(false);
  const [assetIssueOpen, setAssetIssueOpen] = useState(false);
  const [pictureOpen, setPictureOpen] = useState(false);
  // Same ability the Actions kebab is gated on — the by-id picture routes
  // require `employee.write`, so don't offer a button the API would reject.
  const canEditPicture = ability.can('write', 'employee');
  const e = detail.employee;
  const r = detail.resolved;
  const activeProbation = e?.activeProbation;
  const activeSuspension = detail.activeSuspension;
  const exit = detail.exit;
  const employeeId = Number(id);

  // Next-action CTA — surfaces the most urgent decision next to the kebab.
  // Computed here (not in the hero) because the link target is route-typed.
  const probationDays = activeProbation?.daysRemaining;
  const probationOverdue = typeof probationDays === 'number' && probationDays < 0;
  const probationDueSoon = typeof probationDays === 'number' && probationDays >= 0 && probationDays <= 14;
  const nextAction: { label: string; tone: 'error' | 'warning' } | null = (() => {
    if (activeSuspension) return { label: 'Review suspension', tone: 'error' };
    if (exit?.status === 'PENDING') return { label: 'Review exit', tone: 'warning' };
    if (probationOverdue) return { label: 'Probation overdue', tone: 'error' };
    if (probationDueSoon) {
      return {
        label: probationDays === 0 ? 'Probation due today' : `Confirm probation · ${probationDays}d`,
        tone: 'warning',
      };
    }
    return null;
  })();

  return (
    <>
      <EmployeeHero
        employee={e}
        resolved={r}
        activeContract={detail.activeContract}
        activeSuspension={activeSuspension}
        exit={exit}
        tenureDays={detail.tenureDays}
        sticky
        avatarBadge={
          canEditPicture ? (
            <Tooltip title={e?.profilePictureUrl ? 'Change picture' : 'Add a picture'}>
              <Button
                size="small"
                shape="circle"
                aria-label={
                  e?.profilePictureUrl ? 'Change profile picture' : 'Add profile picture'
                }
                icon={<Camera size={13} strokeWidth={1.75} />}
                onClick={() => setPictureOpen(true)}
              />
            </Tooltip>
          ) : null
        }
        actions={
          <>
            {nextAction ? (
              <Link to="/people/$id/employment" params={{ id }}>
                <Button
                  type="primary"
                  danger={nextAction.tone === 'error'}
                  icon={<ArrowRight size={14} strokeWidth={2} />}
                  iconPosition="end"
                  style={
                    nextAction.tone === 'warning'
                      ? {
                          background: token.colorWarning,
                          borderColor: token.colorWarning,
                          color: token.colorTextLightSolid,
                        }
                      : undefined
                  }
                >
                  {nextAction.label}
                </Button>
              </Link>
            ) : null}
            {e?.id ? (
              <Link to="/people/org" search={{ root: e.id, depth: 3 }}>
                <Button
                  icon={<Network size={16} strokeWidth={1.75} />}
                  aria-label="Open in org chart"
                  title="Open in org chart"
                />
              </Link>
            ) : null}
            {e ? <EmployeePrintMenu employee={e} /> : null}
            <ActionsMenu
              detail={detail}
              onAction={onAction}
              setIssueOpen={setIssueOpen}
              setAssetIssueOpen={setAssetIssueOpen}
            />
            {issueOpen ? (
          <IssueWarningModal
            employeeId={employeeId}
            onClose={() => setIssueOpen(false)}
          />
        ) : null}
            <BelongingIssuanceFormDrawer
              open={assetIssueOpen}
              onClose={() => setAssetIssueOpen(false)}
              mode="employee"
              presetEmployeeId={employeeId}
            />
          </>
        }
      />
      {canEditPicture ? (
        <ProfilePictureUploader
          open={pictureOpen}
          onClose={() => setPictureOpen(false)}
          employeeId={employeeId}
          employeeName={e?.fullName}
          currentUrl={e?.profilePictureUrl}
          fallbackInitials={initialsOf(e?.fullName ?? '')}
          // The POST returns the write-side DTO with a null picture URL, so
          // refetch the detail composite rather than patching from it.
          onUploaded={() => {
            invalidateEmployeeReads(qc, employeeId);
            // HR editing their own row from the directory — refresh the
            // chrome that renders the signed-in user's avatar too.
            if (myEmployeeId === employeeId) invalidateMyProfilePicture(qc);
          }}
        />
      ) : null}
    </>
  );
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '—';
  return (parts[0]![0]! + (parts.length > 1 ? parts[parts.length - 1]![0]! : ''))
    .toUpperCase();
}

/* --------------------- Tab nav --------------------- */

function TabNav({ detail }: { detail: EmployeeDetail }) {
  const { token } = useToken();
  const { id } = useParams({ from: '/_app/people/$id' });
  const location = useLocation();
  const currentPath = location.pathname.replace(/\/$/, '') || '/';

  // Counts on tab labels — populated lazily as feature tabs add their own
  // count queries. Empty for now since the only tab with a countKey is
  // Documents and the BFF doesn't surface a doc count yet.
  const counts: Partial<Record<NonNullable<TabDef['countKey']>, number>> = {};
  void detail;

  return (
    <nav
      aria-label="Employee sections"
      style={{
        display: 'flex',
        gap: 4,
        overflowX: 'auto',
        padding: 4,
        background: token.colorFillQuaternary,
        borderRadius: 12,
        width: 'fit-content',
        maxWidth: '100%',
      }}
    >
      {TABS.map(({ to, label, icon: Icon, countKey }) => {
        const resolvedPath = to.replace('$id', id);
        const active =
          to === '/people/$id'
            ? currentPath === resolvedPath
            : currentPath.startsWith(resolvedPath);
        const count = countKey ? counts[countKey] : undefined;
        return (
          <Link
            key={to}
            to={to}
            params={{ id }}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 8,
              padding: '8px 14px',
              fontSize: 13,
              fontWeight: active ? 600 : 500,
              borderRadius: 8,
              color: active ? token.colorPrimary : token.colorTextSecondary,
              background: active
                ? `color-mix(in srgb, ${token.colorPrimary} 14%, ${token.colorBgElevated})`
                : 'transparent',
              boxShadow: active
                ? `inset 0 0 0 1px color-mix(in srgb, ${token.colorPrimary} 32%, transparent), 0 4px 14px color-mix(in srgb, ${token.colorPrimary} 18%, transparent)`
                : 'none',
              transition: 'background 160ms ease, color 160ms ease, box-shadow 160ms ease',
              whiteSpace: 'nowrap',
              textDecoration: 'none',
            }}
          >
            <Icon size={14} strokeWidth={1.75} aria-hidden />
            {label}
            {typeof count === 'number' && count > 0 ? (
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  minWidth: 18,
                  height: 18,
                  padding: '0 5px',
                  borderRadius: 9,
                  background: token.colorErrorBg,
                  color: token.colorError,
                  fontSize: 11,
                  fontWeight: 700,
                }}
              >
                {count}
              </span>
            ) : null}
          </Link>
        );
      })}
    </nav>
  );
}

/* --------------------- Actions menu --------------------- */

function ActionsMenu({
  detail,
  onAction,
  setIssueOpen,
  setAssetIssueOpen,
}: {
  detail: EmployeeDetail;
  onAction: (action: EmployeeActionKey) => void;
  setIssueOpen: (open: boolean) => void;
  setAssetIssueOpen: (open: boolean) => void;
}) {
  const auth = useAuth();
  const e = detail.employee;
  const status = e?.employmentStatus;
  const isProbation = detail.activeContract?.contractType === 'PROBATION';
  const probationExtended = detail.activeContract?.extended === true;
  const hasActiveSuspension = Boolean(detail.activeSuspension);
  const hasPendingExit = detail.exit?.status === 'PENDING';
  const hasEss = e?.hasEss === true;


  // Self-actions guard — never let HR resign / terminate themselves from this UI.
  const isSelf = auth.user?.employeeLinked && auth.user.fullName === e?.fullName;

  // Each action's modal lives in the relevant tab so the contract history
  // sits next to the action that mutates it. The kebab links there with a
  // pre-opened drawer (next slice wires the URL flag).
  const items: MenuProps['items'] = (() => {
    const out: NonNullable<MenuProps['items']> = [];

    if (isProbation && status === 'ACTIVE') {
      out.push({ key: 'confirm', icon: <ShieldCheck size={14} />, label: 'Confirm employee' });
      if (!probationExtended) {
        out.push({
          key: 'extend',
          icon: <CalendarClock size={14} />,
          label: 'Extend probation',
        });
      }
      out.push({ type: 'divider' });
    }

    if (status && status !== 'TERMINATED' && !isSelf) {
      if (status === 'ACTIVE') {
        out.push({ key: 'resign', icon: <LogOut size={14} />, label: 'Submit resignation' });
      }
      if (status === 'ON_NOTICE' || hasPendingExit) {
        out.push({ key: 'withdraw-exit', icon: <Undo2 size={14} />, label: 'Withdraw exit' });
      }
      out.push({
        key: 'terminate',
        icon: <UserMinus size={14} />,
        label: 'Terminate employment',
        danger: true,
      });
      out.push({ key: 'issue-asset', icon: <Package size={14} />, label: 'Allocate asset' });
    }

    if (status === 'ACTIVE' || status === 'ON_NOTICE') {
      out.push({ type: 'divider' });
      if (hasActiveSuspension) {
        out.push({ key: 'lift', icon: <ShieldCheck size={14} />, label: 'Lift suspension' });
      } else {
        out.push({ key: 'suspend', icon: <ShieldX size={14} />, label: 'Suspend' });
      }
    }

    out.push({ type: 'divider' });
    out.push({
      key: 'discipline',
      icon: <ShieldAlert size={14} />,
      label: 'Issue warning',
    });

    out.push({ type: 'divider' });
    if (hasEss) {
      out.push({ key: 'revoke-ess', icon: <KeyRound size={14} />, label: 'Revoke ESS access' });
    } else {
      out.push({ key: 'grant-ess', icon: <UserPlus size={14} />, label: 'Grant ESS access' });
    }

    return out;
  })();

  const handleSelect = ({ key }: { key: string }) => {
    if (key === 'discipline') {
      setIssueOpen(true);
      return;
    }
    if (key === 'issue-asset') {
      setAssetIssueOpen(true);
      return;
    }
    onAction(key as EmployeeActionKey);
  };

  return (
    <Can I="write" a="employee">
      <Dropdown
        menu={{ items, onClick: handleSelect }}
        trigger={['click']}
        placement="bottomRight"
      >
        <Tooltip title="Employee actions">
          <Button icon={<MoreHorizontal size={18} strokeWidth={1.75} />}>
            Actions
          </Button>
        </Tooltip>
      </Dropdown>

    </Can>
  );
}

