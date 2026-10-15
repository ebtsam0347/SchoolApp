// /me — canonical self-view for the signed-in employee.
//
// /account is auth-side (sessions, security, delegations, notifications).
// /me is employee-side: the HR record HR sees on /people/$id, but framed
// as "you, looking at yourself" — a personalized hero, employment summary,
// manager + reports, and a row of quick links to the surfaces a typical
// employee uses every day (payslips, attendance, leaves, documents).
//
// Single composite read against /bff/account/profile resolves the
// employee, catalog labels, active contract, team and probation deadline
// in one round-trip.

import { createFileRoute, Link, redirect } from '@tanstack/react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  Alert,
  Card,
  Empty,
  Skeleton,
  Tag,
  Tooltip,
  theme as antdTheme,
} from 'antd';
import {
  ArrowRight,
  Briefcase,
  CalendarClock,
  Camera,
  ExternalLink,
  FileText,
  Inbox,
  KeyRound,
  Lock,
  Mail,
  MapPin,
  Package,
  Phone,
  Plane,
  Receipt,
  ShieldCheck,
  UserCheck,
  Wallet,
  type LucideIcon,
} from 'lucide-react';

import {
  ExpiryBadge,
  KpiGrid,
  PersonCell,
  StatCard,
  StatusPill,
} from '@/components';
import { useAuth } from '@/auth';
import { formatDateShort, getErrorMessage, isForbidden, isNotFound, readSnapshot } from '@/lib';
import {
  CONTRACT_TYPE_LABEL,
  EmployeeHero,
  EMPLOYMENT_TYPE_LABEL,
  formatCityWithProvince,
  invalidateEmployeeReads,
  invalidateMyProfilePicture,
  peopleBff,
  ProfilePictureUploader,
} from '@/people';
import {
  approvalsBff,
  attendanceBff,
  formatClock,
  formatMinutes,
  holidaysApi,
  leavesApi,
  prettyLeaveCode,
  type AttendanceTodayResponse,
} from '@/attendance';

const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/me')({
  // Standalone (non-employee-linked) users land on /account instead — the
  // self-view of a system user is auth-side, not HR-side.
  beforeLoad: () => {
    const snap = readSnapshot();
    if (snap?.user && snap.user.employeeLinked === false) {
      throw redirect({ to: '/account' });
    }
  },
  component: MePage,
});

function MePage() {
  const auth = useAuth();
  const profileQuery = useQuery({
    queryKey: ['bff', 'account', 'profile'],
    queryFn: ({ signal }) => peopleBff.accountProfile(signal),
  });
  const todayQuery = useQuery({
    queryKey: ['bff', 'attendance', 'today'],
    queryFn: ({ signal }) => attendanceBff.today(signal),
    staleTime: 30_000,
  });

  if (profileQuery.isLoading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <Skeleton active avatar paragraph={{ rows: 3 }} />
        <Skeleton active paragraph={{ rows: 6 }} />
      </div>
    );
  }

  if (profileQuery.isError) {
    const forbidden = isForbidden(profileQuery.error);
    const notFound = isNotFound(profileQuery.error);
    return (
      <Alert
        type={forbidden || notFound ? 'info' : 'error'}
        showIcon
        title={
          notFound
            ? "We couldn't find your employee record."
            : forbidden
              ? "You don't have permission to view your employee record."
              : "Couldn't load your profile"
        }
        description={
          notFound
            ? 'Ask HR to link your account to your employee record.'
            : getErrorMessage(profileQuery.error)
        }
      />
    );
  }

  const profile = profileQuery.data;
  if (!profile) return null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <Hero
        profile={profile}
        fallbackName={auth.user?.fullName ?? null}
        today={todayQuery.data ?? null}
      />
      <SelfStats />
      <QuickActions />
      <SummaryGrid profile={profile} />
    </div>
  );
}

/* --------------------------- Self stats --------------------------- */

function SelfStats() {
  const year = new Date().getFullYear();

  const balancesQuery = useQuery({
    queryKey: ['leaves', 'me', 'balances', 'active'],
    queryFn: ({ signal }) => leavesApi.myBalances(undefined, signal),
    staleTime: 60_000,
  });
  const inboxQuery = useQuery({
    queryKey: ['bff', 'approvals', 'inbox'],
    queryFn: ({ signal }) => approvalsBff.inbox(signal),
    staleTime: 30_000,
  });
  const holidaysQuery = useQuery({
    queryKey: ['holidays', year],
    queryFn: ({ signal }) => holidaysApi.list(year, signal),
    staleTime: 24 * 60 * 60 * 1000,
  });

  const balances = balancesQuery.data ?? [];
  const annual = balances.find((b) => b.leaveTypeCode === 'ANNUAL') ?? balances[0];
  const totalAvailable = balances.reduce((s, b) => s + b.available, 0);

  const pendingMine = inboxQuery.data?.counts.total ?? 0;

  const today = new Date().toISOString().slice(0, 10);
  const upcomingHoliday = (holidaysQuery.data ?? [])
    .filter((h) => (h.holidayDate ?? '') >= today)
    .sort((a, b) => (a.holidayDate ?? '').localeCompare(b.holidayDate ?? ''))[0];

  const isLoading = balancesQuery.isLoading || inboxQuery.isLoading || holidaysQuery.isLoading;

  return (
    <KpiGrid minColumnWidth={220}>
      <StatCard
        variant="accent-corner"
        tone="success"
        accentCorner="tr"
        icon={Plane}
        label={annual ? `${prettyLeaveCode(annual.leaveTypeCode)} balance` : 'Leave balance'}
        value={isLoading ? '…' : annual ? `${annual.available}d` : `${totalAvailable}d`}
        delta={
          annual
            ? `${annual.used + annual.pending} used of ${annual.allocated + annual.carried}`
            : balances.length > 0
              ? `Across ${balances.length} types`
              : 'No balance set'
        }
      />
      <StatCard
        variant="plain"
        tone={pendingMine > 0 ? 'warning' : 'neutral'}
        icon={Inbox}
        label="Approvals waiting on me"
        value={isLoading ? '…' : `${pendingMine}`}
        delta={
          pendingMine === 0
            ? 'Inbox is clear'
            : `${inboxQuery.data?.counts.leaves ?? 0} leaves · ${inboxQuery.data?.counts.regularization ?? 0} regs`
        }
      />
      <StatCard
        variant="plain"
        tone="info"
        icon={CalendarClock}
        label="Next holiday"
        value={
          isLoading
            ? '…'
            : upcomingHoliday?.holidayDate
              ? formatDateShort(upcomingHoliday.holidayDate)
              : 'None upcoming'
        }
        delta={upcomingHoliday?.name ?? undefined}
      />
    </KpiGrid>
  );
}

/* ----------------------------- Hero ------------------------------ */

function Hero({
  profile,
  fallbackName,
  today,
}: {
  profile: NonNullable<Awaited<ReturnType<typeof peopleBff.accountProfile>>>;
  fallbackName: string | null;
  today: AttendanceTodayResponse | null;
}) {
  const { token } = useToken();
  const qc = useQueryClient();
  const [pictureOpen, setPictureOpen] = useState(false);
  const e = profile.employee;
  const fullName = e?.fullName ?? fallbackName ?? '—';
  const firstName = fullName.split(/\s+/)[0] ?? fullName;
  const greeting = greetingFor(new Date());

  const todayLabel = describeToday(today, {
    success: token.colorSuccess,
    warning: token.colorWarning,
    info: token.colorInfo,
    muted: token.colorTextTertiary,
  });

  return (
    <>
      <EmployeeHero
        employee={e}
        resolved={profile.resolved}
        activeContract={profile.activeContract}
        surface="accent"
        eyebrow={`${greeting}, ${firstName}`}
        avatarBadge={
          <Tooltip title={e?.profilePictureUrl ? 'Change picture' : 'Add a picture'}>
            <button
              type="button"
              aria-label={e?.profilePictureUrl ? 'Change profile picture' : 'Add profile picture'}
              onClick={() => setPictureOpen(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 30,
                height: 30,
                borderRadius: '50%',
                cursor: 'pointer',
                padding: 0,
                background: 'var(--app-on-accent-chip-bg)',
                border: '1px solid var(--app-on-accent-chip-border)',
                color: 'var(--app-on-accent-fg)',
              }}
            >
              <Camera size={14} strokeWidth={1.75} />
            </button>
          </Tooltip>
        }
        extras={
          todayLabel ? (
            <TodayPill label={todayLabel} fallbackPulseColor={token.colorSuccess} />
          ) : null
        }
        actions={
          e?.id ? (
            <Link
              to="/people/$id"
              params={{ id: String(e.id) }}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                padding: '6px 12px',
                borderRadius: 8,
                background: 'var(--app-on-accent-chip-bg)',
                border: '1px solid var(--app-on-accent-chip-border)',
                fontSize: 13,
                fontWeight: 500,
                color: 'var(--app-on-accent-fg)',
                textDecoration: 'none',
                whiteSpace: 'nowrap',
              }}
            >
              View full profile
            </Link>
          ) : null
        }
      />
      <ProfilePictureUploader
        open={pictureOpen}
        onClose={() => setPictureOpen(false)}
        currentUrl={e?.profilePictureUrl}
        fallbackInitials={initialsOf(fullName)}
        // Shared helper so the hero, the topbar pill and the /account
        // stripe all refresh off one upload.
        onUploaded={() => {
          invalidateMyProfilePicture(qc);
          if (e?.id) invalidateEmployeeReads(qc, e.id);
        }}
      />
    </>
  );
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '—';
  return (parts[0]![0]! + (parts.length > 1 ? parts[parts.length - 1]![0]! : ''))
    .toUpperCase();
}

function TodayPill({
  label,
  fallbackPulseColor,
}: {
  label: TodayLabel;
  fallbackPulseColor: string;
}) {
  return (
    <Link
      to="/time/today"
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        alignSelf: 'flex-start',
        padding: '4px 10px',
        borderRadius: 999,
        background: 'var(--app-on-accent-chip-bg)',
        border: '1px solid var(--app-on-accent-chip-border)',
        fontSize: 12,
        fontWeight: 500,
        color: 'var(--app-on-accent-fg)',
        textDecoration: 'none',
      }}
    >
      <span
        aria-hidden
        style={{
          width: 8,
          height: 8,
          borderRadius: 999,
          background: label.dotColor ?? 'var(--app-on-accent-fg)',
          boxShadow: label.pulse
            ? `0 0 0 4px color-mix(in srgb, ${label.dotColor ?? fallbackPulseColor} 25%, transparent)`
            : undefined,
        }}
      />
      {label.text}
    </Link>
  );
}

type TodayLabel = { text: string; dotColor?: string; pulse?: boolean };
type TodayDots = { success: string; warning: string; info: string; muted: string };

function describeToday(
  t: AttendanceTodayResponse | null,
  dots: TodayDots,
): TodayLabel | null {
  if (!t) return null;
  if (t.activeLeave) {
    const code = t.activeLeave.leaveTypeCode
      ? prettyLeaveCode(t.activeLeave.leaveTypeCode)
      : 'Leave';
    return { text: `On leave · ${code}`, dotColor: dots.warning };
  }
  if (t.todayHoliday) {
    return {
      text: `Holiday · ${t.todayHoliday.name ?? 'Today'}`,
      dotColor: dots.info,
    };
  }
  const punches = t.punches ?? [];
  if (punches.length === 0) return { text: 'Not punched in yet', dotColor: dots.muted };
  const isOddIn = punches.length % 2 === 1;
  if (isOddIn && t.today?.checkIn) {
    const elapsed = (t.today.workedMinutes ?? 0) > 0 ? formatMinutes(t.today.workedMinutes) : null;
    return {
      text: elapsed
        ? `In since ${formatClock(t.today.checkIn)} · ${elapsed}`
        : `In since ${formatClock(t.today.checkIn)}`,
      dotColor: dots.success,
      pulse: true,
    };
  }
  if (t.today?.workedMinutes) {
    return {
      text: `Out · ${formatMinutes(t.today.workedMinutes)} today`,
      dotColor: dots.muted,
    };
  }
  return { text: 'Out for the day', dotColor: dots.muted };
}


/* ------------------------- Quick actions ------------------------- */

const QUICK_LINKS: Array<{
  label: string;
  description: string;
  to: string;
  icon: LucideIcon;
}> = [
  {
    label: 'My attendance',
    description: 'Punches, hours, and shift assignments',
    to: '/time/attendance',
    icon: CalendarClock,
  },
  {
    label: 'My payslips',
    description: 'Salary history and downloadable payslips',
    to: '/payroll/payslips',
    icon: Receipt,
  },
  {
    label: 'Leaves',
    description: 'Apply for leave and see balances',
    to: '/time/leaves',
    icon: Plane,
  },
  {
    label: 'Allocated Assets',
    description: 'Company issued allocated assets to you',
    to: '/me/belongings',
    icon: Package,
  },
  {
    label: 'Approvals inbox',
    description: 'Items waiting on your decision',
    to: '/inbox',
    icon: Inbox,
  },
  {
    label: 'Account & security',
    description: 'Password, sessions, delegations',
    to: '/account/security',
    icon: KeyRound,
  },
  {
    label: 'Delegations',
    description: 'Hand off your approvals while you are away',
    to: '/account/delegations',
    icon: UserCheck,
  },
];

function QuickActions() {
  const { token } = useToken();
  return (
    <section
      aria-label="Quick links"
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
        gap: 12,
      }}
    >
      {QUICK_LINKS.map((q) => (
        <Link
          key={q.to}
          to={q.to}
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 12,
            padding: '14px 16px',
            background: token.colorBgContainer,
            border: `1px solid ${token.colorBorderSecondary}`,
            borderRadius: 12,
            color: token.colorText,
            textDecoration: 'none',
            transition: 'transform 200ms ease, box-shadow 200ms ease, border-color 160ms ease',
          }}
          onMouseEnter={(ev) => {
            ev.currentTarget.style.transform = 'translateY(-2px)';
            ev.currentTarget.style.boxShadow = `0 6px 18px color-mix(in srgb, ${token.colorText} 8%, transparent)`;
            ev.currentTarget.style.borderColor = `color-mix(in srgb, ${token.colorPrimary} 30%, ${token.colorBorderSecondary})`;
          }}
          onMouseLeave={(ev) => {
            ev.currentTarget.style.transform = '';
            ev.currentTarget.style.boxShadow = '';
            ev.currentTarget.style.borderColor = token.colorBorderSecondary;
          }}
        >
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 36,
              height: 36,
              borderRadius: 10,
              background: `color-mix(in srgb, ${token.colorPrimary} 12%, transparent)`,
              color: token.colorPrimary,
              flexShrink: 0,
            }}
          >
            <q.icon size={18} strokeWidth={1.75} />
          </span>
          <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                fontSize: 14,
                fontWeight: 600,
                color: token.colorTextHeading,
              }}
            >
              {q.label}
              <ArrowRight
                size={13}
                strokeWidth={2}
                style={{ color: token.colorTextTertiary }}
              />
            </span>
            <span style={{ fontSize: 12, color: token.colorTextSecondary }}>
              {q.description}
            </span>
          </span>
        </Link>
      ))}
    </section>
  );
}

/* --------------------------- Summary ----------------------------- */

function SummaryGrid({
  profile,
}: {
  profile: NonNullable<Awaited<ReturnType<typeof peopleBff.accountProfile>>>;
}) {
  const { token } = useToken();
  const e = profile.employee;
  const r = profile.resolved;
  const contract = profile.activeContract;

  const cityLabel = r?.city?.name
    ? formatCityWithProvince(r.city.name, r.city.provinceCode)
    : null;

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
        gap: 16,
        alignItems: 'start',
      }}
    >
      <Card
        size="small"
        title={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <Briefcase size={15} strokeWidth={1.75} />
            <span style={{ fontWeight: 600 }}>Employment</span>
          </span>
        }
      >
        <DefList>
          <DefRow label="Designation" value={r?.designation?.title} />
          <DefRow label="Grade" value={r?.grade?.name} />
          <DefRow label="Department" value={r?.department?.name} />
          <DefRow label="Section" value={r?.section?.name} />
          <DefRow
            label="Type"
            value={e?.employmentType ? EMPLOYMENT_TYPE_LABEL[e.employmentType] : null}
          />
          <DefRow
            label="Joined"
            value={e?.joiningDate ? formatDateShort(e.joiningDate) : null}
          />
          {e?.confirmationDate ? (
            <DefRow label="Confirmed" value={formatDateShort(e.confirmationDate)} />
          ) : null}
        </DefList>
      </Card>

      <Card
        size="small"
        title={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <Phone size={15} strokeWidth={1.75} />
            <span style={{ fontWeight: 600 }}>Contact</span>
          </span>
        }
      >
        <DefList>
          <DefRow icon={Phone} label="Phone" value={e?.phone} mono />
          <DefRow icon={Mail} label="Work email" value={e?.workEmail} mono />
          <DefRow icon={Mail} label="Personal email" value={e?.personalEmail} mono />
          <DefRow icon={MapPin} label="City" value={cityLabel} />
          <DefRow label="Permanent address" value={e?.permanentAddress} />
          {e?.currentAddress && e.currentAddress !== e.permanentAddress ? (
            <DefRow label="Current address" value={e.currentAddress} />
          ) : null}
        </DefList>
      </Card>

      <Card
        size="small"
        title={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <ShieldCheck size={15} strokeWidth={1.75} />
            <span style={{ fontWeight: 600 }}>Contract</span>
          </span>
        }
        extra={
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              fontSize: 12,
              color: token.colorTextSecondary,
            }}
          >
            <Lock size={13} strokeWidth={1.75} />
            HR maintained
          </span>
        }
      >
        {contract ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '4px 10px',
                  background: token.colorFillQuaternary,
                  border: `1px solid ${token.colorBorderSecondary}`,
                  borderRadius: 999,
                  fontSize: 12,
                  fontWeight: 500,
                  color: token.colorTextHeading,
                }}
              >
                <ShieldCheck size={14} color={token.colorPrimary} />
                {contract.contractType
                  ? CONTRACT_TYPE_LABEL[contract.contractType]
                  : 'Contract'}
              </span>
              {contract.extended ? <Tag color="warning">Extended</Tag> : null}
              {contract.documentUrl ? (
                <a
                  href={contract.documentUrl}
                  target="_blank"
                  rel="noreferrer"
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                    fontSize: 12,
                  }}
                >
                  <FileText size={14} /> View signed PDF
                  <ExternalLink size={12} />
                </a>
              ) : null}
            </div>
            <DefList>
              <DefRow
                label="Started"
                value={contract.startDate ? formatDateShort(contract.startDate) : null}
              />
              {profile.upcomingProbationDeadline ? (
                <DefRow
                  icon={CalendarClock}
                  label="Probation due"
                  value={
                    <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
                      {formatDateShort(profile.upcomingProbationDeadline)}
                      <ExpiryBadge
                        date={profile.upcomingProbationDeadline}
                        mode="deadline"
                      />
                    </span>
                  }
                />
              ) : contract.endDate ? (
                <DefRow
                  label="Ends"
                  value={
                    <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
                      {formatDateShort(contract.endDate)}
                      <ExpiryBadge date={contract.endDate} mode="expiry" />
                    </span>
                  }
                />
              ) : null}
              <DefRow
                label="Status"
                value={
                  contract.status ? (
                    <StatusPill tone="info">{contract.status}</StatusPill>
                  ) : null
                }
              />
            </DefList>
          </div>
        ) : (
          <Empty
            description="No active contract on file"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        )}
      </Card>

      <Card
        size="small"
        title={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <Wallet size={15} strokeWidth={1.75} />
            <span style={{ fontWeight: 600 }}>
              Direct reports
              {profile.team?.length ? ` · ${profile.team.length}` : ''}
            </span>
          </span>
        }
      >
        {(profile.team?.length ?? 0) === 0 ? (
          <Empty description="No reports" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {profile.team!.slice(0, 8).map((r) => (
              <div key={r.id} style={{ padding: '6px 0' }}>
                <PersonCell
                  size="compact"
                  person={{
                    fullName: r.fullName ?? '—',
                    employeeCode: r.employeeCode,
                    profilePictureUrl: r.profilePictureUrl,
                  }}
                  trailing={
                    r.directReportsCount && r.directReportsCount > 0 ? (
                      <Tag style={{ margin: 0 }}>
                        {r.directReportsCount} {r.directReportsCount === 1 ? 'report' : 'reports'}
                      </Tag>
                    ) : null
                  }
                />
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}

/* ---------------------------- helpers ---------------------------- */

function DefList({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>{children}</div>
  );
}

function DefRow({
  icon: Icon,
  label,
  value,
  mono,
}: {
  icon?: LucideIcon;
  label: string;
  value?: React.ReactNode;
  mono?: boolean;
}) {
  const { token } = useToken();
  if (value === undefined || value === null || value === '') return null;
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '140px 1fr',
        alignItems: 'baseline',
        gap: 12,
        padding: '4px 0',
      }}
    >
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          fontSize: 12,
          color: token.colorTextSecondary,
        }}
      >
        {Icon ? <Icon size={14} strokeWidth={1.75} aria-hidden /> : null}
        {label}
      </span>
      <span
        style={{
          fontSize: 13,
          color: token.colorTextHeading,
          fontFamily: mono ? 'ui-monospace, SFMono-Regular, Menlo, monospace' : undefined,
          wordBreak: 'break-word',
        }}
      >
        {value}
      </span>
    </div>
  );
}

function greetingFor(d: Date): string {
  const h = d.getHours();
  if (h < 5) return 'Working late';
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  if (h < 21) return 'Good evening';
  return 'Good night';
}
