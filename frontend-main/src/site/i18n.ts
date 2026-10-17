// Language of the public website: English (default) or Urdu.
//
// The choice is read once when the page loads and kept in localStorage.
// Changing it reloads the page, so a plain function — tr() — can translate
// anything: JSX, module-level data in content.ts, form messages, page titles.
// Urdu text lives in i18n-ur.ts, keyed by the exact English source string; a
// string without an Urdu entry falls back to English. The site layout sets
// <html lang dir> (see useSiteLanguage in hooks.ts) so Urdu renders right-to-left.

import { UR } from './i18n-ur';

export type Lang = 'en' | 'ur';

const KEY = 'school.site.lang';

function readStored(): Lang | null {
  try {
    const v = typeof window === 'undefined' ? null : window.localStorage.getItem(KEY);
    return v === 'en' || v === 'ur' ? v : null;
  } catch {
    return null;
  }
}

/** The visitor's saved choice, or null if they have not chosen yet. */
export const chosenLang: Lang | null = readStored();
/** Language this page load renders in. */
export const lang: Lang = chosenLang ?? 'en';
export const isUrdu = lang === 'ur';

/** Translates an English source string for the current page load. */
export function tr(english: string): string {
  return isUrdu ? (UR[english] ?? english) : english;
}

/** Saves the visitor's choice; reloads when it changes the language. */
export function chooseLang(next: Lang): void {
  try {
    window.localStorage.setItem(KEY, next);
  } catch {
    /* private mode: the choice lasts for this page only */
  }
  if (next !== lang) window.location.reload();
}

/** tr() for sentences with values in them: trf('Map showing {address}', { address }). */
export function trf(english: string, vars: Record<string, string>): string {
  return tr(english).replace(/\{(\w+)\}/g, (_, k: string) => vars[k] ?? '');
}
