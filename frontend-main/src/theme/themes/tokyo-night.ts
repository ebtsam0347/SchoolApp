import { theme } from 'antd';
import type { ThemeDefinition } from '../tokens';

/**
 * Tokyo Night — dev-respected dark theme. Inherits IDE-theme lineage.
 * Sidebar one shade deeper than surface so it's felt, not seen.
 */
export const tokyoNight: ThemeDefinition = {
  id: 'tokyo-night',
  label: 'Tokyo Night',
  surface: 'dark',
  preview: { brand: '#7AA2F7', accent: '#BB9AF7', sidebar: '#16161E' },
  blurb: 'Dev-respected. Deep night navy, blue-violet accents, IDE-theme DNA.',
  antd: {
    cssVar: { key: 'fshrms' },
    hashed: false,
    algorithm: theme.darkAlgorithm,
    token: {
      colorPrimary: '#7AA2F7',
      colorInfo: '#7DCFFF',
      colorSuccess: '#9ECE6A',
      colorWarning: '#E0AF68',
      colorError: '#F7768E',
      colorBgLayout: '#1A1B26',
      colorBgContainer: '#24283B',
      colorBgElevated: '#2A2E45',
      colorBorder: '#3B3F5C',
      colorBorderSecondary: '#2F334D',
      colorText: '#C8D2F8',
      colorTextSecondary: '#A9B4D8',
      colorTextTertiary: '#7D88AE',
      colorTextQuaternary: '#565F89',
      colorTextHeading: '#E4EAFF',
      colorTextLabel: '#A9B4D8',
      colorTextDescription: '#7D88AE',
      colorTextPlaceholder: '#565F89',
      colorTextDisabled: '#3F4467',
      borderRadius: 8,
      borderRadiusLG: 12,
      borderRadiusSM: 6,
      fontFamily:
        'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      fontSize: 14,
    },
    components: {
      Layout: { headerBg: '#24283B', headerHeight: 56, siderBg: '#16161E' },
      Menu: { itemHeight: 36, itemBorderRadius: 8 },
      Button: { fontWeight: 500, controlHeight: 36 },
      Card: { paddingLG: 20 },
      Modal: { borderRadiusLG: 16 },
    },
  },
};
