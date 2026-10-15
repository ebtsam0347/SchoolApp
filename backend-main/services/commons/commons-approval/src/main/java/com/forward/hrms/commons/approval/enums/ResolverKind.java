package com.forward.hrms.commons.approval.enums;

/**
 * How an approval stage finds the user(s) allowed to act on it.
 *
 * <ul>
 *   <li>{@code MANAGER} — snapshot the submitter's manager at submit time;
 *       a delegate may stand in via {@code ApprovalDelegation}.</li>
 *   <li>{@code PERMISSION} — role-only pool: {@code assigned_to} stays null
 *       and anyone holding the stage's {@code approve_permission} can act.</li>
 *   <li>{@code DEPARTMENT_HEAD} — snapshot the head of a fixed target
 *       department (not the submitter's own) at submit time, keyed by
 *       {@code resolver_target} (a department code).</li>
 *   <li>{@code GRADE} — role-only pool: {@code assigned_to} stays null and
 *       anyone whose employee record's grade matches {@code resolver_target}
 *       (a grade code) can act.</li>
 * </ul>
 */
public enum ResolverKind {
    MANAGER, PERMISSION, DEPARTMENT_HEAD, GRADE
}
