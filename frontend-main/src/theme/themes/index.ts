import type { ThemeDefinition, ThemeId } from '../tokens';
import { spirit } from './spirit';
import { vellum } from './vellum';
import { klein } from './klein';
import { linen } from './linen';
import { tokyoNight } from './tokyo-night';
import { graphite } from './graphite';

export const themes: Record<ThemeId, ThemeDefinition> = {
  spirit,
  vellum,
  klein,
  linen,
  'tokyo-night': tokyoNight,
  graphite,
};

export const themeList: ThemeDefinition[] = [
  spirit,
  vellum,
  klein,
  linen,
  tokyoNight,
  graphite,
];

export const DEFAULT_THEME: ThemeId = 'spirit';

export function getTheme(id: string | null | undefined): ThemeDefinition {
  if (id && id in themes) return themes[id as ThemeId];
  return themes[DEFAULT_THEME];
}
