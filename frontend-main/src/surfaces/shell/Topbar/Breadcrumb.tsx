// Route-driven breadcrumb. Trail is built from `nav-config` so labels stay in
// sync with the sidebar; segments past the deepest matching nav entry are
// humanized so dynamic detail routes (e.g. /people/$id/employment) still read.

import { useLocation, useNavigate } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { ChevronRight } from 'lucide-react';
import { navGroups, type NavGroup, type NavItem } from '../nav-config';
import type { EmployeeDetail } from '@/people/types';

interface Crumb {
  label: string;
  to?: string;
}

export function Breadcrumb() {
  const location = useLocation();
  const navigate = useNavigate();
  // Subscribe to the people-detail cache so the breadcrumb re-renders
  // when the employee name lands. The queryFn is a no-op rejection (it
  // never fires because `enabled: false` short-circuits) — we only
  // observe whatever the page-level layout populates. The noop fn is
  // there to keep React Query's "no queryFn" warning quiet.
  const personMatch = location.pathname.match(/^\/people\/(\d+)(\/|$)/);
  const personId = personMatch ? Number(personMatch[1]) : null;
  const personQuery = useQuery<EmployeeDetail>({
    queryKey: ['people', 'detail', personId ?? -1],
    queryFn: () => Promise.reject(new Error('breadcrumb subscriber')),
    enabled: false,
  });
  const personName = personQuery.data?.employee?.fullName ?? null;
  const trail = enrichTrail(buildTrail(location.pathname), personName);

  if (trail.length === 0) return null;

  return (
    <nav
      aria-label="Breadcrumb"
      style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, minWidth: 0 }}
    >
      {trail.map((crumb, i) => {
        const isLast = i === trail.length - 1;
        const muted = !isLast;
        return (
          <span key={`${crumb.label}-${i}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            {i > 0 && (
              <ChevronRight
                size={14}
                strokeWidth={1.75}
                color="var(--app-topbar-text-muted)"
                aria-hidden="true"
              />
            )}
            {crumb.to && !isLast ? (
              <button
                type="button"
                onClick={() => void navigate({ to: crumb.to as never })}
                style={{
                  background: 'transparent',
                  border: 'none',
                  padding: 0,
                  cursor: 'pointer',
                  fontSize: 13,
                  color: 'var(--app-topbar-text-muted)',
                  fontFamily: 'inherit',
                }}
              >
                {crumb.label}
              </button>
            ) : (
              <span
                aria-current={isLast ? 'page' : undefined}
                style={{
                  color: muted ? 'var(--app-topbar-text-muted)' : undefined,
                  fontWeight: isLast ? 600 : 400,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  maxWidth: 240,
                }}
              >
                {crumb.label}
              </span>
            )}
          </span>
        );
      })}
    </nav>
  );
}

function buildTrail(rawPath: string): Crumb[] {
  const pathname = rawPath.replace(/\/+$/, '') || '/';
  const match = findDeepestNavMatch(pathname);

  if (match) {
    const { group, parent, leaf, exact } = match;
    const trail: Crumb[] = [{ label: group.label }];
    if (parent) trail.push({ label: parent.label });

    if (exact) {
      trail.push({ label: leaf.label, to: leaf.to });
      return trail;
    }

    // Prefix-only match: leaf doesn't represent the current page (its `to`
    // is just the closest nav ancestor). Make it a clickable crumb and append
    // humanized extras for the remaining path segments.
    trail.push({ label: leaf.label, to: leaf.to });
    const extra = pathname.slice(leaf.to!.length).split('/').filter(Boolean);
    for (const seg of extra) trail.push({ label: humanizeSegment(seg) });
    return trail;
  }

  // No nav match — fall back to humanized path segments.
  return pathname
    .split('/')
    .filter(Boolean)
    .map((seg) => ({ label: humanizeSegment(seg) }));
}

interface NavMatch {
  group: NavGroup;
  parent: NavItem | null;
  leaf: NavItem & { to: string };
  /** True when `pathname === leaf.to`. False for prefix-only matches. */
  exact: boolean;
}

function findDeepestNavMatch(pathname: string): NavMatch | null {
  let best: NavMatch | null = null;

  const visit = (group: NavGroup, parent: NavItem | null, item: NavItem) => {
    if (item.to) {
      const to = item.to;
      const exact = pathname === to;
      const prefix = to !== '/' && pathname.startsWith(`${to}/`);
      if (exact || prefix) {
        if (!best || to.length > best.leaf.to.length) {
          best = { group, parent, leaf: item as NavItem & { to: string }, exact };
        }
      }
    }
    if (item.children) {
      for (const child of item.children) visit(group, item, child);
    }
  };

  for (const group of navGroups) {
    for (const item of group.items) visit(group, null, item);
  }

  return best;
}

function humanizeSegment(seg: string): string {
  // Numeric / UUID-ish ids → "Detail" so /people/123 reads as "… / Detail"
  // rather than "… / 123". Anything else → title-case the kebab.
  if (/^\d+$/.test(seg)) return 'Detail';
  if (/^[0-9a-f]{8}-[0-9a-f]{4}-/i.test(seg)) return 'Detail';
  return seg
    .split('-')
    .filter(Boolean)
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(' ');
}

/** Replace the generic "Detail" crumb on `/people/:id` paths with the
 *  employee's actual name when it's already in the React Query cache.
 *  No fetch — just opportunistic enrichment so the breadcrumb identifies
 *  the page instead of saying "Detail". */
function enrichTrail(trail: Crumb[], personName: string | null): Crumb[] {
  if (!personName) return trail;
  // The "Detail" crumb is the one that came from humanizeSegment for the
  // numeric id segment. Replace its label in-place; later segments stay.
  return trail.map((c) => (c.label === 'Detail' ? { ...c, label: personName } : c));
}
