// User-facing message for each known error code. The map is intentionally
// small — most server messages (`title` + `detail`) are already user-grade
// and rendered as-is via getErrorMessage's fallback.
//
// Add an entry here only when:
//   - the server's wording is too technical / leaks an implementation detail,
//   - the UI needs to swap copy based on context (e.g. show a "go to settings"
//     CTA on USER_LOCKED), or
//   - a non-2xx is expected and we want a friendlier voice.

import { ApiError, isApiError } from './api-error';
import { isKnownCode } from './errorCodes';

const messages: Partial<Record<string, string>> = {
  // Common
  UNAUTHENTICATED: 'Your session has ended. Please sign in again.',
  FORBIDDEN: "You don't have permission to do this.",
  RATE_LIMITED: 'Too many requests — slow down for a moment.',
  DEPENDENCY_UNAVAILABLE: 'A required service is briefly unavailable.',
  GATEWAY_TIMEOUT: "The server didn't respond in time. Try again.",
  INTERNAL_ERROR: 'Something went wrong. Please try again.',
  NETWORK_ERROR: "Couldn't reach the server. Check your connection.",
  VALIDATION_FAILED: 'Some fields need attention.',

  // Auth-specific
  USER_DISABLED: 'This account is disabled. Contact your HR admin.',
  USER_LOCKED:
    'This account is temporarily locked due to repeated sign-in failures. Try again in 15 minutes or ask HR to unlock.',
  EMPLOYEE_LINKED_NAME_READONLY:
    'Your name is managed by HR — ask them to update your employee record.',
  INVALID_CREDENTIALS: 'Incorrect username or password.',
  PASSWORD_POLICY_VIOLATION:
    'New password does not meet the security requirements.',
  PASSWORD_REUSE_NOT_ALLOWED: 'You cannot reuse a recent password.',

  // Factory Tour
  TOUR_VALIDATION_FAILED:
    "Your account isn't linked to an employee record, so a travel request can't be created. Contact HR to get set up.",
};

export function getErrorMessage(e: unknown): string {
  if (isApiError(e)) {
    const code = e.code;
    // VALIDATION_FAILED normally means per-field violations are already
    // shown inline (see applyApiErrorToForm), so the generic copy below is
    // just a pointer at those. But some VALIDATION_FAILED responses are a
    // flat business-rule rejection with no violations[] to show inline
    // (e.g. "Belonging name X already exists") — for those the specific
    // `detail` is the only explanation the user gets, so don't shadow it.
    if (code === 'VALIDATION_FAILED' && e.violations.length === 0) {
      return e.problem.detail ?? e.problem.title ?? 'Request failed.';
    }
    if (isKnownCode(code) && messages[code]) return messages[code]!;
    if (messages[code]) return messages[code]!;
    return e.problem.detail ?? e.problem.title ?? 'Request failed.';
  }
  if (e instanceof Error) return e.message;
  return 'Request failed.';
}

export { ApiError };
