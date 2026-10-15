package com.forward.hrms.commons.web.page;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Normalize a controller's incoming {@link Pageable} into one with a
 * sane page size (default + hard cap) and a default sort when the
 * caller didn't provide one.
 *
 * <p>The "default sort only" pattern was inlined across 18+ sites in
 * the monorepo and crucially missed the size clamp on six of them —
 * {@code GET /audit/search?size=2147483647} would happily return an
 * Integer.MAX_VALUE-sized page on a 1Gi pod. This consolidates the
 * shape so callers can't forget the clamp.
 *
 * <p>Use the 4-arg overload when the call site has its own size /
 * sort policy. The 2-arg overload is the convention for approval /
 * inbox listings (default 20, max 200, {@code <field>,desc}).
 */
public final class PageableDefaults {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    private PageableDefaults() {}

    public static Pageable apply(Pageable in, int defaultSize, int maxSize, Sort defaultSort) {
        int size = in.getPageSize();
        if (size <= 0 || size > maxSize) size = defaultSize;
        Sort sort = in.getSort().isUnsorted() ? defaultSort : in.getSort();
        return PageRequest.of(Math.max(0, in.getPageNumber()), size, sort);
    }

    /** Default 20, max 200, {@code <defaultSortField>,desc}. */
    public static Pageable apply(Pageable in, String defaultSortField) {
        return apply(in, DEFAULT_SIZE, MAX_SIZE, Sort.by(defaultSortField).descending());
    }

    public static Pageable applyAscending(Pageable in, String defaultSortField) {
        return apply(in, DEFAULT_SIZE, MAX_SIZE, Sort.by(defaultSortField).ascending());
    }
}
