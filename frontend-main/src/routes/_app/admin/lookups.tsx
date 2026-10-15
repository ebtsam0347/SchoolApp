// /admin/lookups — layout for the HR lookup catalogs.
//
// One sidebar entry instead of many: tab strip swaps between
// Departments / Sections / Designations / Grades / Skills /
// Qualifications / Cities / Shifts. Each child page owns its
// CatalogShell (or its own scaffold for Shifts) so the page title
// + description still come from the catalog.
//
// Tabs are router links so deep-linking, back/forward, and
// share-link all work without manual sync.

import { createFileRoute, Link, Outlet, redirect, useLocation } from '@tanstack/react-router';
import { theme as antdTheme } from 'antd';
import {
  Award,
  Boxes,
  Clock,
  GraduationCap,
  Layers,
  Map as MapIcon,
  Network,
  ScrollText,
  Sliders,
  Sparkles,
  Tag as TagIcon,
  type LucideIcon,
} from 'lucide-react';

const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/admin/lookups')({
  beforeLoad: ({ location }) => {
    if (location.pathname.replace(/\/$/, '') === '/admin/lookups') {
      throw redirect({ to: '/admin/lookups/departments' });
    }
  },
  component: LookupsLayout,
});

type LookupTab = {
  to:
    | '/admin/lookups/departments'
    | '/admin/lookups/sections'
    | '/admin/lookups/designations'
    | '/admin/lookups/grades'
    | '/admin/lookups/skills'
    | '/admin/lookups/qualifications'
    | '/admin/lookups/cities'
    | '/admin/lookups/shifts'
    | '/admin/lookups/leave-types'
    | '/admin/lookups/religions'
    | '/admin/lookups/belongings'
    | '/admin/lookups/off-cycle-payments'
    | '/admin/lookups/travel-policies'
    | '/admin/lookups/travel-grade-limits'
    // | '/admin/lookups/vehicles' — re-enable with the tab below once routes/_app/admin/lookups/vehicles.tsx exists
    ;
  label: string;
  icon: LucideIcon;
};

const TABS: LookupTab[] = [
  { to: '/admin/lookups/departments', label: 'Departments', icon: Network },
  { to: '/admin/lookups/sections', label: 'Sections', icon: Layers },
  { to: '/admin/lookups/designations', label: 'Designations', icon: TagIcon },
  { to: '/admin/lookups/grades', label: 'Grades', icon: Award },
  { to: '/admin/lookups/skills', label: 'Skills', icon: Sparkles },
  { to: '/admin/lookups/qualifications', label: 'Qualifications', icon: GraduationCap },
  { to: '/admin/lookups/cities', label: 'Cities & Provinces', icon: MapIcon },
  { to: '/admin/lookups/shifts', label: 'Shifts', icon: Clock },
  { to: '/admin/lookups/leave-types', label: 'Leave Types', icon: Clock },
  { to: '/admin/lookups/religions', label: 'Religions', icon: Sparkles },
  { to: '/admin/lookups/belongings', label: 'Assets', icon: Boxes },
  { to: '/admin/lookups/off-cycle-payments', label: 'Payment Category', icon: Sparkles },
  { to: '/admin/lookups/travel-policies', label: 'Travel Policies', icon: ScrollText },
  { to: '/admin/lookups/travel-grade-limits', label: 'Travel Grade Limits', icon: Sliders },
  // Vehicles has no route yet; linking to it fails `tsc -b` (typed routes) and the CI build.
  // { to: '/admin/lookups/vehicles', label: 'Vehicles', icon: Sparkles },
];

function LookupsLayout() {
  const { token } = useToken();
  const location = useLocation();
  const currentPath = location.pathname.replace(/\/$/, '') || '/';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <nav
        aria-label="Lookup catalogs"
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 4,
          padding: 4,
          background: token.colorFillQuaternary,
          borderRadius: 12,
          width: 'fit-content',
          maxWidth: '100%',
        }}
      >
        {TABS.map(({ to, label, icon: Icon }) => {
          const active = currentPath.startsWith(to);
          return (
            <Link
              key={to}
              to={to}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8,
                padding: '8px 14px',
                fontSize: 13,
                fontWeight: 500,
                borderRadius: 8,
                color: active ? token.colorText : token.colorTextSecondary,
                background: active ? token.colorBgContainer : 'transparent',
                boxShadow: active
                  ? `0 1px 0 ${token.colorBorderSecondary}, 0 4px 12px color-mix(in srgb, ${token.colorText} 6%, transparent)`
                  : 'none',
                transition: 'background 160ms ease, color 160ms ease',
                whiteSpace: 'nowrap',
              }}
            >
              <Icon size={15} strokeWidth={1.75} />
              {label}
            </Link>
          );
        })}
      </nav>

      <Outlet />
    </div>
  );
}
