// Attendance SSE event handlers.
//
// Forward-compatible: the BFF does NOT yet publish attendance.* events onto
// `/bff/notifications/stream`. This map is registered on the single multiplexed
// connection (see `src/routes/_app.tsx`) so that the moment the backend starts
// forwarding these events, the operator dashboard refreshes instantly instead
// of waiting for its poll interval. Until then it's inert — no such event ever
// arrives, so nothing fires.
//
// Backend contract to implement (mirror `NotificationStreamService.java` +
// the chat.* / presence.changed precedent):
//   attendance.punch    — a punch was recorded (check-in / check-out)
//   attendance.updated  — a record changed (amend / regularize / operator punch)
//   attendance.leave    — a leave was approved or cancelled (moves on-leave)
// Payload may be empty; we refetch rather than trust the wire, matching
// `defaultNotificationHandler`'s policy.

import type { QueryClient } from '@tanstack/react-query';

export type StreamHandler = (qc: QueryClient, raw: string) => void;

const invalidateAttendance: StreamHandler = (qc) => {
  // Broad invalidation under the shared `attendance` key covers the pulse,
  // distribution, department breakdown, live feed and the queues in one go.
  void qc.invalidateQueries({ queryKey: ['attendance'] });
};

export const attendanceStreamHandlers: Record<string, StreamHandler> = {
  'attendance.punch': invalidateAttendance,
  'attendance.updated': invalidateAttendance,
  'attendance.leave': invalidateAttendance,
};
