package com.forward.hrms.commons.approval.service;

import com.forward.hrms.commons.web.page.PageResponse;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Function;

/**
 * In-memory slice for inbox-bounded lists (pending approvals etc.).
 * Inbox sets are small — assemble in memory, then page. Don't reach
 * for this on unbounded result sets.
 */
public final class InboxPagination {

    private InboxPagination() {}

    public static <S, T> PageResponse<T> apply(List<S> rows, Pageable pageable, Function<S, T> mapper) {
        int total = rows.size();
        int size = pageable.getPageSize() <= 0 ? 20 : Math.min(pageable.getPageSize(), 200);
        int page = Math.max(0, pageable.getPageNumber());
        int fromIdx = Math.min(page * size, total);
        int toIdx = Math.min(fromIdx + size, total);
        List<T> content = rows.subList(fromIdx, toIdx).stream().map(mapper).toList();
        int totalPages = size == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new PageResponse<>(content, page, size, total, totalPages, null);
    }
}
