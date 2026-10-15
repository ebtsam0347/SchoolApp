package com.forward.hrms.bff.service.search;

import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.bff.config.BffCacheConfig;
import com.forward.hrms.bff.dto.response.SearchResponse;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.web.async.RequestPropagatingExecutor;

import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Fans search out across every registered {@link SearchProvider}, applies
 * caller-scope filtering, merges the results, re-ranks globally, and caps
 * to the request limit.
 *
 * <p>Per-provider deadline 250 ms, whole-request ceiling 600 ms — both
 * tunable via {@link Tunables}. Providers whose required subject the
 * caller lacks are silently dropped (no 403, no diagnostic row).
 */
@Slf4j
@Service
public class SearchAggregator {

    /** Per-provider hard cap regardless of caller request. */
    public static final int HARD_LIMIT = 10;
    public static final int DEFAULT_LIMIT = 5;
    public static final int MIN_QUERY_LENGTH = 2;

    /** Boost added to the provider's score for an exact-prefix title match (case-insensitive). */
    private static final double EXACT_PREFIX_BOOST = 0.15;

    /**
     * §7.bff #40 — refuse pathologically long search queries before they
     * propagate to every provider. Two chars is the floor (set by
     * MIN_QUERY_LENGTH); 200 chars is comfortably above any UI typeahead
     * pattern and still rejects the multi-KB strings a script could
     * fire at the palette endpoint.
     */
    public static final int MAX_QUERY_LENGTH = 200;

    private final List<SearchProvider> providers;
    private final EmployeeClient employees;
    private final RequestPropagatingExecutor propagating;
    private final Cache callerEmployeeIdCache;
    private final Tunables tunables = Tunables.defaults();

    public SearchAggregator(List<SearchProvider> providers,
                            EmployeeClient employees,
                            RequestPropagatingExecutor propagating,
                            CacheManager cacheManager) {
        this.providers = providers;
        this.employees = employees;
        this.propagating = propagating;
        this.callerEmployeeIdCache = cacheManager.getCache(BffCacheConfig.CACHE_CALLER_EMPLOYEE_ID);
    }

    public SearchResponse search(String q, Set<String> typeFilter, Integer limitOverride) {
        if (q == null || q.strip().length() < MIN_QUERY_LENGTH) {
            return new SearchResponse(List.of(), false, List.of());
        }
        String query = q.strip();
        // §7.bff #40 — cap the query string length so a 10 KB payload
        // doesn't flood every provider. Truncate (rather than reject)
        // so a typo at the FE side still returns sensible results.
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }
        int limit = clampLimit(limitOverride);

        CurrentUser caller = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError(CommonErrorCode.UNAUTHENTICATED,
                        "no current user"));
        Long callerEmployeeId = resolveCallerEmployeeId(caller);

        List<SearchProvider> selected = providers.stream()
                .filter(p -> typeFilter.isEmpty() || typeFilter.contains(p.type()))
                .filter(p -> canRead(caller, p))
                .toList();

        if (selected.isEmpty()) {
            return new SearchResponse(List.of(), false, List.of());
        }

        SearchCtx ctx = new SearchCtx(query, limit, caller.userId(), callerEmployeeId,
                tunables.perProviderTimeout());

        long startedAt = System.nanoTime();
        long deadlineNanos = startedAt + tunables.totalBudget().toNanos();

        List<ProviderRun> runs = new ArrayList<>(selected.size());
        for (SearchProvider p : selected) {
            long t0 = System.nanoTime();
            CompletableFuture<List<SearchHit>> f = supplyWithRequest(() -> p.search(ctx));
            runs.add(new ProviderRun(p, f, t0));
        }

        List<SearchHit> merged = new ArrayList<>();
        List<SearchResponse.ProviderDiagnostic> diags = new ArrayList<>(runs.size());
        boolean partial = false;

        for (ProviderRun r : runs) {
            long remainingTotal = Math.max(0, deadlineNanos - System.nanoTime());
            long perProvider = Math.min(tunables.perProviderTimeout().toNanos(), remainingTotal);
            String status;
            int hits = 0;
            try {
                List<SearchHit> raw = perProvider <= 0
                        ? List.of()
                        : r.future.get(perProvider, TimeUnit.NANOSECONDS);
                List<SearchHit> capped = cap(raw, ctx.limit());
                List<SearchHit> boosted = applyExactPrefixBoost(capped, query);
                merged.addAll(boosted);
                hits = capped.size();
                status = "ok";
            } catch (TimeoutException te) {
                r.future.cancel(true);
                partial = true;
                status = "timeout";
                log.warn("search provider {} timed out for q='{}'", r.provider.type(), query);
            } catch (Exception ex) {
                partial = true;
                status = "error";
                log.warn("search provider {} failed for q='{}': {}",
                        r.provider.type(), query, rootMessage(ex));
            }
            long ms = (System.nanoTime() - r.startedAtNanos) / 1_000_000L;
            diags.add(new SearchResponse.ProviderDiagnostic(r.provider.type(), hits, ms, status));
        }

        List<SearchHit> ranked = rankAndDedupe(merged);

        return new SearchResponse(ranked, partial, diags);
    }

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_LIMIT;
        return Math.min(requested, HARD_LIMIT);
    }

    private static boolean canRead(CurrentUser caller, SearchProvider p) {
        String subject = p.requiredSubject();
        if (subject == null || subject.isBlank()) return true;
        // §7.bff #36 — convention: subject "employee" → "employee.read".
        // Log at debug when we silently drop a provider so a "where did my
        // result go?" investigation has somewhere to look.
        boolean allowed = caller.hasPermission(subject + ".read");
        if (!allowed) {
            log.debug("search provider {} dropped — caller userId={} lacks {}.read",
                    p.type(), caller.userId(), subject);
        }
        return allowed;
    }

    /**
     * §7.bff #27 — Caffeine-backed cache of {@code (userId → employeeId)}
     * with a 60-second TTL. The palette endpoint runs on every keystroke
     * (10/sec is a normal typing burst); without this cache each one
     * burns an HTTP round-trip to {@code /employees/me}. Misses still
     * resolve via the client, and the cache stores even the "no ESS
     * link" outcome (as null in an Optional wrapper) so a per-keystroke
     * stream of 404s doesn't replay either.
     */
    private Long resolveCallerEmployeeId(CurrentUser caller) {
        long userId = caller.userId();
        if (callerEmployeeIdCache != null) {
            Cache.ValueWrapper hit = callerEmployeeIdCache.get(userId);
            if (hit != null) {
                return (Long) hit.get();
            }
        }
        Long resolved = loadCallerEmployeeId(caller);
        if (callerEmployeeIdCache != null) {
            callerEmployeeIdCache.put(userId, resolved);
        }
        return resolved;
    }

    private Long loadCallerEmployeeId(CurrentUser caller) {
        try {
            EmployeeDto me = employees.me();
            return me == null ? null : me.id();
        } catch (RuntimeException ex) {
            // /employees/me 404s for callers without an ESS link. That's
            // fine — self-only providers will skip themselves.
            log.debug("no employee record for userId={} ({})", caller.userId(), rootMessage(ex));
            return null;
        }
    }

    private static List<SearchHit> cap(List<SearchHit> hits, int limit) {
        if (hits == null || hits.isEmpty()) return List.of();
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    private static List<SearchHit> applyExactPrefixBoost(List<SearchHit> hits, String query) {
        if (hits.isEmpty()) return hits;
        String needle = query.toLowerCase(Locale.ROOT);
        List<SearchHit> out = new ArrayList<>(hits.size());
        for (SearchHit h : hits) {
            String title = h.title() == null ? "" : h.title().toLowerCase(Locale.ROOT);
            if (title.startsWith(needle)) {
                out.add(new SearchHit(h.id(), h.type(), h.title(), h.subtitle(),
                        h.url(), Math.min(1.0, h.score() + EXACT_PREFIX_BOOST), h.badge()));
            } else {
                out.add(h);
            }
        }
        return out;
    }

    private static List<SearchHit> rankAndDedupe(List<SearchHit> merged) {
        if (merged.isEmpty()) return List.of();
        merged.sort(Comparator.comparingDouble(SearchHit::score).reversed()
                .thenComparing(SearchHit::title, Comparator.nullsLast(Comparator.naturalOrder())));
        // De-dupe by id — provider-prefixed ids should already be unique, but
        // cheap insurance against accidental collisions across providers.
        Set<String> seen = new LinkedHashSet<>();
        List<SearchHit> deduped = new ArrayList<>(merged.size());
        for (SearchHit h : merged) {
            if (seen.add(h.id())) deduped.add(h);
        }
        return deduped;
    }

    private <T> CompletableFuture<T> supplyWithRequest(Supplier<T> supplier) {
        return propagating.async(supplier);
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage();
    }

    private record ProviderRun(SearchProvider provider,
                               CompletableFuture<List<SearchHit>> future,
                               long startedAtNanos) {}

    /*
     * §B3 — bumped from 250ms / 600ms after every provider call was timing
     * out on the live system. The only registered provider hits the
     * employee-service /employees trgm-similarity endpoint which is
     * comfortably sub-second but not 250ms-fast under load (the directory
     * composite that uses the same call has been observed at multi-second
     * latency). With one cold-cache CatalogCache.snapshot() in the same path
     * the original 250ms cap guaranteed `partial: true, results: []`.
     * 800ms / 1500ms keeps the palette feeling responsive (the FE debounce
     * already absorbs a 1s answer) without truncating real hits.
     */
    record Tunables(Duration perProviderTimeout, Duration totalBudget) {
        static Tunables defaults() {
            return new Tunables(Duration.ofMillis(800), Duration.ofMillis(1500));
        }
    }
}
