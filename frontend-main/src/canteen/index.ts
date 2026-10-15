// Barrel for the Canteen module — types + API wrappers + display maps +
// the shared surfaces the /canteen routes mount.

export * from './types';
export {
  canteenAttendanceApi,
  canteenDevicesApi,
  canteenEmployeesApi,
  canteenUsageApi,
  type ListCanteenAttendanceParams,
  type ListCanteenEmployeesParams,
} from './api';
export {
  MEAL_TYPE_LABEL,
  MEAL_TYPE_TONE,
  CANTEEN_SOURCE_LABEL,
  CANTEEN_SOURCE_TONE,
  UPLOAD_TYPE_LABEL,
  CANTEEN_CATEGORY_LABEL,
  CANTEEN_CATEGORY_TONE,
  isCanteenMealType,
  isCanteenEntrySource,
  isCanteenCategory,
  formatCanteenTime,
} from './format';
export { CanteenTabs } from './CanteenTabs';
export { PendingBackendNotice } from './PendingBackendNotice';
export type { PendingBackendNoticeProps } from './PendingBackendNotice';
export { CanteenEmployeeLookup } from './CanteenEmployeeLookup';
export type { CanteenEmployeeLookupProps } from './CanteenEmployeeLookup';
export { AddCanteenAttendanceDrawer } from './AddCanteenAttendanceDrawer';
export type { AddCanteenAttendanceDrawerProps } from './AddCanteenAttendanceDrawer';
export { EditCanteenAttendanceDrawer } from './EditCanteenAttendanceDrawer';
export type { EditCanteenAttendanceDrawerProps } from './EditCanteenAttendanceDrawer';
export { CanteenUploadResult } from './CanteenUploadResult';
export type { CanteenUploadResultProps } from './CanteenUploadResult';
export { UploadCanteenAttendanceDrawer } from './UploadCanteenAttendanceDrawer';
export type { UploadCanteenAttendanceDrawerProps } from './UploadCanteenAttendanceDrawer';
