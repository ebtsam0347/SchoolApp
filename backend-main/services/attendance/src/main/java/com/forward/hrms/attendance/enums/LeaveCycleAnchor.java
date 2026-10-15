package com.forward.hrms.attendance.enums;

/**
 * How a leave type's entitlement cycle is anchored. Replaces the dead
 * {@code LeaveAccrualRule} — the anchor actually drives provisioning,
 * the renewal job, and proration.
 *
 * <ul>
 *   <li>{@code JOINING} — anniversary cycle (joining date → next joining
 *       date). Full grant from the joining date; renewed each anniversary.
 *       Annual leave. Pairs with {@code encashable=true} for end-of-cycle
 *       encashment.</li>
 *   <li>{@code FISCAL} — fiscal-year cycle (Jul 1 → Jun 30). Renewed each
 *       Jul 1; {@code prorateOnEntry} prorates a mid-year joiner's first
 *       cycle. M1 / Casual.</li>
 *   <li>{@code NONE} — no scheduled renewal. The balance row is lazily
 *       provisioned at the full default on a calendar-year window when
 *       first needed. Occasional / statutory types (maternity, unpaid, …)
 *       that aren't an annual entitlement.</li>
 * </ul>
 */
public enum LeaveCycleAnchor {
    JOINING, FISCAL, NONE
}
