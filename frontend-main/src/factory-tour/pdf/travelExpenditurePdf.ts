// Client-side "Official Tour Expenditure" print view — the same layout the
// legacy Factory Duty Tour software produced as a PDF. There is no backend
// endpoint for this document on the /travel API, so this builds the sheet
// in the browser, renders it into a hidden iframe on the current page and
// prints that straight to PDF — the user never leaves the page they're on.
//
// One request = one sheet: the trip details/approvals are printed once and
// every settled traveller gets a row in the applicant and expense tables,
// rather than a separate document per person.

import { formatDateShort, formatDateTime } from '@/lib';
import type { ApprovalStageRole, ApprovalStepDto } from '@/attendance';
import type { TravelExpenseResponse, TravelSettlementResponse } from '@/travel';
import type { DepartmentDto, DesignationDto, GradeDto } from '@/people';
import type { FactoryTourDto } from '../types';

const KM_PER_LITRE = 10;

/** Best-effort mapping onto the legacy sheet's two fixed approval rows —
 * the /travel workflow's stage roles don't carry the old HOD/DD labels
 * directly, so this is a readable guess, not a guaranteed match. */
const STAGE_LABEL: Record<string, string> = {
  DEPT_HEAD: 'HOD Approval',
  MANAGER: 'DD Approval',
  HR: 'HR Approval',
};

function stageLabel(role: ApprovalStageRole): string {
  return STAGE_LABEL[role as string] ?? `${role} Approval`;
}

function escapeHtml(value: string | number | null | undefined): string {
  if (value == null) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function money(amount: number): string {
  return amount.toLocaleString('en-PK');
}

/** One settled traveller on the trip — a row in the sheet's tables. */
export type TravelExpenditureTraveller = {
  employeeCode: string;
  employeeName: string;
  settlement: TravelSettlementResponse;
  /** This traveller's own expense claims on the trip (non-cancelled). */
  expenses: TravelExpenseResponse[];
};

export type TravelExpenditurePdfInput = {
  tour: FactoryTourDto;
  /** Every settled traveller the sheet covers — all on one document. */
  travellers: TravelExpenditureTraveller[];
  departments?: DepartmentDto[];
  designations?: DesignationDto[];
  grades?: GradeDto[];
};

type TravellerTotals = {
  accommodation: number;
  meal: number;
  tollTax: number;
  other: number;
  total: number;
};

function totalsFor(expenses: TravelExpenseResponse[]): TravellerTotals {
  const sum = (pred: (e: TravelExpenseResponse) => boolean) =>
    expenses
      .filter(pred)
      .reduce((s, e) => s + (e.approvedAmount ?? e.eligibleAmount ?? e.claimedAmount ?? 0), 0);

  const accommodation = sum((e) => e.expenseCategoryCode === 'ACCOMMODATION');
  const meal = sum((e) => e.expenseCategoryCode === 'MEAL');
  const tollTax = sum((e) => e.expenseCategoryCode === 'TOLL');
  const other = sum(
    (e) => !['ACCOMMODATION', 'MEAL', 'TOLL'].includes(e.expenseCategoryCode ?? ''),
  );

  return { accommodation, meal, tollTax, other, total: accommodation + meal + tollTax + other };
}

function directionLabel(settlement: TravelSettlementResponse): string {
  if (settlement.settlementDirection === 'EMPLOYEE_PAYABLE') return 'Employee payable';
  if (settlement.settlementDirection === 'COMPANY_PAYABLE') return 'Company payable';
  return settlement.settlementDirection ?? '—';
}

function buildHtml(input: TravelExpenditurePdfInput): string {
  const { tour, travellers } = input;
  const currency =
    travellers[0]?.settlement.settlementCurrencyCode ?? tour.currency ?? 'PKR';

  const departmentName = input.departments?.find((d) => d.id === tour.departmentId)?.name ?? '—';
  const designationTitle =
    input.designations?.find((d) => d.id === tour.designationId)?.title ?? '—';
  const gradeName = input.grades?.find((g) => g.id === tour.gradeId)?.name ?? '—';

  const distanceKm = tour.totalDistanceKm ?? 0;
  const petrolLitres = Math.round((distanceKm / KM_PER_LITRE) * 10) / 10;

  const rows = travellers.map((t) => ({ ...t, totals: totalsFor(t.expenses) }));
  const grand = rows.reduce<TravellerTotals>(
    (acc, r) => ({
      accommodation: acc.accommodation + r.totals.accommodation,
      meal: acc.meal + r.totals.meal,
      tollTax: acc.tollTax + r.totals.tollTax,
      other: acc.other + r.totals.other,
      total: acc.total + r.totals.total,
    }),
    { accommodation: 0, meal: 0, tollTax: 0, other: 0, total: 0 },
  );

  const applicantRows = rows
    .map(
      (r) => `
        <tr>
          <td>${escapeHtml(r.employeeCode)}</td>
          <td>${escapeHtml(r.employeeName)}</td>
          <td>${escapeHtml(r.settlement.settlementNumber ?? `#${r.settlement.id}`)}</td>
          <td>${escapeHtml(directionLabel(r.settlement))}</td>
          <td class="num">${money(r.settlement.finalSettlementAmount ?? r.totals.total)} ${escapeHtml(
            r.settlement.settlementCurrencyCode ?? currency,
          )}</td>
        </tr>`,
    )
    .join('');

  const expenseRows = rows
    .map(
      (r) => `
        <tr>
          <td>${escapeHtml(r.employeeName)} <span class="muted">(${escapeHtml(r.employeeCode)})</span></td>
          <td class="num">${money(r.totals.accommodation)}</td>
          <td class="num">${money(r.totals.meal)}</td>
          <td class="num">${money(r.totals.tollTax)}</td>
          <td class="num">${money(r.totals.other)}</td>
          <td class="num">${money(r.totals.total)}</td>
        </tr>`,
    )
    .join('');

  const steps: ApprovalStepDto[] = tour.steps ?? [];
  const approvalRows = steps
    .slice(0, 2)
    .map(
      (s) => `
        <tr>
          <th>${escapeHtml(stageLabel(s.stageRole))}</th>
          <td>${
            s.status === 'APPROVED'
              ? `<span class="pill pill-approved">Approved</span> (${escapeHtml(s.decidedByName ?? s.assignedToName ?? '—')})`
              : s.status === 'REJECTED'
                ? `<span class="pill pill-rejected">Rejected</span> (${escapeHtml(s.decidedByName ?? '—')})`
                : `<span class="pill pill-pending">Pending</span> (${escapeHtml(s.assignedToName ?? '—')})`
          }</td>
          <th>Approval Date</th>
          <td>${s.decidedAt ? formatDateTime(s.decidedAt) : '-'}</td>
        </tr>`,
    )
    .join('');

  return `<!doctype html>
<html>
<head>
<meta charset="utf-8" />
<title>Official Tour Expenditure · ${escapeHtml(tour.requestId)}</title>
<style>
  /* Fixes the page box so the sheet's height budget is deterministic —
     without it Chrome adds its own default margin on top of the body's and
     the layout below can no longer be sized against one A4 page. */
  @page { size: A4; margin: 10mm; }

  * { box-sizing: border-box; }
  body {
    font-family: -apple-system, Segoe UI, Roboto, Arial, sans-serif;
    color: #1f2937;
    /* Margins come from @page — keeping body at 0 means the on-screen
       render inside the print iframe matches the printed box exactly, which
       is what fitToOnePage measures against. */
    margin: 0;
    font-size: 13px;
  }
  h1 {
    font-size: 18px;
    text-align: center;
    margin: 0 0 10px;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 10px;
  }
  caption {
    background: #2b3a55;
    color: #fff;
    font-weight: 600;
    text-align: center;
    padding: 4px;
    font-size: 13px;
  }
  th, td {
    border: 1px solid #d9dde3;
    /* Deliberately tight: the whole sheet is meant to land on one page. */
    padding: 4px 8px;
    text-align: left;
    vertical-align: top;
  }
  th {
    background: #f4f6f9;
    font-weight: 600;
    white-space: nowrap;
  }
  .kv th { width: 18%; }
  .num { text-align: right; white-space: nowrap; }
  .muted { color: #6b7280; font-size: 11px; }
  .total-row th, .total-row td {
    background: #f5c518;
    font-weight: 700;
  }
  .pill {
    display: inline-block;
    padding: 1px 8px;
    border-radius: 10px;
    font-size: 11px;
    font-weight: 600;
    color: #fff;
  }
  .pill-approved { background: #16a34a; }
  .pill-rejected { background: #dc2626; }
  .pill-pending { background: #d97706; }
  .meta {
    text-align: right;
    color: #6b7280;
    font-size: 11px;
    margin-top: 6px;
  }
  .signatures {
    display: flex;
    justify-content: space-between;
    margin-top: 28px;
    page-break-inside: avoid;
  }
  .signatures div {
    text-align: center;
    font-size: 12px;
  }
  .signatures div span {
    display: block;
    border-top: 1px solid #1f2937;
    width: 220px;
    padding-top: 4px;
    margin-bottom: 4px;
  }
  @media print {
    .no-print { display: none; }
    table { page-break-inside: avoid; }
  }
  /* Only for the downloaded .html fallback, which is read on screen before
     it's printed — a zero-margin page looks broken in a browser tab. The
     print iframe overrides this back to the bare print box (see PRINT_BOX). */
  @media screen {
    body { margin: 24px auto; max-width: 190mm; }
  }
</style>
</head>
<body>
  <div class="no-print" style="text-align:right; margin-bottom:12px;">
    <button onclick="window.print()" style="padding:6px 14px; cursor:pointer;">Print / Save as PDF</button>
  </div>

  <h1>Official Tour Expenditure</h1>

  <table class="kv">
    <caption>Tour Details</caption>
    <tr>
      <th>Request No.</th><td>${escapeHtml(tour.requestId)}</td>
      <th>Travellers</th><td>${rows.length}</td>
    </tr>
    <tr>
      <th>Department</th><td>${escapeHtml(departmentName)}</td>
      <th>Designation</th><td>${escapeHtml(designationTitle)}</td>
    </tr>
    <tr>
      <th>Grade</th><td>${escapeHtml(gradeName)}</td>
      <th>Distance</th><td>${distanceKm ? `${distanceKm} KM` : '—'}</td>
    </tr>
    <tr>
      <th>Start</th><td>${formatDateTime(tour.tourStart)}</td>
      <th>End</th><td>${formatDateTime(tour.tourEnd)}</td>
    </tr>
    <tr>
      <th>Departure From</th><td>${escapeHtml(tour.origin)}</td>
      <th>Arrival To</th><td>${escapeHtml(tour.destination)}</td>
    </tr>
    <tr>
      <th>Reason</th><td>${escapeHtml(tour.purpose ?? '—')}</td>
      <th>Petrol (In Liters)</th><td>${petrolLitres} Ltr</td>
    </tr>
  </table>

  <table>
    <caption>Applicant Information</caption>
    <tr>
      <th>Card No</th>
      <th>Name</th>
      <th>Settlement No.</th>
      <th>Direction</th>
      <th class="num">Final Amount</th>
    </tr>
    ${applicantRows || '<tr><td colspan="5">No settled travellers.</td></tr>'}
  </table>

  <table class="kv">
    <caption>Vehicle Details</caption>
    <tr>
      <th>Type</th><td>${escapeHtml(tour.transportationType ?? '—')}</td>
      <th>Source</th><td>${escapeHtml(tour.vehicleSource ?? '—')}</td>
      <th>Vehicle No</th><td>${escapeHtml(tour.vehicleNo ?? 'None')}</td>
    </tr>
  </table>

  <table class="kv">
    <caption>Approvals</caption>
    ${approvalRows || '<tr><td colspan="4">No approval steps recorded.</td></tr>'}
  </table>

  <table>
    <caption>Expenses (${escapeHtml(currency)})</caption>
    <tr>
      <th>Traveller</th>
      <th class="num">Accommodation</th>
      <th class="num">Meal</th>
      <th class="num">Toll Tax</th>
      <th class="num">Other</th>
      <th class="num">Total</th>
    </tr>
    ${expenseRows || '<tr><td colspan="6">No expense claims recorded.</td></tr>'}
    <tr class="total-row">
      <th>Total Expenses</th>
      <td class="num">${money(grand.accommodation)}</td>
      <td class="num">${money(grand.meal)}</td>
      <td class="num">${money(grand.tollTax)}</td>
      <td class="num">${money(grand.other)}</td>
      <td class="num">${money(grand.total)}</td>
    </tr>
  </table>

  <div class="meta">Generated on ${formatDateShort(new Date().toISOString())}</div>

  <div class="signatures">
    <div><span>&nbsp;</span>(Dept. Head) Signature</div>
    <div><span>&nbsp;</span>(GM People &amp; Culture) Signature</div>
  </div>
</body>
</html>`;
}

/**
 * The printable area of the sheet's `@page` box: A4 minus its 10mm margins,
 * in CSS px at the 96dpi the browser lays out against.
 */
const MM_TO_PX = 96 / 25.4;
const PRINT_BOX = { widthMm: 210 - 20, heightPx: (297 - 20) * MM_TO_PX };

/**
 * The sheet is meant to be a single page, but its height grows with the
 * traveller count, so tight CSS alone can't promise that. This measures what
 * the iframe actually laid out and, if it spills past one page, shrinks the
 * document just enough to fit.
 *
 * `zoom` rather than `transform: scale()` — a transform is ignored when the
 * browser paginates, so the overflow would still break to a second page.
 * Floored at 0.6: past that the sheet is too small to read, and two legible
 * pages beat one unreadable one.
 */
function fitToOnePage(doc: Document): void {
  const contentHeight = doc.body.scrollHeight;
  if (contentHeight <= PRINT_BOX.heightPx) return;
  const scale = Math.max(0.6, PRINT_BOX.heightPx / contentHeight);
  doc.body.style.zoom = String(scale);
}

/**
 * Renders the sheet into a hidden iframe on the current page and opens the
 * print dialog on it, where the destination defaults to "Save as PDF". No
 * new tab, so nothing depends on the popup blocker and the settlements modal
 * stays open underneath — and because the sheet is real HTML (not a canvas
 * snapshot) the saved PDF keeps selectable text plus the sheet's own print
 * stylesheet.
 *
 * Browsers name the saved file after the document title, and for an iframe
 * print that's the *parent* page's title in Chrome — so it's swapped for the
 * sheet's filename while the dialog is up and restored once it closes.
 *
 * Safe to call after an `await`: unlike `window.open`, creating an iframe
 * doesn't need to happen inside the click handler.
 */
export function printTravelExpenditureSheet(input: TravelExpenditurePdfInput): void {
  const iframe = document.createElement('iframe');
  iframe.setAttribute('aria-hidden', 'true');
  // Positioned off-screen rather than display:none — a frame with no layout
  // box prints blank in some browsers. Sized to the @page content box so the
  // on-screen layout fitToOnePage measures is the printed layout.
  iframe.style.cssText =
    `position:fixed;left:-10000px;top:0;width:${PRINT_BOX.widthMm}mm;height:297mm;border:0;`;
  document.body.appendChild(iframe);

  const win = iframe.contentWindow;
  const doc = iframe.contentDocument;
  if (!win || !doc) {
    iframe.remove();
    downloadTravelExpenditureHtml(input);
    return;
  }

  doc.open();
  doc.write(buildHtml(input));
  doc.close();

  // Drop the two screen-only affordances the standalone .html fallback needs
  // (a print button and centring margins) so the frame lays out as the bare
  // print box — anything else here would skew the height measurement.
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
    fitToOnePage(doc);
    document.title = `Tour-Expenditure-${input.tour.requestId}`;
    win.addEventListener('afterprint', cleanup);
    try {
      win.focus();
      win.print();
    } catch {
      cleanup();
      return;
    }
    // print() blocks until the dialog closes, so afterprint has normally
    // fired by now; this is the belt-and-braces path for browsers where it
    // doesn't. The delay matters — tearing the frame down the instant
    // print() returns can cancel the job that's still spooling.
    setTimeout(cleanup, 1000);
  };

  // The sheet loads no external assets, so document.write parses it
  // synchronously — just yield a frame so the iframe lays out before we ask
  // the browser to paginate it.
  if (doc.readyState === 'complete') setTimeout(print, 0);
  else win.addEventListener('load', print);
}

/** Fallback for the rare case where the iframe can't be reached (a sandboxed
 * or torn-down document): saves the sheet as an .html file instead. The user
 * can open it and print/save as PDF from there, same output, no dependency
 * on the print dialog being reachable. */
export function downloadTravelExpenditureHtml(input: TravelExpenditurePdfInput): void {
  const html = buildHtml(input);
  const blob = new Blob([html], { type: 'text/html' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `Tour-Expenditure-${input.tour.requestId}.html`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
