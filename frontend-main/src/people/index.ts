// Barrel for the People module — types + API wrappers + cache helpers.

export * from './types';
export * from './api';
export * from './bff-api';
export {
  detailQueryKey,
  invalidateEmployeeReads,
  patchEmployeeDetail,
} from './cache';
export { EmployeeHero } from './EmployeeHero';
export type { EmployeeHeroProps } from './EmployeeHero';
export {
  ProfilePictureUploader,
  type ProfilePictureUploaderProps,
} from './ProfilePictureUploader';
export {
  useMyEmployee,
  invalidateMyProfilePicture,
  MY_EMPLOYEE_QUERY_KEY,
} from './use-my-employee';
export { EmployeePicker, type EmployeePickerProps } from './EmployeePicker';
export {
  employeePickerApi,
  type EmployeePickerCandidate,
  type EmployeePickerResponse,
} from './employee-picker';
export { formatCityWithProvince, formatCountry } from './format';
