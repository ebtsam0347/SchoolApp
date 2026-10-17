// Every string the public website passes to tr()/trf() must have an Urdu entry,
// and trf() translations must keep the same {placeholders} as the English.

import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { UR } from './i18n-ur';

const SRC = join(__dirname, '..');
const FILES = [
  ...readdirSync(join(SRC, 'site'))
    .filter((f) => /\.tsx?$/.test(f) && !f.endsWith('.test.ts'))
    .map((f) => join(SRC, 'site', f)),
  ...readdirSync(join(SRC, 'routes', '_site')).map((f) => join(SRC, 'routes', '_site', f)),
  join(SRC, 'routes', '_site.tsx'),
];

/** Words that are intentionally empty in Urdu (word order differs). */
const INTENTIONALLY_EMPTY = new Set(['At']);

function calls(): { key: string; file: string }[] {
  const out: { key: string; file: string }[] = [];
  for (const file of FILES) {
    const src = readFileSync(file, 'utf8');
    for (const m of src.matchAll(/\btrf?\(\s*'((?:[^'\\]|\\.)*)'/g)) {
      out.push({ key: m[1].replace(/\\(.)/g, '$1'), file: file.slice(SRC.length + 1) });
    }
  }
  return out;
}

describe('Urdu translations', () => {
  const used = calls();

  it('finds the translated strings in the site sources', () => {
    expect(used.length).toBeGreaterThan(500);
  });

  it('has an Urdu entry for every tr()/trf() string', () => {
    const missing = [...new Set(used.filter((u) => !(u.key in UR)).map((u) => `${u.file}: ${u.key}`))];
    expect(missing).toEqual([]);
  });

  it('has no unexpected empty translations', () => {
    const empty = Object.entries(UR)
      .filter(([k, v]) => !v.trim() && !INTENTIONALLY_EMPTY.has(k))
      .map(([k]) => k);
    expect(empty).toEqual([]);
  });

  it('keeps the {placeholders} of every templated string', () => {
    const bad = Object.entries(UR).filter(([en, ur]) => {
      const names = (s: string) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(',');
      return names(en) !== names(ur);
    });
    expect(bad).toEqual([]);
  });
});
