// Topbar layout shell.
//
// Owns the <header> chrome, the responsive layout (mobile menu vs collapse
// toggle, breadcrumb visibility), and the global ⌘K listener. Every
// individual control lives as a peer file in this directory so each one is
// reachable on its own (the IconButton primitive in particular is reused
// across MobileMenuButton, CollapseToggle, and SearchTrigger compact).

import { useEffect } from 'react';
import { ThemeSwitcher } from '@/theme/ThemeSwitcher';
import { NotificationsBell } from '@/notifications/NotificationsBell';
import { CommandPalette } from '../CommandPalette';
import { usePaletteStore } from '../palette-store';
import { AssistantLauncher } from './AssistantLauncher';
import { Breadcrumb } from './Breadcrumb';
import { CollapseToggle } from './CollapseToggle';
import { EnvPill } from './EnvPill';
import { MobileMenuButton } from './MobileMenuButton';
import { SearchTrigger } from './SearchTrigger';
import { UserMenu } from './UserMenu';
import { WorkspaceSwitcher } from './WorkspaceSwitcher';

interface TopbarProps {
  /** Desktop sidebar collapse state. `null` when in mobile mode. */
  collapsed: boolean | null;
  /** Toggle desktop sidebar collapse. `null` when in mobile mode. */
  onToggleCollapse: (() => void) | null;
  /** Open the mobile sidebar drawer. `null` when in desktop mode. */
  onMobileMenuOpen: (() => void) | null;
  /** Whether the viewport is desktop-wide. Drives layout adaptations. */
  isDesktop: boolean;
}

export function Topbar({
  collapsed,
  onToggleCollapse,
  onMobileMenuOpen,
  isDesktop,
}: TopbarProps) {
  const env = (import.meta.env.VITE_ENV ?? 'dev') as 'dev' | 'stg' | 'prod';
  const paletteOpen = usePaletteStore((s) => s.open);
  const setPaletteOpen = usePaletteStore((s) => s.setOpen);
  const togglePalette = usePaletteStore((s) => s.toggle);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        togglePalette();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [togglePalette]);

  return (
    <>
      <header
        style={{
          gridArea: 'topbar',
          height: 56,
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '0 16px',
          background: 'var(--app-topbar-bg)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          borderBottom: '1px solid var(--app-topbar-border)',
          color: 'var(--app-topbar-text)',
          position: 'sticky',
          top: 0,
          zIndex: 10,
        }}
      >
        {isDesktop && collapsed !== null && onToggleCollapse && (
          <>
            <CollapseToggle collapsed={collapsed} onToggle={onToggleCollapse} />
            <TopbarDivider />
          </>
        )}
        {!isDesktop && onMobileMenuOpen && (
          <>
            <MobileMenuButton onOpen={onMobileMenuOpen} />
            <TopbarDivider />
          </>
        )}
        <WorkspaceSwitcher />
        {isDesktop && <TopbarDivider />}
        {isDesktop && <Breadcrumb />}
        <div style={{ flex: 1 }} />
        <SearchTrigger onClick={() => setPaletteOpen(true)} compact={!isDesktop} />
        <AssistantLauncher compact={!isDesktop} />
        {env !== 'prod' && <EnvPill env={env} />}
        <NotificationsBell />
        <ThemeSwitcher />
        <UserMenu compact={!isDesktop} />
      </header>
      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
    </>
  );
}

function TopbarDivider() {
  return (
    <span
      aria-hidden="true"
      style={{
        width: 1,
        height: 20,
        background: 'var(--app-topbar-border)',
        flexShrink: 0,
      }}
    />
  );
}
