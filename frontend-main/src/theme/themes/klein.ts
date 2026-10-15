import { theme } from 'antd';
import type { ThemeDefinition } from '../tokens';

/**
 * Klein — museum-bold. Yves Klein IKB ultramarine + electric chartreuse.
 * Chartreuse is punctuation only (focus rings, sidebar active icon). Never wash.
 */
export const klein: ThemeDefinition = {
  id: 'klein',
  label: 'Klein',
  surface: 'light',
  preview: { brand: '#002FA7', accent: '#D4FF00', sidebar: '#0A0A0A' },
  blurb: 'Museum-bold. Ultramarine over true black, with chartreuse punctuation.',
  antd: {
    cssVar: { key: 'fshrms' },
    hashed: false,
    algorithm: theme.defaultAlgorithm,
    token: {
      colorPrimary: '#002FA7',
      colorInfo: '#002FA7',
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
      colorBgLayout: '#FFFFFF',
      colorBgContainer: '#FFFFFF',
      colorBgElevated: '#FFFFFF',
      colorBorder: '#E4E4E7',
      colorBorderSecondary: '#F1F1F3',
      colorText: '#0A0A0A',
      colorTextSecondary: '#3F3F46',
      colorTextTertiary: '#71717A',
      colorTextQuaternary: '#A1A1AA',
      colorTextHeading: '#000000',
      colorTextLabel: '#3F3F46',
      colorTextDescription: '#52525B',
      borderRadius: 6,
      borderRadiusLG: 8,
      borderRadiusSM: 4,
      fontFamily:
        'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      fontSize: 14,
    },
    components: {
      Layout: { headerBg: '#FFFFFF', headerHeight: 56, siderBg: '#0A0A0A' },
      Menu: { itemHeight: 36, itemBorderRadius: 4 },
      Button: { fontWeight: 600, controlHeight: 36 },
      Card: { paddingLG: 20 },
      Modal: { borderRadiusLG: 8 },
    },
  },
};
