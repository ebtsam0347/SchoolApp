package com.forward.hrms.audit.service;

import com.forward.hrms.audit.dto.response.FacetsResponse;
import com.forward.hrms.audit.dto.response.FacetsResponse.Facet;
import com.forward.hrms.audit.repository.AuditEventRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory 5-min TTL cache for /audit/facets. The grouped count
 * queries scan the audit table; with a few thousand rows that's fine
 * but the explorer's filter dropdowns repaint on every navigation —
 * caching the response per-process keeps the dropdowns cheap.
 *
 * <p>No Redis: per-process cache is enough at FSHRMS scale; staleness
 * up to 5 min is acceptable for a "values observed so far" list.
 */
@Service
@RequiredArgsConstructor
public class FacetsService {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final AuditEventRepository repo;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public FacetsResponse get() {
        Cached current = cache.get();
        Instant now = Instant.now();
        if (current != null && current.expiresAt.isAfter(now)) {
            return current.value;
        }
        FacetsResponse fresh = new FacetsResponse(
                toFacets(repo.facetEntityTypes()),
                toFacets(repo.facetActions()),
                toFacets(repo.facetSourceServices())
        );
        cache.set(new Cached(fresh, now.plus(TTL)));
        return fresh;
    }

    private static List<Facet> toFacets(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new Facet((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    private record Cached(FacetsResponse value, Instant expiresAt) {}
}
