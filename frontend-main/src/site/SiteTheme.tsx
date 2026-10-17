// Pins the public website to the Spirit theme, whatever theme a staff member
// picked in the portal. Brand colours come from src/theme/themes/spirit.ts and
// are exposed to site.css as --s-* custom properties.

import { ConfigProvider } from 'antd';
import urPK from 'antd/locale/ur_PK';
import type { CSSProperties, ReactNode } from 'react';
import { spirit, spiritBrand } from '@/theme/themes/spirit';
import { isUrdu } from './i18n';

const t = spirit.antd.token ?? {};

const siteVars = {
  '--s-royal': spiritBrand.royal,
  '--s-bright': spiritBrand.bright,
  '--s-navy': spiritBrand.navy,
  '--s-navy-2': spiritBrand.navy2,
  '--s-gold': spiritBrand.gold,
  '--s-gold-light': spiritBrand.goldLight,
  '--s-bg': t.colorBgLayout,
  '--s-card': t.colorBgContainer,
  '--s-heading': t.colorTextHeading,
  '--s-text': t.colorText,
  '--s-text-2': t.colorTextSecondary,
  '--s-text-3': t.colorTextTertiary,
  '--s-text-4': t.colorTextQuaternary,
  '--s-border': t.colorBorder,
  '--s-border-2': t.colorBorderSecondary,
  '--s-mist': t.colorInfoBg,
  '--s-mist-border': t.colorInfoBorder,
  '--s-success': t.colorSuccess,
  '--s-success-bg': t.colorSuccessBg,
  '--s-error': t.colorError,
  '--s-error-bg': t.colorErrorBg,
} as CSSProperties;

const antdTheme = {
  ...spirit.antd,
  // Own CSS-var scope so it never collides with the portal's ConfigProvider.
  // AntD adds this key as a class on every component, so it must not be
  // "site" (the wrapper class site.css styles).
  cssVar: { key: 'soi-web' },
};

export function SiteTheme({ children }: { children: ReactNode }) {
  return (
    <ConfigProvider theme={antdTheme} direction={isUrdu ? 'rtl' : 'ltr'} locale={isUrdu ? urPK : undefined}>
      <div className="site" style={siteVars}>
        {children}
      </div>
    </ConfigProvider>
  );
}
