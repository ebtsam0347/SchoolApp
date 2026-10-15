// Client-side CSV export — builds a CSV Blob from a header + rows and
// triggers a browser download. No server round-trip and no xlsx dependency;
// every consumer already has the rows in memory (small, paginated tables).

export function downloadCsv(filename: string, header: string[], rows: (string | number)[][]) {
  const escape = (v: string | number) => `"${String(v).replace(/"/g, '""')}"`;
  const lines = rows.map((row) => row.map(escape).join(','));
  const csv = [header.map(escape).join(','), ...lines].join('\n');
  // Leading BOM tells Excel the bytes are UTF-8 — without it, Excel guesses
  // Windows-1252 and mangles any non-ASCII character (e.g. "·" → "Â·").
  const BOM = '﻿';
  const blob = new Blob([BOM + csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
