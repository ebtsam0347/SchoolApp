// Public school website layout. Pathless route: its children live at
// /home, /our-philosophy, /admissions, … and need no login.

import { createFileRoute, Outlet, useRouterState } from '@tanstack/react-router';
import { SiteFooter } from '@/site/SiteFooter';
import { SiteHeader } from '@/site/SiteHeader';
import { SiteTheme } from '@/site/SiteTheme';
import '@/site/site.css';

export const Route = createFileRoute('/_site')({
  component: SiteLayout,
});

function SiteLayout() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  return (
    <SiteTheme>
      <a href="#main" className="s-skip">
        Skip to content
      </a>
      <SiteHeader overDark={pathname === '/home'} />
      <main id="main" tabIndex={-1}>
        <Outlet />
      </main>
      <SiteFooter />
    </SiteTheme>
  );
}
