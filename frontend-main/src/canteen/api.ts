// Typed wrappers for the canteen surface. Routed through the gateway via
// `src/lib/http.ts` like every other module (bearer + idempotency +
// 401-refresh-retry).
//
// Two of the four groups below are ahead of the backend by design:
//
//   canteenAttendanceApi  → /canteen/attendance{,/upload-excel/*}
//       The meal ledger. Filters mirror /attendance/punches/with-employee
//       (date range + employeeId/employeeCode + dept/section) plus the
//       canteen-only knobs (mealType, source, deviceId).
//
//   canteenDevicesApi     → /canteen/attendance/devices
//
//   canteenEmployeesApi   → /canteen/employees  (phase 2)
//       LABOUR / BOUFET roster. No sensible fallback — BOUFET has no
//       equivalent in the employee-service grade catalog — so callers get
//       an explicit "not wired yet" state instead of a wrong list.
//
//   canteenUsageApi       → /canteen/attendance/employee/{id}/summary

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type { Page } from '@/lib/page';
import type { AttendanceDeviceDto, EmployeeSummary } from '@/attendance';
import type {
  CanteenAttendanceDto,
  CanteenAttendanceRow,
  CanteenEmployeeCategory,
  CanteenEmployeeDto,
  CanteenEntrySource,
  CanteenMealType,
  CanteenExcelUploadRequest,
  CanteenExcelUploadResponse,
  CanteenUsageDto,
  CanteenUploadType,
  CreateCanteenAttendanceRequest,
  UpdateCanteenAttendanceRequest,
} from './types';

/* --------------------------- Meal ledger ------------------------------ */

export type ListCanteenAttendanceParams = {
  /**
   * Inclusive range. There is no single-day `date` shortcut here (unlike the
   * attendance BFF) — a one-day view sends the same value for both.
   */
  from?: string;
  to?: string;
  employeeId?: number;
  employeeCode?: string;
  departmentId?: number;
  sectionId?: number;
  mealType?: CanteenMealType;
  source?: CanteenEntrySource;
  deviceId?: number;
  /** Zero-based, Spring convention. */
  page?: number;
  size?: number;
  sort?: string;
};

/**
 * The ledger list is read through a normalizer because the wire shape isn't
 * pinned yet: the service may return rows already wrapped as
 * `{ entry, employee }`, or plain `CanteenAttendanceDto` rows with the
 * employee summary inlined as a sibling field — and either a Spring `Page`
 * or a bare array. Callers always get the wrapper shape, so the table never
 * has to guess (and never blows up reading `row.entry.id` off a flat row).
 */
function normalizeRow(raw: unknown): CanteenAttendanceRow {
  const row = (raw ?? {}) as Record<string, unknown>;
  if (row.entry && typeof row.entry === 'object') {
    return {
      entry: row.entry as CanteenAttendanceDto,
      employee: (row.employee as EmployeeSummary | null) ?? null,
    };
  }
  const { employee, ...entry } = row;
  return {
    entry: entry as unknown as CanteenAttendanceDto,
    employee: (employee as EmployeeSummary | null) ?? null,
  };
}

function normalizePage(raw: unknown): Page<CanteenAttendanceRow> {
  const empty: Page<CanteenAttendanceRow> = {
    content: [],
    page: 0,
    size: 0,
    totalElements: 0,
    totalPages: 0,
    sort: null,
  };
  if (!raw) return empty;
  if (Array.isArray(raw)) {
    const content = raw.map(normalizeRow);
    return { ...empty, content, size: content.length, totalElements: content.length, totalPages: 1 };
  }
  const p = raw as Partial<Page<unknown>>;
  const content = Array.isArray(p.content) ? p.content.map(normalizeRow) : [];
  return {
    content,
    page: p.page ?? 0,
    size: p.size ?? content.length,
    totalElements: p.totalElements ?? content.length,
    totalPages: p.totalPages ?? 1,
    sort: p.sort ?? null,
  };
}

export const canteenAttendanceApi = {
  list: (params: ListCanteenAttendanceParams = {}, signal?: AbortSignal) =>
    http
      .get<unknown>(`/canteen/attendance${qs({ ...params })}`, { signal })
      .then(normalizePage),

  create: (body: CreateCanteenAttendanceRequest) =>
    http.post<CanteenAttendanceRow>('/canteen/attendance', body),

  /** Correct a row in place. Time and count only — see the request type. */
  update: (id: number, body: UpdateCanteenAttendanceRequest) =>
    http
      .patch<unknown>(`/canteen/attendance/${id}`, body)
      .then(normalizeRow),

  /** Hard delete of one ledger row. */
  remove: (id: number) => http.del<void>(`/canteen/attendance/${id}`),

  /**
   * Spreadsheet import. The workbook is parsed in the browser and posted as
   * an identifier → count map; the response reports per-identifier success
   * and error messages keyed by that same identifier.
   *
   * The byEmployeeCode path and its `excelUploadByEmployeeCode` body key are
   * confirmed against the running service; the barcode pair is its assumed
   * sibling.
   */
  uploadExcel: (
    uploadType: CanteenUploadType,
    counts: Record<string, number>,
    params: { attendanceDate: string; attendanceTime: string },
  ) => {
    const byCode = uploadType === 'MANUAL_CARD';
    const body: CanteenExcelUploadRequest = {
      ...(byCode
        ? { excelUploadByEmployeeCode: counts }
        : { excelUploadByBarCode: counts }),
      attendanceDate: params.attendanceDate,
      attendanceTime: params.attendanceTime,
    };
    const path = byCode
      ? '/canteen/attendance/upload-excel/byEmployeeCode'
      : '/canteen/attendance/upload-excel/byBarCode';
    return http.post<CanteenExcelUploadResponse>(path, body);
  },
};

/* ----------------------------- Devices -------------------------------- */

export const canteenDevicesApi = {
  /** Every device the canteen desk is responsible for. Read-only surface. */
  list: (signal?: AbortSignal) =>
    http.get<AttendanceDeviceDto[]>('/canteen/attendance/devices', { signal }),
};

/* ----------------------------- Roster --------------------------------- */

export type ListCanteenEmployeesParams = {
  category?: CanteenEmployeeCategory;
  q?: string;
  departmentId?: number;
  active?: boolean;
  page?: number;
  size?: number;
  sort?: string;
};

export const canteenEmployeesApi = {
  list: (params: ListCanteenEmployeesParams = {}, signal?: AbortSignal) =>
    http.get<Page<CanteenEmployeeDto>>(
      `/canteen/employees${qs({ ...params })}`,
      { signal },
    ),
};

/* ------------------------------ Usage --------------------------------- */

export const canteenUsageApi = {
  /**
   * Per-employee canteen summary over an inclusive range. Callers pass the
   * month around whatever date they are working on, not "today", so the
   * numbers describe the period the entry actually lands in.
   */
  forEmployee: (
    employeeId: number,
    params: { from: string; to: string },
    signal?: AbortSignal,
  ) =>
    http.get<CanteenUsageDto>(
      `/canteen/attendance/employee/${employeeId}/summary${qs({ ...params })}`,
      { signal },
    ),
};
