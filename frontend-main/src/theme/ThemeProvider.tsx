import { createContext, useEffect, useState, type ReactNode } from 'react';
import { App as AntdApp, ConfigProvider } from 'antd';
import { DEFAULT_THEME, getTheme, themes } from './themes';
import type { ThemeDefinition, ThemeId } from './tokens';

const STORAGE_KEY = 'fshrms.theme';

interface ThemeContextValue {
  theme: ThemeDefinition;
  themeId: ThemeId;
  setThemeId: (id: ThemeId) => void;
  available: ThemeDefinition[];
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);

function readInitial(): ThemeId {
  if (typeof window === 'undefined') return DEFAULT_THEME;
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (saved && saved in themes) return saved as ThemeId;
  } catch {
    // localStorage may be blocked; ignore
  }
  return DEFAULT_THEME;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [themeId, setThemeIdState] = useState<ThemeId>(readInitial);

  // Mirror the theme id onto <html data-theme="…"> so themes.css applies.
  // The pre-hydration script in index.html already set this on first paint;
  // this keeps it in sync after user changes.
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', themeId);
  }, [themeId]);

  const setThemeId = (id: ThemeId) => {
    setThemeIdState(id);
    try {
      window.localStorage.setItem(STORAGE_KEY, id);
    } catch {
      // ignore storage failures
    }
  };

  const theme = getTheme(themeId);
  const value: ThemeContextValue = {
    theme,
    themeId,
    setThemeId,
    available: Object.values(themes),
  };

  return (
    <ThemeContext.Provider value={value}>
      <ConfigProvider theme={theme.antd}>
        <AntdApp>{children}</AntdApp>
      </ConfigProvider>
    </ThemeContext.Provider>
  );
}
