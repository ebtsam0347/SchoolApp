// Small label chip rendered inside a sidebar row to flag freshly-launched
// areas ("NEW"), beta surfaces, or live counts. Two visual variants:
//
//   - `pill` (default) — uppercase tracked label, used in the expanded
//     sidebar where there's room beside the row label.
//   - `dot` — tiny coloured dot, used in the collapsed rail where the
//     row only shows an icon. Mirrors the Apple/Notion pattern: subtle,
//     unobtrusive, but visible enough to draw the eye.
//
// Tones map to AntD theme tokens via inline color values from tokens (we
// can't use `useToken()` here because hex literals are blocked in
// surfaces/. Map tones onto Sidebar CSS vars + a primary fallback.

import type { CSSProperties } from 'react';

export type NavBadgeTone = 'new' | 'beta' | 'count';

export interface NavBadge {
  label: string;
  tone?: NavBadgeTone;
}

/**
 * Tone → CSS-variable mapping. Sidebar is always dark, so the variables
 * resolve to a high-contrast accent regardless of theme.
 */
const TONE_BG: Record<NavBadgeTone, string> = {
  new: 'var(--app-accent-grad-from)',
  beta: 'var(--app-sidebar-text-muted)',
  count: 'var(--app-sidebar-bg-elevated)',
};

const TONE_FG: Record<NavBadgeTone, string> = {
  new: 'var(--app-sidebar-bg)',
  beta: 'var(--app-sidebar-bg)',
  count: 'var(--app-sidebar-text)',
};

export interface NavBadgePillProps {
  badge: NavBadge;
  style?: CSSProperties;
}

export function NavBadgePill({ badge, style }: NavBadgePillProps) {
  const tone = badge.tone ?? 'new';
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 16,
        padding: '0 6px',
        borderRadius: 999,
        background: TONE_BG[tone],
        color: TONE_FG[tone],
        fontSize: 9,
        fontWeight: 700,
        letterSpacing: '0.08em',
        textTransform: 'uppercase',
        flexShrink: 0,
        ...style,
      }}
    >
      {badge.label}
    </span>
  );
}

export interface NavBadgeDotProps {
  tone?: NavBadgeTone;
  style?: CSSProperties;
}

export function NavBadgeDot({ tone = 'new', style }: NavBadgeDotProps) {
  return (
    <span
      aria-hidden
      style={{
        position: 'absolute',
        top: 6,
        right: 8,
        width: 6,
        height: 6,
        borderRadius: '50%',
        background: TONE_BG[tone],
        boxShadow: '0 0 0 2px var(--app-sidebar-bg)',
        ...style,
      }}
    />
  );
}

/**
 * Notification-style count badge — for rows whose number changes per
 * request (e.g. "Approvals Inbox") and so can't live in the static
 * `badge` pill above. Same solid-accent + dark-text treatment as the
 * "new" tone above (guaranteed on-brand contrast per theme), just
 * circular instead of a label pill. Capped at "99+" so it never blows
 * out the row. Renders `null` for 0/undefined — callers don't need to
 * guard.
 */
export interface NavCountBadgeProps {
  count?: number;
  style?: CSSProperties;
}

export function NavCountBadge({ count, style }: NavCountBadgeProps) {
  if (!count) return null;
  const label = count > 99 ? '99+' : String(count);
  return (
    <span
      aria-label={`${count} pending`}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        minWidth: 20,
        height: 20,
        padding: '0 6px',
        borderRadius: 999,
        background: 'var(--app-accent-grad-from)',
        color: 'var(--app-sidebar-bg)',
        fontSize: 11,
        fontWeight: 700,
        fontVariantNumeric: 'tabular-nums',
        lineHeight: 1,
        flexShrink: 0,
        boxShadow: '0 0 0 2px var(--app-sidebar-bg)',
        ...style,
      }}
    >
      {label}
    </span>
  );
}

/** Compact version of {@link NavCountBadge} for the collapsed rail — sits
 *  as a corner overlay on the row icon instead of inline after the label. */
export function NavCountDot({ count }: { count?: number }) {
  if (!count) return null;
  const label = count > 9 ? '9+' : String(count);
  return (
    <span
      aria-label={`${count} pending`}
      style={{
        position: 'absolute',
        top: 2,
        right: 10,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        minWidth: 15,
        height: 15,
        padding: '0 3px',
        borderRadius: 999,
        background: 'var(--app-accent-grad-from)',
        color: 'var(--app-sidebar-bg)',
        fontSize: 9,
        fontWeight: 700,
        lineHeight: 1,
        boxShadow: '0 0 0 2px var(--app-sidebar-bg)',
      }}
    >
      {label}
    </span>
  );
}
