import { useEffect, useState, type ReactNode } from 'react';
import { Drawer, theme as antdTheme } from 'antd';
import { useLocation, useNavigate, useMatches } from '@tanstack/react-router';
import { Sidebar } from './shell/Sidebar';
import { Topbar } from './shell/Topbar';
import { Footer } from './shell/Footer';
import { useIsDesktop } from '@/lib';
import { navGroupsForWorkspace, type NavItem } from './shell/nav-config';
import { useWorkspaceStore } from './shell/workspace-store';

const { useToken } = antdTheme;

function flattenItems(items: NavItem[]): NavItem[] {
  return items.flatMap((i) => (i.children ? [i, ...flattenItems(i.children)] : [i]));
}

/**
 * Visually-hidden skip link that becomes visible on keyboard focus. Lets
 * keyboard users jump past the sidebar / topbar straight to the page outlet.
 */
function SkipToMainContent() {
  const { token } = useToken();
  return (
    <a
      href="#main-content"
      onClick={(e) => {
        // Default jump leaves the URL with #main-content on every page; we
        // just want focus to move to <main>.
        e.preventDefault();
        const main = document.getElementById('main-content');
        if (main) {
          main.focus();
          main.scrollIntoView();
        }
      }}
      style={{
        position: 'fixed',
        top: 8,
        left: 8,
        zIndex: 2000,
        padding: '8px 14px',
        background: token.colorPrimary,
        color: token.colorTextLightSolid,
        borderRadius: token.borderRadius,
        textDecoration: 'none',
        fontSize: 13,
        fontWeight: 600,
        boxShadow: token.boxShadow,
        transform: 'translateY(-150%)',
        transition: 'transform 160ms ease',
      }}
      onFocus={(e) => {
        e.currentTarget.style.transform = 'translateY(0)';
      }}
      onBlur={(e) => {
        e.currentTarget.style.transform = 'translateY(-150%)';
      }}
    >
      Skip to main content
    </a>
  );
}

/**
 * Pick the nav item whose `to` is the longest prefix of the current path —
 * keeps `/admin/audit/replay` highlighting the Replay child rather than the
 * Audit parent.
 *
 * A route can override this by declaring `staticData: { navKey: '<key>' }` —
 * useful when the URL hierarchy diverges from the nav hierarchy (e.g.
 * `/people/$id` belongs under Directory, not the `/people` overview).
 */
function activeKeyFromPath(
  pathname: string,
  staticNavKey: string | undefined,
  groups: ReturnType<typeof navGroupsForWorkspace>,
): string {
  const all = groups.flatMap((g) => flattenItems(g.items)).filter((i) => i.to);
  if (staticNavKey && all.some((i) => i.key === staticNavKey)) return staticNavKey;
  let best: NavItem | null = null;
  for (const item of all) {
    const to = item.to!;
    const matches = to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(`${to}/`);
    if (matches && (!best || item.to!.length > best.to!.length)) best = item;
  }
  return best?.key ?? all[0]?.key ?? '';
}

export function AppShell({ children }: { children: ReactNode }) {
  const isDesktop = useIsDesktop();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const matches = useMatches();
  const { token } = useToken();
  const workspace = useWorkspaceStore((s) => s.workspace);
  const navGroupsForActive = navGroupsForWorkspace(workspace);

  // Deepest match wins — child routes can override their parent's navKey.
  const navKeyOverride = [...matches]
    .reverse()
    .map((m) => (m.staticData as { navKey?: string } | undefined)?.navKey)
    .find((k): k is string => !!k);
  const activeKey = activeKeyFromPath(
    location.pathname,
    navKeyOverride,
    navGroupsForActive,
  );

  const onSelect = (key: string) => {
    const all = navGroupsForActive.flatMap((g) => flattenItems(g.items));
    const target = all.find((i) => i.key === key);
    if (target?.to) {
      void navigate({ to: target.to });
    }
  };

  useEffect(() => {
    if (isDesktop && mobileOpen) setMobileOpen(false);
  }, [isDesktop, mobileOpen]);

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: isDesktop ? 'auto 1fr' : '1fr',
        gridTemplateRows: 'auto 1fr auto',
        gridTemplateAreas: isDesktop
          ? `"sidebar topbar"
             "sidebar main"
             "sidebar footer"`
          : `"topbar"
             "main"
             "footer"`,
        height: '100dvh',
        background: token.colorBgLayout,
        color: token.colorText,
      }}
    >
      <SkipToMainContent />
      {isDesktop && (
        <Sidebar
          collapsed={collapsed}
          activeKey={activeKey}
          onSelect={onSelect}
        />
      )}
      <Topbar
        isDesktop={isDesktop}
        collapsed={isDesktop ? collapsed : null}
        onToggleCollapse={isDesktop ? () => setCollapsed((v) => !v) : null}
        onMobileMenuOpen={!isDesktop ? () => setMobileOpen(true) : null}
      />
      <main
        id="main-content"
        tabIndex={-1}
        style={{
          gridArea: 'main',
          overflow: 'auto',
          minWidth: 0,
          padding: isDesktop ? 24 : 16,
          outline: 'none',
        }}
      >
        {children}
      </main>
      <Footer />
      {!isDesktop && (
        <Drawer
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          placement="left"
          size={272}
          closable={false}
          styles={{
            body: { padding: 0, background: 'var(--app-sidebar-bg)' },
            header: { display: 'none' },
            wrapper: { boxShadow: 'none' },
          }}
        >
          <Sidebar
            collapsed={false}
            activeKey={activeKey}
            onSelect={(key) => {
              onSelect(key);
              setMobileOpen(false);
            }}
            variant="drawer"
          />
        </Drawer>
      )}
    </div>
  );
}
