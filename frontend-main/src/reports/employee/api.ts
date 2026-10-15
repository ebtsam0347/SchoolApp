// Per-employee PDF reports: personnel file, department strength, the
// letters, certificates and the ID card. Employee-service ones sit behind
// `employee.reports.run` (managers only reach their direct reports; anyone
// else gets 404); the salary certificate comes from payroll-service.
// Every action is gated
// on its catalog id being present for the caller, not on a local
// permission check — the catalog is already filtered server-side.

import { useQuery } from '@tanstack/react-query';

import { toIsoDate } from '@/lib';
import { qs } from '@/lib/qs';

import { fetchReportCatalog } from '../bff-api';
import { downloadReportBlob, fetchReportFile, type ReportFile } from '../download';

export const EMPLOYEE_REPORT_IDS = {
  profile: 'employee.profile',
  idCard: 'employee.id-card',
  departmentStrength: 'employee.department-strength',
  appointmentLetter: 'employee.appointment-letter',
  serviceLetter: 'employee.service-letter',
  experienceLetter: 'employee.experience-letter',
  confirmationLetter: 'employee.confirmation-letter',
  relievingLetter: 'employee.relieving-letter',
  characterCertificate: 'employee.character-certificate',
  salaryCertificate: 'payroll.salary-certificate',
} as const;

export type EmployeeReportId =
  (typeof EMPLOYEE_REPORT_IDS)[keyof typeof EMPLOYEE_REPORT_IDS];

/** Same fallback shape the catalog runner uses: `{reportId}-{yyyy-mm-dd}.pdf`. */
function fallbackName(reportId: EmployeeReportId): string {
  return `${reportId}-${toIsoDate(new Date())}.pdf`;
}

const PDF = { accept: 'application/pdf' } as const;

/**
 * Catalog ids the caller may run. Shares the /reports page's query key so
 * the catalog is fetched once. A failed or forbidden catalog read yields an
 * empty set, which simply hides every action.
 */
export function useReportCatalogIds(): Set<string> {
  const q = useQuery({
    queryKey: ['reports', 'catalog'],
    queryFn: ({ signal }) => fetchReportCatalog({ signal }),
    staleTime: 5 * 60 * 1000,
  });
  return new Set((q.data ?? []).map((e) => e.id));
}

/** Optional overrides; omitted fields fall back to the server defaults. */
export type AppointmentLetterOverrides = {
  letterDate?: string;
  reportingDate?: string;
  probationMonths?: number;
  includeSalary?: boolean;
  refNo?: string;
};

/** Field names the server may list in a 422 `missingFields`. */
export type AppointmentLetterMissingField =
  | 'fullName'
  | 'designation'
  | 'department'
  | 'joiningDate'
  | 'basicSalary';

/** Optional salary-certificate wording; empty fields are not sent. */
export type SalaryCertificateOptions = {
  /** Omitted → "To Whom It May Concern". */
  addressee?: string;
  purpose?: string;
};

export const employeeReportsApi = {
  /** Personnel file, for preview. */
  profile: (employeeId: number, signal?: AbortSignal): Promise<ReportFile> =>
    fetchReportFile(
      `/reports/employees/${employeeId}/profile`,
      fallbackName(EMPLOYEE_REPORT_IDS.profile),
      { ...PDF, signal },
    ),

  /** 422 with `missingFields: ["confirmationDate"]` when not yet confirmed. */
  confirmationLetter: (employeeId: number, signal?: AbortSignal): Promise<ReportFile> =>
    fetchReportFile(
      `/reports/employees/${employeeId}/confirmation-letter`,
      fallbackName(EMPLOYEE_REPORT_IDS.confirmationLetter),
      { ...PDF, signal },
    ),

  /** 422 while the employee is still in service or has no last working day. */
  relievingLetter: (employeeId: number, signal?: AbortSignal): Promise<ReportFile> =>
    fetchReportFile(
      `/reports/employees/${employeeId}/relieving-letter`,
      fallbackName(EMPLOYEE_REPORT_IDS.relievingLetter),
      { ...PDF, signal },
    ),

  /** 422 with `disciplinaryActions: [ids]` when there's a record on file. */
  characterCertificate: (employeeId: number, signal?: AbortSignal): Promise<ReportFile> =>
    fetchReportFile(
      `/reports/employees/${employeeId}/character-certificate`,
      fallbackName(EMPLOYEE_REPORT_IDS.characterCertificate),
      { ...PDF, signal },
    ),

  /** Payroll-service. 422 EMPLOYEE_INACTIVE or NO_BASIC_SALARY. */
  salaryCertificate: (
    employeeId: number,
    options: SalaryCertificateOptions,
    signal?: AbortSignal,
  ): Promise<ReportFile> =>
    fetchReportFile(
      `/reports/payroll/employees/${employeeId}/salary-certificate${qs({
        addressee: options.addressee?.trim(),
        purpose: options.purpose?.trim(),
      })}`,
      fallbackName(EMPLOYEE_REPORT_IDS.salaryCertificate),
      { ...PDF, signal },
    ),

  downloadDepartmentStrength: () =>
    downloadReportBlob(
      '/reports/employees/department-strength',
      fallbackName(EMPLOYEE_REPORT_IDS.departmentStrength),
      PDF,
    ),

  /** 409 when the employee is TERMINATED. */
  downloadServiceLetter: (employeeId: number) =>
    downloadReportBlob(
      `/reports/employees/${employeeId}/service-letter`,
      fallbackName(EMPLOYEE_REPORT_IDS.serviceLetter),
      PDF,
    ),

  /** 409 unless the employee is TERMINATED. */
  downloadExperienceLetter: (employeeId: number) =>
    downloadReportBlob(
      `/reports/employees/${employeeId}/experience-letter`,
      fallbackName(EMPLOYEE_REPORT_IDS.experienceLetter),
      PDF,
    ),

  /**
   * CR80 ID card (one card per page) for preview. Server filename is
   * `employee-card-{id}.pdf`.
   */
  idCard: (employeeId: number, signal?: AbortSignal): Promise<ReportFile> =>
    fetchReportFile(
      `/reports/employees/${employeeId}/id-card`,
      fallbackName(EMPLOYEE_REPORT_IDS.idCard),
      { ...PDF, signal },
    ),

  /**
   * Render the appointment letter for preview. POST with only the fields
   * that are set; deliberately no Idempotency-Key (see fetchReportFile).
   * 422 APPOINTMENT_LETTER_DATA_MISSING carries `missingFields`; 403 means
   * salary was requested without `payroll.read`; 503 means payroll is down.
   */
  appointmentLetter: (
    employeeId: number,
    overrides: AppointmentLetterOverrides,
    signal?: AbortSignal,
  ): Promise<ReportFile> =>
    fetchReportFile(
      `/reports/employees/${employeeId}/appointment-letter`,
      fallbackName(EMPLOYEE_REPORT_IDS.appointmentLetter),
      { ...PDF, body: overrides, signal },
    ),
};
