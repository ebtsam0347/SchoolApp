// Barrel for the attendance operator module-dir — shared helpers used by
// the /attendance/{records,punches} browser routes.

export { AttendanceTabs } from './AttendanceTabs';
export { EmployeeSummaryCell } from './EmployeeSummaryCell';
export type { EmployeeSummaryCellProps } from './EmployeeSummaryCell';
export {
  AttendanceDayDrawer,
  AttendanceRecordDayBody,
  AttendanceRecordDrawer,
  PunchDayBody,
  PunchRecordDrawer,
  DetailTile,
} from './AttendanceDayDrawer';
export type { AttendanceDayDrawerProps, AttendanceRecordRow } from './AttendanceDayDrawer';
export { PunchAmendModal } from './PunchAmendModal';
export type { PunchAmendModalProps } from './PunchAmendModal';
export { PunchDeleteModal } from './PunchDeleteModal';
export type { PunchDeleteModalProps } from './PunchDeleteModal';
export { BulkOperatorPunchDrawer } from './BulkOperatorPunchDrawer';
export type { BulkOperatorPunchDrawerProps } from './BulkOperatorPunchDrawer';
export { DeviceDetailDrawer } from './DeviceDetailDrawer';
export type { DeviceDetailDrawerProps } from './DeviceDetailDrawer';
export { PunchAddForRecordModal } from './PunchAddForRecordModal';
export type { PunchAddForRecordModalProps } from './PunchAddForRecordModal';
export { DistributionCard } from './DistributionCard';
export type { DistributionCardProps } from './DistributionCard';
export { DepartmentBreakdownCard } from './DepartmentBreakdownCard';
export type {
  DepartmentBreakdownCardProps,
  DepartmentBreakdownFilters,
} from './DepartmentBreakdownCard';
export { LiveAttendanceFeed } from './LiveAttendanceFeed';
export type { LiveAttendanceFeedProps } from './LiveAttendanceFeed';
export { RecentActivityTimeline } from './RecentActivityTimeline';
export type { RecentActivityTimelineProps } from './RecentActivityTimeline';
export { LiveCountersStrip } from './LiveCountersStrip';
export type { LiveCountersStripProps } from './LiveCountersStrip';
export { AnimatedNumber } from './AnimatedNumber';
export type { AnimatedNumberProps } from './AnimatedNumber';
export { FlipNumber } from './FlipNumber';
export type { FlipNumberProps } from './FlipNumber';
export {
  DEPARTMENT_REFETCH_MS,
  RECENT_ACTIVITY_REFETCH_MS,
  DEPARTMENT_SAMPLE_SIZE,
  aggregateDepartmentBreakdown,
  type DepartmentMetric,
  type DepartmentAttendanceRow,
} from './dashboard-live';
export {
  numOrUndef,
  strOrUndef,
  boolOrUndef,
  isAttendanceStatus,
  isPunchSource,
  collapseDateRange,
} from './filter-utils';
