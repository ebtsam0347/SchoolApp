// Per-employee PDF reports: the employee strip's Print menu, the
// appointment-letter drawer, the salary-certificate modal, and their API
// wrappers.

export { EmployeePrintMenu } from './EmployeePrintMenu';
export {
  SalaryCertificateModal,
  type SalaryCertificateModalProps,
} from './SalaryCertificateModal';
export {
  AppointmentLetterDrawer,
  type AppointmentLetterDrawerProps,
} from './AppointmentLetterDrawer';
export {
  EMPLOYEE_REPORT_IDS,
  employeeReportsApi,
  useReportCatalogIds,
  type AppointmentLetterMissingField,
  type AppointmentLetterOverrides,
  type EmployeeReportId,
  type SalaryCertificateOptions,
} from './api';
