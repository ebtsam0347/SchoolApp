// Build a URL query-string from a flat object.
//
// Skips `undefined`, `null`, and empty strings so optional filters don't
// leak as `?status=` and break upstream parsing. Arrays repeat the key
// (`?ids=1&ids=2`). Returns `""` or `"?…"` so callers can append directly
// without a "do I need a ?" check.

export function qs(
  params: Record<string, unknown> | null | undefined,
): string {
  if (!params) return '';
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    if (Array.isArray(v)) {
      for (const item of v) {
        if (item === undefined || item === null || item === '') continue;
        sp.append(k, String(item));
      }
    } else {
      sp.set(k, String(v));
    }
  }
  const s = sp.toString();
  return s ? `?${s}` : '';
}
