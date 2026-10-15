// Sidebar layout shell.
//
// Owns:
//   - the <aside> chrome (width animation, background, border)
//   - the BrandMark header + SidebarFooter
//   - up/down ArrowKey nav across visible rows
//   - ability-aware nav-group filtering
//
// Everything else lives as peer files in this directory:
//   SidebarRow / SubMenu / FlyoutPanel / ActiveBar / BrandMark /
//   SidebarFooter, plus the pure helpers in nav-ability.ts.

import { useContext, useRef, type KeyboardEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AbilityContext, useAuth, type AppAbility } from '@/auth';
import { peopleBff } from '@/people';
import { navGroupsForWorkspace, type NavItem } from '../nav-config';
import { useWorkspaceStore } from '../workspace-store';
import { SidebarFooter } from './SidebarFooter';
import { SidebarRow } from './SidebarRow';
import { SidebarSection } from './SidebarSection';
import { BrandMark } from './BrandMark';
import { filterByAbility } from './nav-ability';
import { useApprovalsInboxTotal } from './useApprovalsInboxTotal';

interface SidebarProps {
  collapsed: boolean;
  activeKey: string;
  onSelect: (key: string) => void;
  /** 'rail' is the inline desktop sidebar (default); 'drawer' renders inside the mobile Drawer. */
  variant?: 'rail' | 'drawer';
}

export function Sidebar({
  collapsed,
  activeKey,
  onSelect,
  variant = 'rail',
}: SidebarProps) {
  const navRef = useRef<HTMLElement>(null);
  const ability = useContext(AbilityContext) as AppAbility;
  const auth = useAuth();
  const employeeLinked = auth.user?.employeeLinked === true;
  const workspace = useWorkspaceStore((s) => s.workspace);

  // directReportsCount is fetched via the same query key + staleTime as
  // useDashboardPersona — the two callers share the cache so we don't
  // double-hit /bff/account/profile per render.
  const profile = useQuery({
    queryKey: ['account', 'profile'],
    queryFn: ({ signal }) => peopleBff.accountProfile(signal),
    staleTime: 60_000,
    enabled: employeeLinked,
    retry: false,
  });
  const hasDirectReports =
    (profile.data?.employee?.directReportsCount ?? 0) > 0;

  const pendingApprovalsTotal = useApprovalsInboxTotal();

  const visibleGroups = navGroupsForWorkspace(workspace)
    .map((g) => ({
      ...g,
      items: filterByAbility(g.items, ability, {
        employeeLinked,
        hasDirectReports,
        roles: auth.user?.roles,
      }).map((item) =>
        item.key === 'inbox' && pendingApprovalsTotal
          ? { ...item, countSuffix: pendingApprovalsTotal }
          : item,
      ),
    }))
    .filter((g) => g.items.length > 0);

  const onNavKeyDown = (e: KeyboardEvent<HTMLElement>) => {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    const buttons = Array.from(
      navRef.current?.querySelectorAll<HTMLButtonElement>('button[data-nav-row]') ?? [],
    ).filter((b) => !b.disabled && !b.closest('[data-section-collapsed="true"]'));
    if (buttons.length === 0) return;
    const current = document.activeElement;
    const idx = buttons.findIndex((b) => b === current);
    e.preventDefault();
    const next =
      e.key === 'ArrowDown'
        ? buttons[(idx + 1 + buttons.length) % buttons.length]
        : buttons[(idx - 1 + buttons.length) % buttons.length];
    next?.focus();
  };

  const isDrawer = variant === 'drawer';

  return (
    <aside
      style={{
        gridArea: isDrawer ? undefined : 'sidebar',
        width: isDrawer ? '100%' : collapsed ? 72 : 256,
        height: isDrawer ? '100%' : undefined,
        background: 'var(--app-sidebar-bg)',
        color: 'var(--app-sidebar-text)',
        borderRight: isDrawer ? 'none' : '1px solid var(--app-sidebar-border)',
        display: 'flex',
        flexDirection: 'column',
        transition: 'width var(--app-transition-normal)',
        overflow: 'hidden',
      }}
    >
      <BrandMark collapsed={collapsed} />
      <nav
        ref={navRef}
        className="no-scrollbar"
        onKeyDown={onNavKeyDown}
        style={{ flex: 1, overflowY: 'auto', padding: '8px 12px' }}
      >
        {visibleGroups.map((g) =>
          collapsed ? (
            // Rail layout: no group headers to fold, so render rows flat.
            <ul
              key={g.key}
              style={{
                listStyle: 'none',
                margin: '0 0 16px',
                padding: 0,
                display: 'flex',
                flexDirection: 'column',
                gap: 2,
              }}
            >
              {g.items.map((item: NavItem) => (
                <SidebarRow
                  key={item.key}
                  item={item}
                  collapsed={collapsed}
                  activeKey={activeKey}
                  onSelect={onSelect}
                />
              ))}
            </ul>
          ) : (
            <SidebarSection
              key={g.key}
              group={g}
              activeKey={activeKey}
              onSelect={onSelect}
            />
          ),
        )}
      </nav>
      <SidebarFooter collapsed={collapsed} />
    </aside>
  );
}
