package com.forward.hrms.commons.approval.spi;

/**
 * Resolves the manager user id for a MANAGER-kind approval stage.
 * The consuming service provides one {@code @Bean} implementation.
 *
 * <p>Returns the manager's <em>user id</em> (not employee id) —
 * {@code approval_steps.assigned_to} is keyed on user_id by every
 * read path (inbox query, canAct, delegation join, notifications).
 */
@FunctionalInterface
public interface ManagerResolver {
    /**
     * @param employeeId the submitting employee's id
     * @return the manager's user id, or {@code null} if unresolvable
     */
    Long resolveManagerUserId(long employeeId);
}
