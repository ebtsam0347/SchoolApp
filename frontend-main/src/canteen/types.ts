// Typed mirror of the canteen surface of attendance-service.
//
// Canteen punches are physically the same event stream as attendance
// punches — they just arrive from a device whose `deviceType` is CANTEEN
// (see `AttendanceDeviceType`). Payroll consumes the aggregate through
// `GET /attendance/employee/{id}/canteen-usage`; this module is the
// operator-facing side: browse the raw meal entries, add one by hand, and
// bulk-load a day's entries off a barcode / manual-card sheet.
//
// Endpoint status (see ./api.ts for the exact paths):
//   - canteen-usage       — live today
//   - /canteen/attendance — meal ledger + create + bulk upload
//   - /canteen/devices    — dedicated list, phase 2 (falls back to
//                           /devices filtered on deviceType=CANTEEN)
//   - /canteen/employees  — LABOUR / BOUFET roster, phase 2

import type { EmployeeSummary } from '@/attendance';

/* ------------------------------- Enums -------------------------------- */

/** Meal window an entry belongs to. */
export type CanteenMealType = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'TEA';

/** How the entry reached the ledger. Mirrors the punch-source idea. */
export type CanteenEntrySource =
  | 'DEVICE'
  | 'MANUAL'
  | 'BARCODE_UPLOAD'
  | 'MANUAL_CARD_UPLOAD';

/**
 * Which sheet layout an upload carries. BARCODE sheets identify the diner
 * by the scanned barcode value; MANUAL_CARD sheets by the printed card
 * number the canteen clerk wrote down.
 */
export type CanteenUploadType = 'BARCODE' | 'MANUAL_CARD';

/** Canteen-side staffing bucket. Not the same as the payroll `GradeType`. */
export type CanteenEmployeeCategory = 'LABOUR' | 'BOUFET';

/* ---------------------------- Meal ledger ----------------------------- */

export type CanteenAttendanceDto = {
  id: number;
  employeeId: number;
  /** YYYY-MM-DD — the canteen day, not the payroll work-date. */
  attendanceDate: string;
  /** HH:mm:ss local. */
  attendanceTime: string;
  /** Meals this row represents. Device rows are 1; sheet rows carry theirs. */
  count: number;
  mealType: CanteenMealType | null;
  source: CanteenEntrySource;
  /** Set for DEVICE rows; null for hand-entered and uploaded ones. */
  deviceId: number | null;
  deviceCode: string | null;
  /** Barcode / card value the row was matched on, when it came off a sheet. */
  cardNo: string | null;
  remarks: string | null;
  createdAt: string | null;
  createdBy: string | null;
};

/** List row — ledger entry plus the inline employee summary. */
export type CanteenAttendanceRow = {
  entry: CanteenAttendanceDto;
  employee: EmployeeSummary | null;
};

export type CreateCanteenAttendanceRequest = {
  employeeId: number;
  attendanceDate: string;
  attendanceTime: string;
  deviceId?: number;
  remarks?: string;
};

/**
 * Partial update of a ledger row. Only the clock time and the count are
 * editable: who the entry belongs to, which day it falls on, and how it was
 * recorded are all history rather than settings. An entry on the wrong day
 * or against the wrong person is deleted and re-added, so the trail stays
 * honest about what was originally captured.
 */
export type UpdateCanteenAttendanceRequest = {
  attendanceTime?: string;
  count?: number;
};

/* ------------------------- Spreadsheet import ------------------------- */

/**
 * Spreadsheet import payload. The frontend parses the workbook and sends a
 * flat identifier → count map, not the file itself.
 *
 * Because it IS a map, an identifier can only appear once: duplicate rows in
 * the sheet are summed as they are parsed, so two lines of `8761` with counts
 * 2 and 5 arrive as `{"8761": 7}` rather than silently collapsing to the last
 * one. Merged rows are flagged in the preview so the operator sees it happen.
 */
export type CanteenExcelUploadRequest = {
  /** MANUAL_CARD sheets: employee code → meal count. */
  excelUploadByEmployeeCode?: Record<string, number>;
  /** BARCODE sheets: barcode → meal count. */
  excelUploadByBarCode?: Record<string, number>;
  /** Stamped on every entry in the batch. */
  attendanceDate: string;
  attendanceTime: string;
};

/**
 * Per-identifier outcome, keyed by the same code that was sent. Both maps
 * can run to thousands of entries on a full-day sheet, so the UI groups,
 * filters and paginates them rather than listing them raw.
 */
export type CanteenExcelUploadResponse = {
  /** code → confirmation message. */
  success?: Record<string, string>;
  /** code → reason it was rejected. */
  error?: Record<string, string>;
};

/* ------------------------------- Usage -------------------------------- */

/**
 * `GET /canteen/attendance/employee/{id}/summary?from&to`.
 *
 * The two counts answer different questions and are both worth showing:
 * `attendanceEntryCount` is how many ledger rows exist in the range, while
 * `canteenMealCount` sums those rows' `count` values. Four visits that
 * covered eight meals gives 4 and 8 — the gap is the double helpings.
 */
export type CanteenUsageDto = {
  employeeId: number;
  employeeCode: string | null;
  /** Ledger rows in [from, to]. */
  attendanceEntryCount: number;
  /** Total meals across those rows. */
  canteenMealCount: number;
};



/* ----------------------------- Roster --------------------------------- */

export type CanteenEmployeeDto = {
  id: number;
  employeeCode: string | null;
  fullName: string;
  category: CanteenEmployeeCategory;
  departmentId: number | null;
  departmentName: string | null;
  sectionName: string | null;
  designationName: string | null;
  gradeName: string | null;
  /** Canteen card / barcode value issued to the employee. */
  cardNo: string | null;
  profilePictureUrl: string | null;
  active: boolean;
};
