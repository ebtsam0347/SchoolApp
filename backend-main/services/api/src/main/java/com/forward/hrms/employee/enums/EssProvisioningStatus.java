package com.forward.hrms.employee.enums;

/**
 * State of ESS (auth-user) provisioning for an employee.
 *
 * <ul>
 *   <li>{@code NOT_REQUESTED} — onboarded without {@code provisionEss=true},
 *       or older record from before the saga substrate landed.</li>
 *   <li>{@code RUNNING} — onboarding saga is in flight, auth call hasn't
 *       completed yet.</li>
 *   <li>{@code COMPLETED} — auth user exists ({@code employees.user_id} is
 *       set). Once true, stays true regardless of any saga row.</li>
 *   <li>{@code FAILED} — saga gave up after the retry budget. HR can
 *       re-issue via {@code POST /sagas/{id}/retry} or grant ESS manually
 *       via {@code POST /employees/{id}/grant-ess}.</li>
 * </ul>
 */
public enum EssProvisioningStatus {
    NOT_REQUESTED,
    RUNNING,
    COMPLETED,
    FAILED
}
