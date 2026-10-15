package com.forward.hrms.commons.core.context;

import java.util.Optional;

/**
 * Thread-local current user, populated by the JWT filter in commons-web.
 * The audit listener and any non-controller code reads from here so it
 * never has to know whether Spring Security is on the classpath.
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

    private CurrentUserHolder() {}

    public static Optional<CurrentUser> get() { return Optional.ofNullable(CURRENT.get()); }
    public static void set(CurrentUser user) { CURRENT.set(user); }
    public static void clear() { CURRENT.remove(); }

    public static String usernameOrSystem() {
        CurrentUser u = CURRENT.get();
        return u == null ? CurrentUser.SYSTEM_USERNAME : u.username();
    }
}
