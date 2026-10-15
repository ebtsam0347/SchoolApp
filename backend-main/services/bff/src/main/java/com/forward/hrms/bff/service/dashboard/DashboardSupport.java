package com.forward.hrms.bff.service.dashboard;

import com.forward.hrms.commons.core.error.UpstreamError;
import com.forward.hrms.commons.web.async.RequestPropagatingExecutor;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Shared parallel fan-out helper for the dashboard composites. Each
 * dashboard service mirrors the same supplyWithRequest+unwrap pattern
 * the rest of the BFF uses; centralizing it here avoids five copies.
 */
@Slf4j
@Component
public class DashboardSupport {

    private final RequestPropagatingExecutor propagating;
    private final MeterRegistry meterRegistry;

    public DashboardSupport(RequestPropagatingExecutor propagating,
                            MeterRegistry meterRegistry) {
        this.propagating = propagating;
        this.meterRegistry = meterRegistry;
    }

    public <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return propagating.async(supplier);
    }

    /**
     * Like {@link #supply(Supplier)} but soft-fails on any 4xx OR 5xx
     * from a downstream service. Returns {@code fallback} instead of
     * propagating so a flapping or scope-mismatched slice surfaces as
     * an empty section in the composite rather than killing the whole
     * response.
     *
     * <p>§7.bff #22 — earlier this method only soft-failed on 4xx; 5xx
     * still propagated, which meant a flapping audit-service collapsed
     * the entire operator dashboard. Operator dashboards trade
     * completeness for availability — better to render the rest of the
     * page than to render nothing.
     *
     * <p>§7.bff #23 — every soft-fail bumps a Counter tagged with the
     * downstream class so Prometheus alerts can target a specific slice
     * spiking ("audit timed out 5x in the last minute").
     */
    public <T> CompletableFuture<T> supplyOrDefault(Supplier<T> supplier, T fallback) {
        return supplyOrDefault(supplier, fallback, "unspecified");
    }

    /** Variant that records the slice name on the metric label. */
    public <T> CompletableFuture<T> supplyOrDefault(Supplier<T> supplier, T fallback, String slice) {
        return supply(supplier).exceptionally(ex -> {
            Throwable root = ex instanceof CompletionException ? ex.getCause() : ex;
            String classification = classify(root);
            if (classification != null) {
                if ("client".equals(classification)) {
                    log.debug("dashboard slice {} soft-failed ({}): {}",
                            slice, root.getClass().getSimpleName(), root.getMessage());
                } else {
                    log.warn("dashboard slice {} soft-failed ({}): {}",
                            slice, root.getClass().getSimpleName(), root.getMessage());
                }
                meterRegistry.counter("bff.dashboard.slice.soft_fail",
                        "slice", slice, "kind", classification).increment();
                return fallback;
            }
            if (root instanceof RuntimeException re) throw re;
            throw new CompletionException(root);
        });
    }

    /** {@code "client"} = 4xx, {@code "server"} = 5xx, {@code null} = neither (don't soft-fail). */
    private static String classify(Throwable t) {
        if (t == null) return null;
        if (t instanceof HttpClientErrorException) return "client";
        if (t instanceof HttpServerErrorException) return "server";
        if (t instanceof UpstreamError ue) {
            Throwable cause = ue.getCause();
            if (cause instanceof HttpClientErrorException) return "client";
            if (cause instanceof HttpServerErrorException) return "server";
            // UpstreamError without an HTTP cause is usually a connect/IO
            // failure; treat as a soft-failable server-side issue.
            return "server";
        }
        return null;
    }

    public static RuntimeException unwrap(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof RuntimeException re) return re;
        return new RuntimeException(cause);
    }
}
