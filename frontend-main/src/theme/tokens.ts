/**
 * Type definitions for our theme system.
 *
 * Two layers:
 *   1. AntD tokens — consumed by ConfigProvider, available via theme.useToken().
 *   2. App tokens  — consumed via CSS variables (var(--app-*)) for surfaces
 *      AntD doesn't theme: sidebar, topbar glass, gradients, charts, footer.
 *
 * App tokens are declared in TS (this file's AppTokens shape), with values
 * mirrored in src/styles/themes.css under [data-theme="…"] selectors.
 * The CSS file is the runtime source of truth so theme switching is just
 * a data-theme attribute swap (no React re-render needed for app tokens).
 */

import type { ThemeConfig } from 'antd';

export type ThemeId =
  | 'spirit'
  | 'vellum'
  | 'klein'
  | 'linen'
  | 'tokyo-night'
  | 'graphite';

export interface ThemeDefinition {
  id: ThemeId;
  label: string;
  surface: 'light' | 'dark';
  /** Brand → accent gradient stops. Used for swatch preview & at-a-glance recognition. */
  preview: { brand: string; accent: string; sidebar: string };
  /** Personality blurb shown in the switcher. */
  blurb: string;
  /** AntD ConfigProvider theme config. */
  antd: ThemeConfig;
}

/** Shape of CSS-variable tokens. Values live in themes.css; this is the contract. */
export interface AppTokens {
  sidebar: {
    bg: string;
    bgElevated: string;
    bgHover: string;
    bgActive: string;
    border: string;
    text: string;
    textMuted: string;
    textActive: string;
    iconActive: string;
  };
  topbar: {
    bg: string;
    bgScrolled: string;
    border: string;
    text: string;
    textMuted: string;
  };
  accent: {
    gradFrom: string;
    gradTo: string;
    glow: string;
  };
  /** Foreground & chip styling for content placed on the accent gradient. */
  onAccent: {
    fg: string;
    fgMuted: string;
    chipBg: string;
    chipBorder: string;
  };
  selection: {
    bg: string;
    fg: string;
  };
  footer: {
    bg: string;
    text: string;
    border: string;
  };
  /** Spacing scale (theme-independent). Use for paddings, gaps, margins. */
  space: {
    xxs: string; // 2px
    xs: string; // 4px
    sm: string; // 8px
    md: string; // 16px
    lg: string; // 24px
    xl: string; // 32px
    xxl: string; // 48px
  };
  /** Border-radius scale (theme-independent). */
  radius: {
    sm: string; // 8px
    md: string; // 12px
    lg: string; // 16px
    full: string; // 999px
  };
  /** Transition timing (theme-independent). */
  transition: {
    fast: string;
    normal: string;
    slow: string;
  };
  /** Elevation shadows (theme-aware — dark surfaces get sharper shadows). */
  shadow: {
    sm: string;
    md: string;
    lg: string;
  };
}

/** Map of CSS variable names. Used by anyone who needs to reference a var by name. */
export const cssVars = {
  sidebar: {
    bg: 'var(--app-sidebar-bg)',
    bgElevated: 'var(--app-sidebar-bg-elevated)',
    bgHover: 'var(--app-sidebar-bg-hover)',
    bgActive: 'var(--app-sidebar-bg-active)',
    border: 'var(--app-sidebar-border)',
    text: 'var(--app-sidebar-text)',
    textMuted: 'var(--app-sidebar-text-muted)',
    textActive: 'var(--app-sidebar-text-active)',
    iconActive: 'var(--app-sidebar-icon-active)',
  },
  topbar: {
    bg: 'var(--app-topbar-bg)',
    bgScrolled: 'var(--app-topbar-bg-scrolled)',
    border: 'var(--app-topbar-border)',
    text: 'var(--app-topbar-text)',
    textMuted: 'var(--app-topbar-text-muted)',
  },
  accent: {
    gradFrom: 'var(--app-accent-grad-from)',
    gradTo: 'var(--app-accent-grad-to)',
    glow: 'var(--app-accent-glow)',
  },
  onAccent: {
    fg: 'var(--app-on-accent-fg)',
    fgMuted: 'var(--app-on-accent-fg-muted)',
    chipBg: 'var(--app-on-accent-chip-bg)',
    chipBorder: 'var(--app-on-accent-chip-border)',
  },
  selection: {
    bg: 'var(--app-selection-bg)',
    fg: 'var(--app-selection-fg)',
  },
  footer: {
    bg: 'var(--app-footer-bg)',
    text: 'var(--app-footer-text)',
    border: 'var(--app-footer-border)',
  },
  space: {
    xxs: 'var(--app-space-xxs)',
    xs: 'var(--app-space-xs)',
    sm: 'var(--app-space-sm)',
    md: 'var(--app-space-md)',
    lg: 'var(--app-space-lg)',
    xl: 'var(--app-space-xl)',
    xxl: 'var(--app-space-xxl)',
  },
  radius: {
    sm: 'var(--app-radius-sm)',
    md: 'var(--app-radius-md)',
    lg: 'var(--app-radius-lg)',
    full: 'var(--app-radius-full)',
  },
  transition: {
    fast: 'var(--app-transition-fast)',
    normal: 'var(--app-transition-normal)',
    slow: 'var(--app-transition-slow)',
  },
  shadow: {
    sm: 'var(--app-shadow-sm)',
    md: 'var(--app-shadow-md)',
    lg: 'var(--app-shadow-lg)',
  },
} as const;
