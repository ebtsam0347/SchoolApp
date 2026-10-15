// Page layout for bulk salary-slip PDFs — shared by the run's "Download all
// payslips" and Account Report A1 (Salary Slips). Sent as `?layout=`:
//
//   full   one slip per A4 page (the server default)
//   sheet  three slips per A4 page with dashed cut lines
//
// Bulk prints default to `sheet`; the last choice is remembered per browser.

export type SlipLayout = 'full' | 'sheet';

export const SLIP_LAYOUT_OPTIONS: { label: string; value: SlipLayout }[] = [
  { label: 'Full page (1 per page)', value: 'full' },
  { label: 'Compact (3 per page)', value: 'sheet' },
];

/** Helper text shown under the control while `sheet` is selected. */
export const SHEET_LAYOUT_HINT =
  'Zero-amount salary heads are left out. Room for 8 lines (English) or 5 (Urdu) — ' +
  'extra lines are combined into "Others" with the same total. Held slips show ' +
  'only an "Amount held — not disbursed" banner, not the hold reason.';

const STORAGE_KEY = 'hrms.payslips.layout';

export function loadSlipLayout(): SlipLayout {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return v === 'full' || v === 'sheet' ? v : 'sheet';
  } catch {
    return 'sheet';
  }
}

export function saveSlipLayout(layout: SlipLayout): void {
  try {
    localStorage.setItem(STORAGE_KEY, layout);
  } catch {
    // Storage blocked (private mode etc.) — the choice just isn't remembered.
  }
}
