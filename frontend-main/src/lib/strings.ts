// String helpers used widely enough to deserve a single home.
//
// `enumLabel`: SCREAMING_SNAKE → Sentence case ("ANNUAL_LEAVE" → "Annual
// leave"). Replaces several local variants — some of which used
// `replace('_', ' ')` (first underscore only — bug on multi-word codes).
//
// `matches`/`matchesAny`: case-insensitive substring filter used by every
// table search box. Stops every list page rolling its own
// `.toLowerCase().includes(q.toLowerCase())`.

/** "ANNUAL_LEAVE" → "Annual leave". Empty input → "". */
export function enumLabel(value: string | null | undefined): string {
  if (!value) return '';
  const words = value.split('_').filter(Boolean);
  if (words.length === 0) return '';
  return words
    .map((w, i) => {
      const lower = w.toLowerCase();
      return i === 0 ? lower.charAt(0).toUpperCase() + lower.slice(1) : lower;
    })
    .join(' ');
}

/** Case-insensitive substring match. Empty / whitespace query matches. */
export function matches(text: string | null | undefined, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  if (!text) return false;
  return text.toLowerCase().includes(q);
}

/** True if any field on `row` matches `query`. */
export function matchesAny<Row>(
  row: Row,
  query: string,
  fields: ReadonlyArray<keyof Row>,
): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  for (const f of fields) {
    const v = row[f];
    if (v == null) continue;
    if (String(v).toLowerCase().includes(q)) return true;
  }
  return false;
}
