package com.forward.hrms.bff.service.search;

import java.util.List;

/**
 * Producer of palette results for one logical type.
 *
 * <p>Implementations are autodetected as Spring beans and registered by the
 * aggregator at startup. Adding a provider requires no controller change —
 * the aggregator picks it up from the {@code List<SearchProvider>} the
 * context injects.
 */
public interface SearchProvider {

    /**
     * Stable wire identifier. Echoed verbatim in {@code SearchHit.type} and
     * accepted in {@code GET /bff/search?types=…}. Use kebab-case
     * ({@code employee}, {@code payslip-mine}, {@code lookup-department}).
     */
    String type();

    /**
     * Permission subject the caller must hold to receive this provider's
     * results. Returning {@code null} means any authenticated caller —
     * used for self-only providers ({@code payslip-mine}, {@code leave-mine},
     * {@code inbox-item}) where the underlying endpoint is already
     * caller-scoped.
     */
    default String requiredSubject() { return null; }

    /** Default per-type cap — typically 5; the aggregator clamps to context limit. */
    default int defaultLimit() { return 5; }

    /**
     * Return up to {@code ctx.limit()} hits. Implementations should cap
     * latency themselves where possible — the aggregator additionally
     * wraps the call with a per-provider deadline and treats overruns as
     * {@code timeout} in the response diagnostics.
     */
    List<SearchHit> search(SearchCtx ctx);
}
