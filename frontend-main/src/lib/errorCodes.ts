// Frontend mirror of every backend `code` we explicitly switch on. Backend is
// the source of truth — unknown codes from the wire still parse fine; this
// list only narrows the type when you compare against a known one.
//
// Sources: docs/api-conventions.md §5 + per-service docs. Keep alphabetised
// within each group so duplicates are obvious.

export const KNOWN_ERROR_CODES = [
  // Common (commons-core / commons-web — every service)
  'DEPENDENCY_UNAVAILABLE',
  'FORBIDDEN',
  'GATEWAY_TIMEOUT', // synthetic — fired by the client when a request exceeds DEFAULT_TIMEOUT_MS
  'IDEMPOTENCY_REPLAY',
  'INTERNAL_ERROR',
  'NETWORK_ERROR', // synthetic — fired by the client when fetch fails (offline, DNS, gateway down)
  'RATE_LIMITED',
  'UNAUTHENTICATED',
  'VALIDATION_FAILED',

  // auth-service
  'EMPLOYEE_LINKED_NAME_READONLY',
  'INVALID_CREDENTIALS',
  'PASSWORD_POLICY_VIOLATION',
  'PASSWORD_REUSE_NOT_ALLOWED',
  'USER_DISABLED',
  'USER_LOCKED',

  // employee-service
  'EMPLOYEE_NOT_FOUND',

  // attendance-service (leave / overtime / regularization)
  'LEAVE_OVERLAPS_EXISTING',
  'OT_HOURS_OUT_OF_RANGE',
] as const

export type KnownErrorCode = (typeof KNOWN_ERROR_CODES)[number]

export const isKnownCode = (code: string): code is KnownErrorCode =>
  (KNOWN_ERROR_CODES as readonly string[]).includes(code)
