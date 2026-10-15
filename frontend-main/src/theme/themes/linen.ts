import { theme } from 'antd';
import type { ThemeDefinition } from '../tokens';

/**
 * Linen — Nordic soft. Hay/Muuto product catalog energy.
 * Oat surface, dusty cobalt primary, clay rust accent, warm bistre sidebar.
 */
export const linen: ThemeDefinition = {
  id: 'linen',
  label: 'Linen',
  surface: 'light',
  preview: { brand: '#1E3A8A', accent: '#B45309', sidebar: '#1F1B16' },
  blurb: 'Nordic soft. Oat surface, cobalt + clay rust, warm bistre sidebar.',
  antd: {
    cssVar: { key: 'fshrms' },
    hashed: false,
    algorithm: theme.defaultAlgorithm,
    token: {
      colorPrimary: '#1E3A8A',
      colorInfo: '#1E3A8A',
      colorSuccess: '#166534',
      colorWarning: '#B45309',
      colorError: '#9F1239',
      // Preset-color tag/alert/badge text+bg+border (see vellum.ts for context).
      colorSuccessText: '#166534',
      colorSuccessBg: '#F0FDF4',
      colorSuccessBorder: '#86EFAC',
      colorWarningText: '#854D0E',
      colorWarningBg: '#FEF3C7',
      colorWarningBorder: '#FCD34D',
      colorErrorText: '#9F1239',
      colorErrorBg: '#FFE4E6',
      colorErrorBorder: '#FDA4AF',
      colorInfoText: '#1E3A8A',
      colorInfoBg: '#DBEAFE',
      colorInfoBorder: '#93C5FD',
      colorBgLayout: '#F1ECE3',
      colorBgContainer: '#FBF7EF',
      colorBgElevated: '#FFFFFF',
      colorBorder: '#DDD4C4',
      colorBorderSecondary: '#E8E0D2',
      colorText: '#2B2926',
      colorTextSecondary: '#5C544A',
      colorTextTertiary: '#8E8478',
      colorTextQuaternary: '#B8AE9F',
      colorTextHeading: '#1F1D1A',
      colorTextLabel: '#5C544A',
      colorTextDescription: '#74695E',
      borderRadius: 10,
      borderRadiusLG: 14,
      borderRadiusSM: 8,
      fontFamily:
        'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      fontSize: 14,
    },
    components: {
      Layout: { headerBg: '#FBF7EF', headerHeight: 56, siderBg: '#1F1B16' },
      Menu: { itemHeight: 36, itemBorderRadius: 10 },
      Button: { fontWeight: 500, controlHeight: 36 },
      Card: { paddingLG: 20 },
      Modal: { borderRadiusLG: 16 },
    },
  },
};
