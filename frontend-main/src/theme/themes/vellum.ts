import { theme } from 'antd';
import type { ThemeDefinition } from '../tokens';

/**
 * Vellum — editorial-newsprint. Default theme.
 * Reads like Monocle magazine. Vermillion ink on warm cream.
 */
export const vellum: ThemeDefinition = {
  id: 'vellum',
  label: 'Vellum',
  surface: 'light',
  preview: { brand: '#9A2D2A', accent: '#0F766E', sidebar: '#1A1714' },
  blurb: 'Editorial-newsprint. Vermillion ink on warm cream.',
  antd: {
    cssVar: { key: 'fshrms' },
    hashed: false,
    algorithm: theme.defaultAlgorithm,
    token: {
      colorPrimary: '#9A2D2A',
      colorInfo: '#0F766E',
      colorSuccess: '#15803D',
      colorWarning: '#B45309',
      colorError: '#991B1B',
      // Preset-color tag/alert/badge text+bg+border. AntD's algorithm derives
      // these from the seed and produces low-contrast pairings on light themes
      // (e.g. sage text on sage-grey bg). Pin them to explicit high-contrast
      // tints so every `<Tag color="success|warning|error|info">` reads cleanly.
      colorSuccessText: '#166534',
      colorSuccessBg: '#F0FDF4',
      colorSuccessBorder: '#86EFAC',
      colorWarningText: '#854D0E',
      colorWarningBg: '#FEF3C7',
      colorWarningBorder: '#FCD34D',
      colorErrorText: '#991B1B',
      colorErrorBg: '#FEE2E2',
      colorErrorBorder: '#FCA5A5',
      colorInfoText: '#155E75',
      colorInfoBg: '#CFFAFE',
      colorInfoBorder: '#67E8F9',
      colorBgLayout: '#F8F4ED',
      colorBgContainer: '#FCFAF5',
      colorBgElevated: '#FFFFFF',
      colorBorder: '#E5DDD0',
      colorBorderSecondary: '#EFE9DC',
      colorText: '#1A1714',
      colorTextSecondary: '#4A413A',
      colorTextTertiary: '#8C8175',
      colorTextQuaternary: '#B8AE9F',
      colorTextHeading: '#0F0C0A',
      colorTextLabel: '#4A413A',
      colorTextDescription: '#6B6157',
      borderRadius: 8,
      borderRadiusLG: 12,
      borderRadiusSM: 6,
      fontFamily:
        'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      fontSize: 14,
    },
    components: {
      Layout: { headerBg: '#FCFAF5', headerHeight: 56, siderBg: '#1A1714' },
      Menu: { itemHeight: 36, itemBorderRadius: 8 },
      Button: { fontWeight: 500, controlHeight: 36 },
      Card: { paddingLG: 20 },
      Modal: { borderRadiusLG: 16 },
    },
  },
};
