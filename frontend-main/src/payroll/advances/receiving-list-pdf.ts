// Client-side "Permanent Advance Receiving List" slip — the sheet HR prints
// for a long-term advance once HR has approved it and it's waiting on
// Accounts. Same layout as the legacy software's PDF: company name, a purple
// title band, one table row per advance, a totals band (count + amount in
// figures and words) and two signature lines.
//
// There's no backend document for this, so the sheet is built as HTML,
// rendered into a hidden iframe on the current page and printed — the print
// dialog defaults to "Save as PDF". Same approach as the travel expenditure
// sheet (factory-tour/pdf/travelExpenditurePdf.ts).
//
// Colours come from the active app theme (see `receivingListPalette`), not
// hard-coded, so the slip matches whichever theme the user is on.

import type { GlobalToken } from 'antd';
import type { ThemeDefinition } from '@/theme';
import type { PermanentAdvanceReceivingRow } from '../types';

const COMPANY_NAME = 'The Spirit of Islam School';

/** The slip's four colours, taken from the active theme. */
export type ReceivingListPalette = {
  /** Title / totals bands and table header. */
  accent: string;
  /** Text on `accent`. */
  onAccent: string;
  /** Body text on the (white) paper. */
  text: string;
  border: string;
};

/**
 * Maps the active theme onto the slip. Every theme pairs a `colorPrimary`
 * with a contrasting `colorBgContainer` (dark primary + near-white container
 * on light themes, the reverse on dark ones), so those two make the bands.
 * Body text is printed on white paper: a light theme's `colorText` is dark
 * already, but a dark theme's is near-white — there `colorBgContainer` (its
 * darkest surface) stands in so the text stays legible.
 */
export function receivingListPalette(
  token: GlobalToken,
  surface: ThemeDefinition['surface'],
): ReceivingListPalette {
  return {
    accent: token.colorPrimary,
    onAccent: token.colorBgContainer,
    text: surface === 'light' ? token.colorText : token.colorBgContainer,
    border: token.colorBorder,
  };
}

function escapeHtml(value: string | number | null | undefined): string {
  if (value == null) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** "DD/MM/YYYY" from an ISO date / date-time; "—" when absent. */
function ddmmyyyy(iso: string | null | undefined): string {
  if (!iso) return '—';
  const [y, m, d] = iso.slice(0, 10).split('-');
  return y && m && d ? `${d}/${m}/${y}` : '—';
}

function todayDdmmyyyy(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()}`;
}

const ONES = [
  '', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
  'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen',
  'Eighteen', 'Nineteen',
];
const TENS = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

function belowThousand(n: number): string {
  const parts: string[] = [];
  if (n >= 100) {
    parts.push(`${ONES[Math.floor(n / 100)]} Hundred`);
    n %= 100;
  }
  if (n >= 20) {
    parts.push(TENS[Math.floor(n / 10)] + (n % 10 ? ` ${ONES[n % 10]}` : ''));
  } else if (n > 0) {
    parts.push(ONES[n]);
  }
  return parts.join(' ');
}

/** Whole rupees in words, international scale — 30000 → "Thirty Thousand". */
export function amountInWords(amount: number): string {
  let n = Math.floor(Math.abs(amount));
  if (n === 0) return 'Zero';
  const scales = ['', 'Thousand', 'Million', 'Billion'];
  const parts: string[] = [];
  for (let i = 0; n > 0 && i < scales.length; i++) {
    const chunk = n % 1000;
    if (chunk) parts.unshift(`${belowThousand(chunk)}${scales[i] ? ` ${scales[i]}` : ''}`);
    n = Math.floor(n / 1000);
  }
  return parts.join(' ');
}

/**
 * The slip's per-row fields, resolved from the receiving-list row. Kept in
 * one place so the backend's enrichment (`employee`, `dateOfJoining`) maps
 * here and nowhere else.
 */
export function receivingSlipFields(row: PermanentAdvanceReceivingRow) {
  const e = row.employee ?? null;
  return {
    employeeCode: e?.employeeCode ?? null,
    name: e?.fullName ?? null,
    department: e?.departmentName ?? null,
    section: e?.sectionName ?? null,
    designation: e?.designationName ?? null,
    reason: row.description ?? null,
    dateOfJoining: row.dateOfJoining ?? null,
    amount: row.amount,
    deduction: row.monthlyDeduction,
  };
}

function buildHtml(rows: PermanentAdvanceReceivingRow[], palette: ReceivingListPalette): string {
  const cell = (v: string | number | null | undefined) => escapeHtml(v ?? '—');
  const tableRows = rows
    .map((row) => {
      const f = receivingSlipFields(row);
      return `
        <tr>
          <td class="strong">${cell(f.employeeCode)}</td>
          <td>${cell(f.name)}</td>
          <td>${cell(f.department)}</td>
          <td>${cell(f.section)}</td>
          <td>${cell(f.designation)}</td>
          <td>${cell(f.reason)}</td>
          <td class="nowrap">${ddmmyyyy(f.dateOfJoining)}</td>
          <td class="strong">${escapeHtml(f.amount)}</td>
          <td>${escapeHtml(f.deduction)}</td>
        </tr>`;
    })
    .join('');
  const total = rows.reduce((s, r) => s + (r.amount ?? 0), 0);
  const title = `Permanent Advance Receiving List (${todayDdmmyyyy()})`;

  return `<!doctype html>
<html>
<head>
<meta charset="utf-8" />
<title>${escapeHtml(title)}</title>
<style>
  @page { size: A4 landscape; margin: 12mm; }
  * { box-sizing: border-box; }
  body {
    font-family: 'Times New Roman', Times, serif;
    color: ${palette.text};
    margin: 0;
    font-size: 12px;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
  .company {
    text-align: center;
    font-size: 17px;
    font-weight: 700;
    margin: 0 0 12px;
  }
  .band {
    background: ${palette.accent};
    color: ${palette.onAccent};
    font-weight: 700;
    padding: 8px 16px;
  }
  .title { text-align: center; font-size: 16px; }
  table {
    width: 100%;
    border-collapse: separate;
    border-spacing: 2px;
    border: 1px solid ${palette.border};
    margin-bottom: 20px;
  }
  th {
    background: ${palette.accent};
    color: ${palette.onAccent};
    font-weight: 700;
    padding: 8px 4px;
    text-align: center;
    vertical-align: middle;
  }
  td {
    border: 1px solid ${palette.border};
    padding: 8px 4px;
    text-align: center;
    vertical-align: middle;
  }
  .strong { font-weight: 700; font-size: 16px; }
  .nowrap { white-space: nowrap; }
  .totals {
    display: flex;
    justify-content: space-between;
    font-size: 15px;
    padding: 10px 70px;
  }
  .signatures {
    display: flex;
    justify-content: space-between;
    margin-top: 44px;
    font-weight: 700;
    page-break-inside: avoid;
  }
  .signatures div span {
    display: block;
    border-top: 1px solid ${palette.text};
    width: 150px;
    margin-bottom: 26px;
  }
  .signatures div:last-child span { margin-left: auto; }
  @media print {
    .no-print { display: none; }
    table, .totals { page-break-inside: avoid; }
  }
  @media screen {
    body { margin: 24px auto; max-width: 277mm; }
  }
</style>
</head>
<body>
  <div class="no-print" style="text-align:right; margin-bottom:12px;">
    <button onclick="window.print()" style="padding:6px 14px; cursor:pointer;">Print / Save as PDF</button>
  </div>

  <div class="company">${COMPANY_NAME}</div>
  <div class="band title">${escapeHtml(title)}</div>

  <table>
    <tr>
      <th>Employee<br />Code</th>
      <th>Name</th>
      <th>Department</th>
      <th>Section</th>
      <th>Designation</th>
      <th>Reason</th>
      <th>DOJ</th>
      <th>Amount</th>
      <th>Deduction</th>
    </tr>
    ${tableRows}
  </table>

  <div class="band totals">
    <span>Total Employees: ${rows.length}</span>
    <span>Total Amount: ${total.toLocaleString('en-PK')} | ${amountInWords(total)}</span>
  </div>

  <div class="signatures">
    <div><span>&nbsp;</span>Sr.Manager(People &amp; Culture)</div>
    <div style="text-align:right"><span>&nbsp;</span>GM(People &amp; Culture)</div>
  </div>
</body>
</html>`;
}

function fileNameFor(rows: PermanentAdvanceReceivingRow[]): string {
  const code = rows.length === 1 ? (rows[0]!.requestCode ?? `#${rows[0]!.id}`) : `${rows.length}-advances`;
  return `Advance-Receiving-List-${code}`;
}

/**
 * Renders the slip into a hidden iframe and opens the print dialog on it
 * (destination defaults to "Save as PDF"). The parent page's title is
 * swapped for the file name while the dialog is up — Chrome names an
 * iframe print after it — and restored once it closes.
 */
export function printReceivingList(
  rows: PermanentAdvanceReceivingRow[],
  palette: ReceivingListPalette,
): void {
  const iframe = document.createElement('iframe');
  iframe.setAttribute('aria-hidden', 'true');
  // Off-screen rather than display:none — a frame with no layout box prints
  // blank in some browsers.
  iframe.style.cssText = 'position:fixed;left:-10000px;top:0;width:277mm;height:190mm;border:0;';
  document.body.appendChild(iframe);

  const win = iframe.contentWindow;
  const doc = iframe.contentDocument;
  if (!win || !doc) {
    iframe.remove();
    downloadReceivingListHtml(rows, palette);
    return;
  }

  doc.open();
  doc.write(buildHtml(rows, palette));
  doc.close();
  const override = doc.createElement('style');
  override.textContent = '.no-print { display: none !important; } body { margin: 0; max-width: none; }';
  doc.head.appendChild(override);

  const previousTitle = document.title;
  let cleanedUp = false;
  const cleanup = () => {
    if (cleanedUp) return;
    cleanedUp = true;
    document.title = previousTitle;
    iframe.remove();
  };

  const print = () => {
    document.title = fileNameFor(rows);
    win.addEventListener('afterprint', cleanup);
    try {
      win.focus();
      win.print();
    } catch {
      cleanup();
      return;
    }
    // Fallback for browsers that don't fire afterprint; the delay keeps a
    // still-spooling job from being cancelled by the teardown.
    setTimeout(cleanup, 1000);
  };

  if (doc.readyState === 'complete') setTimeout(print, 0);
  else win.addEventListener('load', print);
}

/** Fallback when the iframe can't be reached: save the slip as .html, which
 *  the user can open and print / save as PDF from. */
export function downloadReceivingListHtml(
  rows: PermanentAdvanceReceivingRow[],
  palette: ReceivingListPalette,
): void {
  const blob = new Blob([buildHtml(rows, palette)], { type: 'text/html' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${fileNameFor(rows)}.html`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
