package com.forward.hrms.commons.web.async;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * §7.bff #1 — request-context propagation across a fan-out executor.
 *
 * <p>Most BFF composites and any service that fans HTTP calls out
 * through a virtual-thread executor needs the inbound request's
 * identity to ride along: {@link RequestAttributes} (so
 * {@code IdentityForwardingInterceptor} can read {@code X-User-*}
 * headers off the request scope), {@link CurrentUserHolder} (so
 * service-layer code observes the authenticated caller), and
 * {@link SecurityContextHolder} (so {@code @PreAuthorize} works on
 * downstream Spring beans). Spring tears the bound state down with
 * the request thread, so each fan-out task has to rebind it.
 *
 * <p>This used to live as a copy-pasted {@code supplyWithRequest}
 * helper on every BFF service. The pattern is correct and brittle —
 * any new composite that forgets it sends downstream calls with no
 * identity, which surfaces as a 401 cascade. Centralized here so
 * services just call {@link #async(Supplier)} (or
 * {@link #asyncOr(Object, Supplier)} for the soft-fail variant).
 */
public class RequestPropagatingExecutor {

    private final Executor delegate;

    public RequestPropagatingExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    /**
     * Run {@code work} on the underlying executor with the calling
     * thread's request attributes, current-user, and security-context
     * rebound on the worker thread for the duration of the supplier.
     * State is unconditionally cleared in a finally block so a worker
     * thread reuse can't leak identity to the next task.
     */
    public <T> CompletableFuture<T> async(Supplier<T> work) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        CurrentUser user = CurrentUserHolder.get().orElse(null);
        SecurityContext security = SecurityContextHolder.getContext();
        return CompletableFuture.supplyAsync(() -> {
            if (attrs != null) RequestContextHolder.setRequestAttributes(attrs);
            if (user != null) CurrentUserHolder.set(user);
            if (security != null) SecurityContextHolder.setContext(security);
            try {
                return work.get();
            } finally {
                CurrentUserHolder.clear();
                SecurityContextHolder.clearContext();
                RequestContextHolder.resetRequestAttributes();
            }
        }, delegate);
    }

    /**
     * Soft-fail variant — runs {@code work} but returns
     * {@code fallback} if the supplier throws. The exception is
     * swallowed silently; callers who want to log or count slice
     * failures should use {@link #async(Supplier)} and wire the
     * fallback / metric in their own catch block (e.g.
     * {@code DashboardSupport.supplyOrDefault}).
     */
    public <T> CompletableFuture<T> asyncOr(T fallback, Supplier<T> work) {
        return async(work).exceptionally(ex -> fallback);
    }

    public Executor delegate() {
        return delegate;
    }
}
