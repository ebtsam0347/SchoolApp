package com.forward.hrms.commons.web.security;

/**
 * Header names the gateway injects into requests it forwards downstream.
 *
 * <p>Downstream services trust these headers because:
 * <ol>
 *   <li>The gateway strips any inbound {@code X-User-*} headers from
 *       external requests before the security chain runs (see the
 *       gateway's identity-strip filter), so a client cannot forge them.
 *   <li>The gateway re-adds them after validating the JWT.
 *   <li>Network policy in UAT/prod forbids direct pod-to-pod traffic to
 *       service ports — clients must come through the gateway.
 * </ol>
 *
 * <p>The {@link HeaderIdentityFilter} reads these on the way into a
 * downstream service and populates {@code CurrentUser}.
 */
public final class IdentityHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-User-Name";
    /** Display name from the JWT {@code name} claim — populates {@code actorLabel} on emitted events. */
    public static final String FULL_NAME = "X-User-FullName";
    public static final String SESSION_ID = "X-User-Session";
    public static final String ROLES = "X-User-Roles";
    public static final String PERMISSIONS = "X-User-Permissions";

    public static final String[] ALL = { USER_ID, USERNAME, FULL_NAME, SESSION_ID, ROLES, PERMISSIONS };

    /**
     * Request attribute on which both {@link JwtAuthenticationFilter} (at
     * the gateway) and {@link HeaderIdentityFilter} (downstream) park the
     * authenticated user id. Outer filters that run after the security
     * chain finishes — by which point {@code CurrentUserHolder} is cleared
     * — read the id from here for access logging.
     */
    public static final String REQUEST_ATTR_USER_ID = "hrms.user.id";

    private IdentityHeaders() {}
}
