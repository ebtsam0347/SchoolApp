package com.forward.hrms.commons.web.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method (or class) annotation enforcing permissions from the JWT.
 * Default semantics: ANY of the listed permissions grants access. Set
 * {@link #matchAll()} to require all of them.
 *
 * <pre>
 * &#64;RequirePermission("payroll.run.approve")
 * public void approve(...) { ... }
 * </pre>
 *
 * Throws {@code UnauthorizedError} if no current user, {@code ForbiddenError}
 * if the user lacks the required permission.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String[] value();
    boolean matchAll() default false;
}
