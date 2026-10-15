package com.forward.hrms.commons.web.page;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Single source of truth for the size cap that paginated controllers
 * across the monorepo rely on. Without it, {@code GET /audit/search?size=2147483647}
 * happily returns an Integer.MAX_VALUE-sized page on a 1Gi pod.
 */
class PageableDefaultsTest {

    @Test
    void oversizedRequest_clampsToDefault() {
        Pageable in = PageRequest.of(0, 100_000);
        Pageable out = PageableDefaults.apply(in, "createdAt");
        assertEquals(PageableDefaults.DEFAULT_SIZE, out.getPageSize());
    }

    @Test
    void zeroOrNegativeSize_clampsToDefault() {
        Pageable in = PageRequest.of(0, 1).withPage(0);
        // PageRequest enforces size > 0, so we can't pass 0 directly; use the
        // explicit overload that bypasses the validation.
        Pageable zeroSize = new ZeroSizePageable(0);
        Pageable out = PageableDefaults.apply(zeroSize, "createdAt");
        assertEquals(PageableDefaults.DEFAULT_SIZE, out.getPageSize());
    }

    @Test
    void sizeWithinCap_isHonored() {
        Pageable in = PageRequest.of(2, 50);
        Pageable out = PageableDefaults.apply(in, "createdAt");
        assertEquals(50, out.getPageSize());
        assertEquals(2, out.getPageNumber());
    }

    @Test
    void exactlyMaxSize_isHonored() {
        Pageable in = PageRequest.of(0, PageableDefaults.MAX_SIZE);
        Pageable out = PageableDefaults.apply(in, "createdAt");
        assertEquals(PageableDefaults.MAX_SIZE, out.getPageSize());
    }

    @Test
    void unsortedRequest_appliesDefaultSortDescending() {
        Pageable in = PageRequest.of(0, 20);
        Pageable out = PageableDefaults.apply(in, "createdAt");
        Sort.Order order = out.getSort().getOrderFor("createdAt");
        assertTrue(order != null && order.isDescending());
    }

    @Test
    void callerSort_takesPrecedence() {
        Pageable in = PageRequest.of(0, 20, Sort.by("name").ascending());
        Pageable out = PageableDefaults.apply(in, "createdAt");
        Sort.Order order = out.getSort().getOrderFor("name");
        assertTrue(order != null && order.isAscending());
        // Default isn't appended when caller provided one.
        assertTrue(out.getSort().getOrderFor("createdAt") == null);
    }

    @Test
    void fourArgOverload_respectsCustomCap() {
        Pageable in = PageRequest.of(0, 10_000);
        Pageable out = PageableDefaults.apply(in, 50, 500, Sort.by("id").ascending());
        // 10_000 > 500 → falls back to default 50.
        assertEquals(50, out.getPageSize());
    }

    /** PageRequest.of refuses size <= 0; this stub bypasses that for the clamp test. */
    private static final class ZeroSizePageable implements Pageable {
        private final int size;
        ZeroSizePageable(int size) { this.size = size; }
        @Override public int getPageNumber() { return 0; }
        @Override public int getPageSize() { return size; }
        @Override public long getOffset() { return 0; }
        @Override public Sort getSort() { return Sort.unsorted(); }
        @Override public Pageable next() { return this; }
        @Override public Pageable previousOrFirst() { return this; }
        @Override public Pageable first() { return this; }
        @Override public Pageable withPage(int pageNumber) { return this; }
        @Override public boolean hasPrevious() { return false; }
    }
}
