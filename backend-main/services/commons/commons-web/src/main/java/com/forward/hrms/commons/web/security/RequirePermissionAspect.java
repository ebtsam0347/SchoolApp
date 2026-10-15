package com.forward.hrms.commons.web.security;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
public class RequirePermissionAspect {

    @Around("@annotation(com.forward.hrms.commons.web.security.RequirePermission) "
            + "|| @within(com.forward.hrms.commons.web.security.RequirePermission)")
    public Object enforce(ProceedingJoinPoint pjp) throws Throwable {
        Method m = ((MethodSignature) pjp.getSignature()).getMethod();
        RequirePermission a = m.getAnnotation(RequirePermission.class);
        if (a == null) a = m.getDeclaringClass().getAnnotation(RequirePermission.class);

        CurrentUser user = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required."));

        boolean ok = a.matchAll()
                ? Arrays.stream(a.value()).allMatch(user::hasPermission)
                : Arrays.stream(a.value()).anyMatch(user::hasPermission);
        if (!ok) {
            throw new ForbiddenError("Missing required permission: " + String.join(",", a.value()));
        }
        return pjp.proceed();
    }
}
