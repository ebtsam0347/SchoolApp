// Public school website layout. Pathless route: its children live at
// /home, /our-philosophy, /admissions, … and need no login.

import { createFileRoute, Outlet, useRouterState } from '@tanstack/react-router';
import { useSiteLanguage } from '@/site/hooks';
import { LanguagePrompt } from '@/site/LanguageChoice';
import { SiteFooter } from '@/site/SiteFooter';
import { SiteHeader } from '@/site/SiteHeader';
import { SiteTheme } from '@/site/SiteTheme';
import { tr } from '@/site/i18n';
import '@/site/site.css';

export const Route = createFileRoute('/_site')({
  component: SiteLayout,
});

function SiteLayout() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  useSiteLanguage();
  return (
    <SiteTheme>
      <a href="#main" className="s-skip">
        {tr('Skip to content')}</a>
      <SiteHeader overDark={pathname === '/home'} />
      <main id="main" tabIndex={-1}>
        <Outlet />
      </main>
      <SiteFooter />
      <LanguagePrompt />
    </SiteTheme>
  );
}
