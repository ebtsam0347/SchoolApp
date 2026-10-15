package com.forward.hrms.commons.web.page;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Stable JSON shape for paged lists. Always use this — never return Spring's
 * {@code Page<T>} / {@code PageImpl<T>} directly; their JSON shape is not
 * contract-stable across Spring versions. See docs/api-conventions.md §2.2.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort
) {

    public static <T> PageResponse<T> from(Page<T> p) {
        return new PageResponse<>(
                p.getContent(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages(),
                serializeSort(p.getSort())
        );
    }

    /**
     * Builds a {@code PageResponse} that keeps the page metadata of {@code p}
     * but uses {@code content} as the body. Use when the content list is a
     * batch-enriched projection of the page rows that {@link Page#map} can't
     * express (e.g. one DTO per row built from a separate batched lookup).
     */
    public static <S, T> PageResponse<T> from(Page<S> p, List<T> content) {
        return new PageResponse<>(
                content,
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages(),
                serializeSort(p.getSort())
        );
    }

    private static String serializeSort(Sort sort) {
        if (sort == null || sort.isUnsorted()) return null;
        return sort.stream()
                .map(o -> o.getProperty() + "," + o.getDirection().name().toLowerCase())
                .collect(Collectors.joining(";"));
    }
}
