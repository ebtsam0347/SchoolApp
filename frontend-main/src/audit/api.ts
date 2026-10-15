// Typed wrappers for audit-service compliance reads + admin operations.
// Mirrors `docs/services/audit.openapi.json`. The user-facing
// /timeline/{entityType}/{entityId} read for "my events" already lives in
// `src/auth/api.ts` as `timelineApi`; here we surface the admin / compliance
// views: search, by-entity (full history), by-actor, by-correlation,
// consumer-lag health, and bounded replay.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';

export type AuditEventDto = {
  id: number;
  eventId: string;
  sourceService: string;
  entityType: string;
  entityId: string;
  action: string;
  actorUserId: number | null;
  actorUsername: string | null;
  actorLabel: string | null;
  before: unknown;
  after: unknown;
  changes: unknown;
  metadata: unknown;
  correlationId: string;
  ipAddress: string | null;
  userVisible: boolean;
  message: string | null;
  occurredAt: string;
  ingestedAt: string;
};

export type PageResponseAuditEventDto = {
  content: AuditEventDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: string;
};

export type ByEntityPage = {
  content: AuditEventDto[];
  nextCursor: string | null;
};

export type SearchAuditParams = {
  q?: string;
  entityType?: string;
  action?: string;
  /** Exact-match correlation id; deep-link from the drawer's "View by correlation". */
  correlationId?: string;
  /** Filter to a single actor across the global feed. */
  actorUserId?: number;
  /** Tri-state: omit = both, true = user-visible only, false = internal only. */
  userVisible?: boolean;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
};

export type ByActorParams = {
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
};

export type ByEntityParams = {
  from?: string;
  to?: string;
  limit?: number;
  cursor?: string;
};

export type Facet = {
  value: string;
  count: number;
};

export type FacetsResponse = {
  /** Distinct entity_type values in audit_events, ordered by frequency. */
  entityTypes: Facet[];
  actions: Facet[];
  sourceServices: Facet[];
};

export type ReplayRequest = {
  topic: string;
  fromOffset: number;
  toOffset: number;
  partition?: number;
  dryRun?: boolean;
  /**
   * Returned by a prior `dryRun: true` call (5-min single-use). Required for
   * `dryRun: false`. The UI handles this transparently — the operator just
   * runs dry first, then commits.
   */
  replayToken?: string;
  /**
   * `dryRun: true` + `countOnly: true` skips Kafka polling and returns an
   * upper-bound row count. Drives the "About to replay ~412 records" prompt.
   */
  countOnly?: boolean;
};

export type ReplayResult = {
  replayed: number;
  skipped: number;
  errors: number;
  dryRun: boolean;
  /** Set on a `dryRun: true` response. Hand it back on the real run. */
  replayToken?: string;
  /** Echoes the request — true on a count-only (cheap upper-bound estimate). */
  countOnly?: boolean;
};

export type PartitionHealth = {
  partition: number;
  endOffset: number;
  committedOffset: number;
  lag: number;
};

export type TopicHealth = {
  topic: string;
  consumerGroup: string;
  totalLag: number;
  partitions: PartitionHealth[];
};

export type HealthResponse = {
  topics: TopicHealth[];
};

export type DeadLetterReason =
  | 'MALFORMED_JSON'
  | 'MISSING_EVENT_ID'
  | 'OVERSIZED_EVENT_ID'
  | 'MISSING_REQUIRED_PAYLOAD'
  | 'UNPARSEABLE_OCCURRED_AT';

export type AuditDeadLetterDto = {
  id: number;
  sourceService: string;
  topic: string;
  partition: number;
  offset: number;
  key: string | null;
  reason: DeadLetterReason;
  reasonDetail: string | null;
  receivedAt: string;
  lastReplayAt: string | null;
  replayAttempts: number;
  /** Raw envelope JSON. Listing endpoint omits this; detail endpoint includes it. */
  rawPayload: string | null;
};

export type PageResponseAuditDeadLetterDto = {
  content: AuditDeadLetterDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: string;
};

export type ListDeadLettersParams = {
  /** Filter by source service (auth, employee, …). */
  source?: string;
  /** Filter by skip reason. */
  reason?: DeadLetterReason;
  /** Inclusive lower bound on receivedAt (ISO-8601). */
  since?: string;
};

export type DeadLetterReplayStatus = 'INSERTED' | 'DUPLICATE' | 'STILL_FAILING';

export type DeadLetterReplayResult = {
  status: DeadLetterReplayStatus;
  /** Set when STILL_FAILING. */
  reason: DeadLetterReason | null;
  /** Set when STILL_FAILING. */
  detail: string | null;
};

export const auditApi = {
  search: (params: SearchAuditParams = {}, signal?: AbortSignal) =>
    http.get<PageResponseAuditEventDto>(
      `/audit/search${qs({
        q: params.q,
        entityType: params.entityType,
        action: params.action,
        correlationId: params.correlationId,
        actorUserId: params.actorUserId,
        userVisible: params.userVisible,
        from: params.from,
        to: params.to,
        page: params.page,
        size: params.size,
        sort: params.sort,
      })}`,
      { signal },
    ),
  /** Single-event detail; service owns redaction. */
  event: (id: number, signal?: AbortSignal) =>
    http.get<AuditEventDto>(`/audit/events/${id}`, { signal }),
  byEntity: (
    entityType: string,
    entityId: string,
    params: ByEntityParams = {},
    signal?: AbortSignal,
  ) =>
    http.get<ByEntityPage>(
      `/audit/by-entity/${encodeURIComponent(entityType)}/${encodeURIComponent(entityId)}${qs({
        from: params.from,
        to: params.to,
        limit: params.limit,
        cursor: params.cursor,
      })}`,
      { signal },
    ),
  byActor: (userId: number, params: ByActorParams = {}, signal?: AbortSignal) =>
    http.get<PageResponseAuditEventDto>(
      `/audit/by-actor/${userId}${qs({
        from: params.from,
        to: params.to,
        page: params.page,
        size: params.size,
        sort: params.sort,
      })}`,
      { signal },
    ),
  byCorrelation: (correlationId: string, signal?: AbortSignal) =>
    http.get<AuditEventDto[]>(
      `/audit/by-correlation/${encodeURIComponent(correlationId)}`,
      { signal },
    ),
  /** Distinct entity_type / action / source_service values, frequency-ordered. */
  facets: (signal?: AbortSignal) =>
    http.get<FacetsResponse>('/audit/facets', { signal }),
  health: (signal?: AbortSignal) =>
    http.get<HealthResponse>('/admin/audit/health', { signal }),
  replay: (body: ReplayRequest) =>
    http.post<ReplayResult>('/admin/audit/replay', body),
  deadLetters: {
    list: (params: ListDeadLettersParams = {}, signal?: AbortSignal) =>
      http.get<PageResponseAuditDeadLetterDto>(
        `/admin/audit/dead-letters${qs({
          source: params.source,
          reason: params.reason,
          since: params.since,
        })}`,
        { signal },
      ),
    get: (id: number, signal?: AbortSignal) =>
      http.get<AuditDeadLetterDto>(`/admin/audit/dead-letters/${id}`, { signal }),
    replay: (id: number) =>
      http.post<DeadLetterReplayResult>(`/admin/audit/dead-letters/${id}/replay`),
    discard: (id: number) => http.del<void>(`/admin/audit/dead-letters/${id}`),
  },
};
