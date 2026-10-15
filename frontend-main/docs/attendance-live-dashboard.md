# Attendance operator dashboard — live monitoring

Extends the existing `/attendance` operator dashboard (`src/routes/_app/attendance/index.tsx`)
with a distribution donut, a department-wise breakdown, a live punch feed, and a
recent-activity timeline. Built **frontend-only** — no backend changes — so updates
are **near-real-time via polling**, with the SSE path stubbed for a later step.

## What was added

| Piece | Component | Data source (existing endpoint) |
|---|---|---|
| Distribution donut | `DistributionCard` | derived from `GET /attendance/operator/dashboard` pulse (no extra call) |
| Department-wise split | `DepartmentBreakdownCard` | `GET /attendance/records/with-employee` (≤500 rows), aggregated client-side |
| Live punch feed | `LiveAttendanceFeed` | `GET /attendance/punches/with-employee?sort=punchedAt,desc` |
| Recent activity | `RecentActivityTimeline` | same punch query as the feed (single fetch) |
| Filters | `FilterBar` + `catalogsApi` + `EmployeePicker` | scope the live block only |

Pure derivation logic lives in `src/attendance/admin/dashboard-live.ts`
(`deriveDistribution`, `aggregateDepartmentBreakdown`) so it's testable and reusable.

## How "real-time" works today

- On `/attendance/live` (the wall-board), the summary (present/absent/on leave),
  the recent-activity feed, and the department counts all share the same
  `RECENT_ACTIVITY_REFETCH_MS` (3s) cadence — deliberately, so the three panels
  refresh together rather than drifting apart on independent timers. New feed
  rows and department counts that tick up flash briefly (`dashboard-live.css`,
  `AnimatedNumber`) so it's visible *which* department just changed.
- On the (non-live) operator dashboard, the department breakdown card polls
  every `DEPARTMENT_REFETCH_MS` (45s).
- `attendanceStreamHandlers` (`src/attendance/stream-handlers.ts`) is already
  registered on the single multiplexed SSE connection in `src/routes/_app.tsx`.
  It's **inert** until the backend forwards `attendance.*` events — the moment it
  does, the dashboard invalidates and refreshes instantly (polling becomes a
  fallback). True zero-delay push still needs that backend event; polling is the
  ceiling until then.

## Backend contract to reach true push (deferred)

Mirror the existing `chat.*` / `presence.changed` precedent
(`NotificationStreamService.java`): publish these events onto
`/bff/notifications/stream`. Payload may be empty — the FE refetches rather than
trusting the wire.

| Event | Emitted when |
|---|---|
| `attendance.punch` | a punch is recorded (check-in / check-out) |
| `attendance.updated` | a record is amended / regularized / operator-punched |
| `attendance.leave` | a leave is approved or cancelled (moves on-leave) |

## Known limitation

The department breakdown aggregates the first 500 records for the day and flags
the sample as partial beyond that. The scalable fix is a server-side aggregate,
e.g. `GET /attendance/operator/department-breakdown?date=` returning per-department
present/absent/leave counts — then `DepartmentBreakdownCard` swaps its query with
no UI change.
