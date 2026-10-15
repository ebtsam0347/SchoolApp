import { theme } from 'antd';
import type { ThemeDefinition } from '../tokens';

/**
 * Graphite — minimalist-studio dark theme. Hermès / Flos / Nendo.
 * Achromatic everywhere except a single coral note on active states.
 * The only theme where sidebar is darker than the surface.
 */
export const graphite: ThemeDefinition = {
  id: 'graphite',
  label: 'Graphite',
  surface: 'dark',
  preview: { brand: '#FB7185', accent: '#FDA4AF', sidebar: '#000000' },
  blurb: 'Minimalist-studio. Achromatic restraint with a single coral note.',
  antd: {
    cssVar: { key: 'fshrms' },
    hashed: false,
    algorithm: theme.darkAlgorithm,
    token: {
      colorPrimary: '#FB7185',
      colorInfo: '#FDA4AF',
      colorSuccess: '#4ADE80',
      colorWarning: '#FBBF24',
      colorError: '#F87171',
      colorBgLayout: '#0F0F0F',
      colorBgContainer: '#1A1A1A',
      colorBgElevated: '#242424',
      colorBorder: '#2E2E2E',
      colorBorderSecondary: '#1F1F1F',
      colorText: '#FAFAFA',
      colorTextSecondary: '#C8C8C8',
      colorTextTertiary: '#8A8A8A',
      colorTextQuaternary: '#5C5C5C',
      colorTextHeading: '#FFFFFF',
      colorTextLabel: '#C8C8C8',
      colorTextDescription: '#A0A0A0',
      colorTextPlaceholder: '#6B6B6B',
      colorTextDisabled: '#4A4A4A',
      borderRadius: 4,
      borderRadiusLG: 6,
      borderRadiusSM: 2,
      fontFamily:
        'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      fontSize: 14,
    },
    components: {
      Layout: { headerBg: '#1A1A1A', headerHeight: 56, siderBg: '#000000' },
      Menu: { itemHeight: 36, itemBorderRadius: 4 },
      Button: { fontWeight: 500, controlHeight: 36 },
      Card: { paddingLG: 20 },
      Modal: { borderRadiusLG: 6 },
    },
  },
};
