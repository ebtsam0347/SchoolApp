import { theme } from 'antd';
import type { ThemeDefinition } from '../tokens';

/**
 * Crest palette — the single source of truth for brand colours. The staff
 * app reads these through the AntD theme below; the public website
 * (src/site) maps them onto its own CSS variables.
 */
export const spiritBrand = {
  royal: '#1D47B8',
  bright: '#2F6BD8',
  navy: '#0C1E52',
  navy2: '#132A68',
  gold: '#D4A93C',
  goldLight: '#E8C25A',
} as const;

/**
 * Spirit — The Spirit of Islam School crest. Default theme.
 * Royal blue shield over deep navy, with gold as punctuation only
 * (sidebar active icon, rules, focus accents) — never a wash.
 */
export const spirit: ThemeDefinition = {
  id: 'spirit',
  label: 'Spirit',
  surface: 'light',
  preview: { brand: spiritBrand.royal, accent: spiritBrand.gold, sidebar: spiritBrand.navy },
  blurb: 'The school crest. Royal blue on deep navy, with a gold sunrise.',
  antd: {
    cssVar: { key: 'fshrms' },
    hashed: false,
    algorithm: theme.defaultAlgorithm,
    token: {
      colorPrimary: spiritBrand.royal,
      colorInfo: spiritBrand.royal,
      colorSuccess: '#15803D',
      colorWarning: '#B45309',
      colorError: '#991B1B',
      // Preset-color tag/alert/badge text+bg+border (see vellum.ts for context).
      colorSuccessText: '#166534',
      colorSuccessBg: '#F0FDF4',
      colorSuccessBorder: '#86EFAC',
      colorWarningText: '#854D0E',
      colorWarningBg: '#FEF3C7',
      colorWarningBorder: '#FCD34D',
      colorErrorText: '#991B1B',
      colorErrorBg: '#FEE2E2',
      colorErrorBorder: '#FCA5A5',
      colorInfoText: '#1E3A8A',
      colorInfoBg: '#DBEAFE',
      colorInfoBorder: '#93C5FD',
      colorBgLayout: '#F4F6FB',
      colorBgContainer: '#FFFFFF',
      colorBgElevated: '#FFFFFF',
      colorBorder: '#DCE2EF',
      colorBorderSecondary: '#EBEFF7',
      colorText: '#0F1A33',
      colorTextSecondary: '#3B4664',
      colorTextTertiary: '#6B7591',
      colorTextQuaternary: '#A3ABC2',
      colorTextHeading: '#0A1430',
      colorTextLabel: '#3B4664',
      colorTextDescription: '#556080',
      borderRadius: 8,
      borderRadiusLG: 12,
      borderRadiusSM: 6,
      fontFamily:
        'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      fontSize: 14,
    },
    components: {
      Layout: { headerBg: '#FFFFFF', headerHeight: 56, siderBg: spiritBrand.navy },
      Menu: { itemHeight: 36, itemBorderRadius: 8 },
      Button: { fontWeight: 600, controlHeight: 36 },
      Card: { paddingLG: 20 },
      Modal: { borderRadiusLG: 12 },
    },
  },
};
