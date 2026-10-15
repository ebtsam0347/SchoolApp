import {
  ProblemDetailSchema,
  type ProblemDetail,
  type Violation,
} from './problem-detail'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly correlationId: string
  readonly violations: readonly Violation[]
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title)
    this.name = 'ApiError'
    this.status = problem.status
    this.code = problem.code
    this.correlationId = problem.correlationId
    this.violations = problem.violations ?? []
    this.problem = problem
  }
}

export const isApiError = (e: unknown): e is ApiError => e instanceof ApiError

/** Convenience: did the request fail with a 403? */
export const isForbidden = (e: unknown): boolean =>
  isApiError(e) && e.status === 403

/** Convenience: did the request fail with a 404? */
export const isNotFound = (e: unknown): boolean =>
  isApiError(e) && e.status === 404

// True only when the server explicitly rejected our credentials. Used to
// decide whether a failed /auth/me or /auth/refresh should drop the session.
// Network blips, gateway 5xx, and client-side timeouts are NOT auth failures
// — clearing the session on those forces the user to re-login during any
// transient backend hiccup.
export const isAuthFailure = (e: unknown): boolean =>
  isApiError(e) && (e.status === 401 || e.status === 403)

/**
 * Pick a permission-aware copy. Returns `forbiddenMsg` if the error is a
 * 403, otherwise `genericMsg`. Replaces the long ternary repeated across
 * every screen: `isApiError(q.error) && q.error.status === 403 ? "..." : "..."`
 */
export function forbiddenOr(
  e: unknown,
  forbiddenMsg: string,
  genericMsg: string,
): string {
  return isForbidden(e) ? forbiddenMsg : genericMsg
}

export const hasCode = <C extends string>(
  e: unknown,
  code: C,
): e is ApiError & { code: C } => isApiError(e) && e.code === code

// Always returns an ApiError — synthesises a generic INTERNAL_ERROR if the
// body is missing, non-JSON, or doesn't match the contract. We never want a
// caller to have to handle "what if the error itself was malformed".
export async function parseProblemDetail(
  response: Response,
): Promise<ApiError> {
  let body: unknown
  try {
    body = await response.json()
  } catch {
    body = null
  }

  const parsed = ProblemDetailSchema.safeParse(body)
  if (parsed.success) return new ApiError(parsed.data)

  // A body that carries our `code` but omits the RFC 7807 boilerplate
  // (`type` / `title`) is still a real ProblemDetail — keep its code,
  // detail and violations instead of collapsing it to INTERNAL_ERROR.
  if (body && typeof body === 'object' && typeof (body as { code?: unknown }).code === 'string') {
    const filled = ProblemDetailSchema.safeParse({
      type: 'about:blank',
      title: response.statusText || 'Request failed',
      status: response.status,
      correlationId: response.headers.get('X-Correlation-Id') ?? 'unknown',
      timestamp: new Date().toISOString(),
      ...(body as Record<string, unknown>),
    })
    if (filled.success) return new ApiError(filled.data)
  }

  return new ApiError({
    type: 'about:blank',
    title: response.statusText || 'Request failed',
    status: response.status,
    code: 'INTERNAL_ERROR',
    correlationId: response.headers.get('X-Correlation-Id') ?? 'unknown',
    timestamp: new Date().toISOString(),
  })
}
