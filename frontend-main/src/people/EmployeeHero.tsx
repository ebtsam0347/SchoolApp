// Shared identity strip used by /people/$id and /me. Owns avatar +
// identity row + reference row + meta footer. Surface chrome (gradient,
// sticky-collapse, action button, kebab) is configured by props so each
// route stays in charge of its own surroundings.
//
// surface='plain' — directory detail look (container bg, body text colors).
// surface='accent' — /me hero look (theme accent gradient, on-accent tokens).

import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Avatar, Tooltip, Typography, theme as antdTheme } from 'antd';
import { Check, Copy } from 'lucide-react';

import { ExpiryBadge, OrgPath, PersonCell, StatusPill } from '@/components';
import { formatDateShort } from '@/lib';
import {
  EMPLOYMENT_STATUS_LABEL,
  EMPLOYMENT_STATUS_TONE,
  EMPLOYMENT_TYPE_LABEL,
  type EmployeeDto,
  type EmployeeResolved,
  type EmploymentContractDto,
  type EssProvisioningStatus,
  type ExitDto,
  type SuspensionDto,
} from './types';

const { useToken } = antdTheme;
const { Title } = Typography;

export type EmployeeHeroProps = {
  employee: EmployeeDto | undefined;
  resolved: EmployeeResolved | undefined;
  activeContract?: EmploymentContractDto;
  /** Active suspension drives the "Suspended" pill, urgency ring, and the
   *  "without pay" suffix on the status pill. Optional — /me's profile
   *  endpoint doesn't surface this. */
  activeSuspension?: SuspensionDto;
  /** Pending exit drives the "Last day" pill and urgency ring. Optional. */
  exit?: ExitDto;
  /** Server-computed tenure (days). Only a fallback — tenure is shown as
   *  calendar years/months/days from `employee.joiningDate` when present. */
  tenureDays?: number;

  /** 'plain' uses container bg + body text colors; 'accent' uses theme
   *  accent gradient + on-accent tokens. Defaults to 'plain'. */
  surface?: 'plain' | 'accent';
  /** When true, attach a sticky sentinel and collapse to a slim row past
   *  the threshold. /people/$id sets this; /me leaves it off. */
  sticky?: boolean;

  /** Slot above the name (e.g. greeting on /me). */
  eyebrow?: ReactNode;
  /** Slot rendered after the meta footer (e.g. today's punch on /me). */
  extras?: ReactNode;
  /** Right-side block (action button + kebab on /people/$id). Permissions
   *  are gated by the caller — the hero just renders what's passed in. */
  actions?: ReactNode;
  /** Badge pinned to the avatar's bottom-right corner (e.g. the "change
   *  picture" button on /me). Suppressed in the collapsed sticky row,
   *  where the avatar is too small to carry it. */
  avatarBadge?: ReactNode;
};

export function EmployeeHero({
  employee,
  resolved,
  activeContract,
  activeSuspension,
  exit,
  tenureDays,
  surface = 'plain',
  sticky = false,
  eyebrow,
  extras,
  actions,
  avatarBadge,
}: EmployeeHeroProps) {
  const { token } = useToken();
  const e = employee;
  const r = resolved;
  const isAccent = surface === 'accent';

  // Pills + urgency are derived from the data — no caller computation needed.
  const statusTone = e?.employmentStatus
    ? EMPLOYMENT_STATUS_TONE[e.employmentStatus]
    : 'neutral';
  const statusLabel = e?.employmentStatus
    ? EMPLOYMENT_STATUS_LABEL[e.employmentStatus]
    : 'Unknown';
  const isSuspendedStatus = e?.employmentStatus === 'SUSPENDED';
  const statusPillLabel =
    isSuspendedStatus && activeSuspension?.withPay === false
      ? `${statusLabel} · without pay`
      : statusLabel;
  const activeProbation = e?.activeProbation;
  const probation =
    activeContract?.contractType === 'PROBATION' ? activeContract : null;

  // Avatar urgency ring — most concerning state wins.
  const probationDays = activeProbation?.daysRemaining;
  const probationOverdue =
    typeof probationDays === 'number' && probationDays < 0;
  const probationDueSoon =
    typeof probationDays === 'number' &&
    probationDays >= 0 &&
    probationDays <= 14;
  const urgencyTone: 'success' | 'warning' | 'error' | 'info' | 'primary' | 'neutral' = (() => {
    if (activeSuspension) return 'error';
    if (exit?.status === 'PENDING') return 'warning';
    if (probationOverdue) return 'error';
    if (probationDueSoon) return 'warning';
    return statusTone;
  })();
  const ringColor = toneToColor(urgencyTone, token);
  // Soft tinted pills don't read on a colored gradient — flip to solid on
  // accent surfaces so the tone color carries the pill instead of fighting
  // the surface behind it.
  const pillVariant: 'soft' | 'solid' = isAccent ? 'solid' : 'soft';

  // Sticky-collapse via scroll position with hysteresis + rAF throttling.
  // Slow scrolls expose two failure modes here: (1) the threshold dead
  // zone must be wider than the collapse-driven height delta, otherwise
  // the layout shift on collapse drops scroll back into the "expand" zone
  // and the page ping-pongs; (2) state updates on every scroll event
  // compete with the browser's scroll thread, so we coalesce to one
  // update per animation frame.
  const sentinelRef = useRef<HTMLDivElement>(null);
  const [compact, setCompact] = useState(false);
  useEffect(() => {
    if (!sticky) return;
    const sentinel = sentinelRef.current;
    if (!sentinel) return;
    let scrollRoot: HTMLElement | Window = window;
    let cursor: HTMLElement | null = sentinel.parentElement;
    while (cursor && cursor !== document.body) {
      const overflow = getComputedStyle(cursor).overflowY;
      if (overflow === 'auto' || overflow === 'scroll') {
        scrollRoot = cursor;
        break;
      }
      cursor = cursor.parentElement;
    }
    let pending = 0;
    const update = () => {
      pending = 0;
      const top =
        scrollRoot === window
          ? window.scrollY
          : (scrollRoot as HTMLElement).scrollTop;
      setCompact((prev) => {
        if (prev && top < 24) return false;
        if (!prev && top > 120) return true;
        return prev;
      });
    };
    const onScroll = () => {
      if (pending) return;
      pending = requestAnimationFrame(update);
    };
    update();
    scrollRoot.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      scrollRoot.removeEventListener('scroll', onScroll);
      if (pending) cancelAnimationFrame(pending);
    };
  }, [sticky]);

  // Employee photo on the profile hero — the largest rendering of it in the
  // app, so it carries the most detail. Bumped alongside the PersonCell and
  // AvatarStack avatars so a face reads clearly rather than as a thumbnail.
  const avatarSize = compact ? 52 : isAccent ? 96 : 92;

  // Surface-aware tokens.
  const fg = isAccent ? 'var(--app-on-accent-fg)' : token.colorTextHeading;
  const fgMuted = isAccent
    ? 'var(--app-on-accent-fg-muted)'
    : token.colorTextSecondary;
  const dividerColor = isAccent
    ? 'var(--app-on-accent-chip-border)'
    : token.colorBorderSecondary;
  const surfaceBg = isAccent
    ? `linear-gradient(135deg, var(--app-accent-grad-from), var(--app-accent-grad-to))`
    : token.colorBgContainer;

  // Calendar years/months/days from the joining date; the server's day count
  // only when there's no joining date (and 0 there means "unknown", so skip).
  const tenure =
    tenureFromJoining(e?.joiningDate) ??
    (tenureDays ? tenureDaysToString(tenureDays) : null);

  const personCellAccentStyles = isAccent
    ? {
        primary: { color: fg, fontWeight: 600 },
        avatar: {
          background: 'var(--app-on-accent-fg)',
          color: 'var(--app-accent-grad-from)',
        },
      }
    : undefined;

  return (
    <>
      {sticky ? <div ref={sentinelRef} style={{ height: 1 }} aria-hidden /> : null}
      <header
        style={{
          position: sticky ? 'sticky' : undefined,
          top: sticky ? 0 : undefined,
          zIndex: sticky ? 10 : undefined,
          display: 'grid',
          gridTemplateColumns: actions
            ? 'auto minmax(0, 1fr) auto'
            : 'auto minmax(0, 1fr)',
          alignItems: 'center',
          gap: compact ? 14 : 24,
          padding: compact ? '10px 16px' : isAccent ? '24px 28px' : '20px 24px',
          background: surfaceBg,
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: compact ? 12 : 16,
          color: fg,
          boxShadow: isAccent
            ? `0 8px 24px color-mix(in srgb, ${token.colorPrimary} 18%, transparent)`
            : compact
              ? `0 4px 16px color-mix(in srgb, ${token.colorText} 8%, transparent), 0 1px 0 ${token.colorBorderSecondary}`
              : `0 1px 0 ${token.colorBorderSecondary}`,
          // No transition on layout properties — collapse must snap atomically
          // so the next scroll event doesn't land mid-reflow and re-toggle.
          transition: 'box-shadow 200ms ease',
        }}
      >
        <div style={{ position: 'relative', flexShrink: 0, lineHeight: 0 }}>
          <Avatar
            size={avatarSize}
            src={e?.profilePictureUrl ?? undefined}
            style={{
              background: isAccent
                ? 'var(--app-on-accent-chip-bg)'
                : hslFromName(e?.fullName ?? 'unknown'),
              color: isAccent
                ? 'var(--app-on-accent-fg)'
                : token.colorTextLightSolid,
              fontSize: compact ? 14 : isAccent ? 28 : 26,
              fontWeight: 600,
              flexShrink: 0,
              boxShadow: isAccent
                ? `0 0 0 3px var(--app-on-accent-chip-border)`
                : ringColor
                  ? `0 6px 18px color-mix(in srgb, ${ringColor} 32%, transparent), 0 0 0 2px color-mix(in srgb, ${ringColor} 38%, transparent)`
                  : undefined,
            }}
          >
            {initialsOf(e?.fullName ?? '—')}
          </Avatar>
          {avatarBadge && !compact ? (
            <div
              style={{
                position: 'absolute',
                right: -2,
                bottom: -2,
                lineHeight: 0,
              }}
            >
              {avatarBadge}
            </div>
          ) : null}
        </div>

        <div
          style={{
            minWidth: 0,
            display: 'flex',
            flexDirection: 'column',
            gap: compact ? 0 : 8,
          }}
        >
          {!compact && eyebrow ? (
            <div style={{ fontSize: 13, fontWeight: 500, color: fgMuted }}>
              {eyebrow}
            </div>
          ) : null}

          {/* Identity row — name + code + status pills */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              flexWrap: 'wrap',
              rowGap: 8,
            }}
          >
            <Title
              level={compact ? 5 : isAccent ? 2 : 3}
              style={{
                margin: 0,
                lineHeight: 1.15,
                color: fg,
                fontSize: compact ? undefined : isAccent ? 26 : undefined,
              }}
            >
              {e?.fullName ?? '—'}
            </Title>
            {e?.employeeCode ? (
              isAccent ? (
                <span style={accentCodeStyle}>{e.employeeCode}</span>
              ) : (
                <CopyableCode code={e.employeeCode} />
              )
            ) : null}
            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: 6,
              }}
            >
              <StatusPill tone={statusTone} variant={pillVariant}>
                {statusPillLabel}
              </StatusPill>
              {e?.employmentType ? (
                <StatusPill tone="neutral" variant={pillVariant}>
                  {EMPLOYMENT_TYPE_LABEL[e.employmentType]}
                </StatusPill>
              ) : null}
              {activeProbation?.endDate ? (
                <ExpiryBadge
                  date={activeProbation.endDate}
                  mode="deadline"
                  variant={pillVariant}
                />
              ) : probation?.endDate ? (
                <ExpiryBadge
                  date={probation.endDate}
                  mode="deadline"
                  variant={pillVariant}
                />
              ) : null}
              {activeSuspension && !isSuspendedStatus ? (
                <StatusPill tone="error" variant={pillVariant}>
                  Suspended
                  {activeSuspension.withPay === false ? ' · without pay' : ''}
                </StatusPill>
              ) : null}
              {exit?.status === 'PENDING' && exit.lastWorkingDay ? (
                <StatusPill tone="warning" variant={pillVariant}>
                  Last day {formatDateShort(exit.lastWorkingDay)}
                </StatusPill>
              ) : null}
              <EssStatusPill
                hasEss={e?.hasEss}
                essProvisioningStatus={e?.essProvisioningStatus}
                variant={pillVariant}
              />
            </div>
          </div>

          {!compact ? (
            <>
              {/* Reference row — role · grade · org · reports-to */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 14,
                  flexWrap: 'wrap',
                  color: fgMuted,
                  fontSize: 13,
                }}
              >
                {r?.designation?.title ? (
                  <span style={{ color: fg, fontWeight: isAccent ? 600 : 500 }}>
                    {r.designation.title}
                  </span>
                ) : null}
                {r?.grade?.name ? <span>{r.grade.name}</span> : null}
                {r?.department?.name || r?.section?.name ? (
                  <>
                    <Divider color={dividerColor} />
                    <OrgPath
                      segments={[
                        r?.department?.name ? { label: r.department.name } : null,
                        r?.section?.name ? { label: r.section.name } : null,
                      ]}
                    />
                  </>
                ) : null}
                {r?.manager?.id ? (
                  <>
                    <Divider color={dividerColor} />
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                    >
                      <span style={{ fontSize: 12, color: fgMuted }}>
                        Reports to
                      </span>
                      <PersonCell
                        size="compact"
                        hideSubtitle
                        person={{
                          fullName: r.manager.fullName ?? '—',
                          employeeCode: r.manager.employeeCode,
                          profilePictureUrl: r.manager.profilePictureUrl,
                        }}
                        to={`/people/${r.manager.id}`}
                        styles={personCellAccentStyles}
                      />
                    </span>
                  </>
                ) : null}
              </div>

              {/* Meta footer — tenure, dates, span-of-control. */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 18,
                  marginTop: 2,
                  color: fgMuted,
                  fontSize: 12,
                  flexWrap: 'wrap',
                }}
              >
                {tenure ? (
                  <span>
                    <strong style={{ color: fg, fontWeight: 600 }}>
                      {tenure}
                    </strong>{' '}
                    tenure
                  </span>
                ) : null}
                {typeof e?.directReportsCount === 'number' &&
                e.directReportsCount > 0 ? (
                  <span>
                    Manages{' '}
                    <strong style={{ color: fg, fontWeight: 600 }}>
                      {e.directReportsCount}
                    </strong>
                  </span>
                ) : null}
                {e?.confirmationDate ? (
                  <span>Confirmed {formatDateShort(e.confirmationDate)}</span>
                ) : null}
                {e?.joiningDate ? (
                  <span>Joined {formatDateShort(e.joiningDate)}</span>
                ) : null}
              </div>

              {extras ? <div style={{ marginTop: 4 }}>{extras}</div> : null}
            </>
          ) : null}
        </div>

        {actions ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {actions}
          </div>
        ) : null}
      </header>
    </>
  );
}

/* -------------------------- helpers -------------------------- */

const accentCodeStyle: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  height: 24,
  padding: '0 10px',
  fontSize: 13,
  fontWeight: 500,
  lineHeight: 1,
  borderRadius: 999,
  color: 'var(--app-on-accent-fg)',
  background: 'var(--app-on-accent-chip-bg)',
  border: '1px solid var(--app-on-accent-chip-border)',
  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
  whiteSpace: 'nowrap',
};

function CopyableCode({ code }: { code: string }) {
  const { token } = useToken();
  const [copied, setCopied] = useState(false);
  const handleCopy = () => {
    void navigator.clipboard?.writeText(code).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    });
  };
  return (
    <Tooltip title={copied ? 'Copied' : 'Click to copy'}>
      <button
        type="button"
        onClick={handleCopy}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          padding: '2px 8px',
          background: 'transparent',
          border: '1px solid transparent',
          borderRadius: 6,
          cursor: 'pointer',
          fontSize: 14,
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
          color: token.colorTextSecondary,
          transition: 'background 160ms ease, border-color 160ms ease',
        }}
        onMouseEnter={(ev) => {
          ev.currentTarget.style.background = token.colorFillQuaternary;
          ev.currentTarget.style.borderColor = token.colorBorderSecondary;
        }}
        onMouseLeave={(ev) => {
          ev.currentTarget.style.background = 'transparent';
          ev.currentTarget.style.borderColor = 'transparent';
        }}
      >
        {code}
        {copied ? (
          <Check size={12} strokeWidth={2} color={token.colorSuccess} />
        ) : (
          <Copy size={12} strokeWidth={1.75} />
        )}
      </button>
    </Tooltip>
  );
}

function Divider({ color }: { color: string }) {
  return (
    <span
      aria-hidden
      style={{ width: 1, height: 14, background: color }}
    />
  );
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '·';
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
  return (parts[0]![0]! + parts[parts.length - 1]![0]!).toUpperCase();
}

function hslFromName(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash << 5) - hash + name.charCodeAt(i);
    hash |= 0;
  }
  return `hsl(${Math.abs(hash) % 360}, 55%, 48%)`;
}

/**
 * Calendar tenure from the joining date to today — real years, months and
 * days ("24y 7m 14d"), borrowing the actual length of the previous month the
 * way a person counts it. A day count can't be turned into months exactly
 * (months are 28–31 days long), so the joining date wins whenever we have it.
 */
function tenureFromJoining(joiningIso?: string | null): string | null {
  // "YYYY-MM-DD" read as a local calendar date — `new Date(iso)` would parse
  // it as UTC midnight and can land on the previous day.
  const [jy, jm, jd] = (joiningIso ?? '').slice(0, 10).split('-').map(Number);
  if (!jy || !jm || !jd) return null;
  const today = new Date();
  let years = today.getFullYear() - jy;
  let months = today.getMonth() + 1 - jm;
  let days = today.getDate() - jd;
  if (days < 0) {
    months -= 1;
    // Days in the month before this one (day 0 of this month = its last day).
    days += new Date(today.getFullYear(), today.getMonth(), 0).getDate();
  }
  if (months < 0) {
    years -= 1;
    months += 12;
  }
  if (years < 0) return null; // joining date in the future
  const parts = [
    years ? `${years}y` : null,
    months ? `${months}m` : null,
    days ? `${days}d` : null,
  ].filter(Boolean);
  return parts.length ? parts.join(' ') : '0d';
}

/** Fallback when there's no joining date — a day count, with months
 *  approximated as 30 days. */
function tenureDaysToString(days: number | undefined): string | null {
  if (days == null) return null;
  if (days < 30) return `${days}d`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months}mo`;
  const years = Math.floor(months / 12);
  const remMonths = months % 12;
  if (remMonths === 0) return `${years}y`;
  return `${years}y ${remMonths}m`;
}

function toneToColor(
  tone: 'success' | 'warning' | 'error' | 'info' | 'primary' | 'neutral',
  token: ReturnType<typeof useToken>['token'],
): string {
  switch (tone) {
    case 'success':
      return token.colorSuccess;
    case 'warning':
      return token.colorWarning;
    case 'error':
      return token.colorError;
    case 'info':
      return token.colorInfo;
    case 'primary':
      return token.colorPrimary;
    default:
      return token.colorPrimary;
  }
}

// Driven by EmployeeDto.essProvisioningStatus. The four states map to:
//   COMPLETED + hasEss     → render nothing (the absence of a pill IS the
//                            success signal; everyone with ESS just looks
//                            normal)
//   NOT_REQUESTED          → neutral "No ESS"
//   RUNNING                → info "ESS provisioning…" (active onboarding saga)
//   FAILED                 → error "ESS provisioning failed"
function EssStatusPill({
  hasEss,
  essProvisioningStatus,
  variant = 'soft',
}: {
  hasEss?: boolean;
  essProvisioningStatus?: EssProvisioningStatus;
  variant?: 'soft' | 'solid';
}) {
  if (essProvisioningStatus === 'RUNNING') {
    return (
      <StatusPill tone="info" variant={variant}>
        ESS provisioning…
      </StatusPill>
    );
  }
  if (essProvisioningStatus === 'FAILED') {
    return (
      <StatusPill tone="error" variant={variant}>
        ESS provisioning failed
      </StatusPill>
    );
  }
  if (hasEss === false) {
    return (
      <StatusPill tone="neutral" variant={variant}>
        No ESS
      </StatusPill>
    );
  }
  return null;
}


