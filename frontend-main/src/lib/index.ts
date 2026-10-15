export {
  KNOWN_ERROR_CODES,
  isKnownCode,
  type KnownErrorCode,
} from './errorCodes';

export {
  ProblemDetailSchema,
  ViolationSchema,
  type ProblemDetail,
  type Violation,
} from './problem-detail';

export {
  ApiError,
  isApiError,
  isForbidden,
  isNotFound,
  forbiddenOr,
  hasCode,
  parseProblemDetail,
} from './api-error';

export { http, request } from './http';
export { qs } from './qs';
export { enumLabel, matches, matchesAny } from './strings';
export type { Page } from './page';
export { queryClient } from './query';
export { getErrorMessage } from './errors';
export {
  violationsToFields,
  applyApiErrorToForm,
  handleFormError,
} from './form-violations';
export {
  readSnapshot,
  writeSnapshot,
  clearSnapshot,
  subscribe as subscribeAuthStorage,
  type AuthSnapshot,
} from './auth-storage';

export { useDebounced } from './use-debounced';
export { useUrlDrawerId } from './use-url-drawer-id';
export { useMediaQuery, useIsDesktop } from './use-media-query';
export { useTableColumns } from './use-table-columns';
export type {
  ColumnPrefsOption,
  UseTableColumnsResult,
  UseTableColumnsOptions,
} from './use-table-columns';

export { readJwtExpMs } from './jwt';
export { downloadCsv } from './csv';

export {
  formatDateShort,
  formatDateLong,
  formatDateMonthDay,
  formatDateTime,
  formatDateNumeric,
  formatDateTimeNumeric,
  toLocalDateTime,
  parseDateOnly,
  toIsoDate,
  toIsoMonth,
  daysUntil,
  daysSince,
  daysBetween,
} from './dates';
