package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.web.page.PageResponse;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Function;

/**
 * In-memory slice for small permission-scoped "pending approvals" lists
 * (permanent advances, short-term advance batches). Row ids now come from
 * the real commons-approval chain via {@code ApprovalInboxService}; this is
 * a local copy of the same trivial pagination shape commons-approval's
 * InboxPagination uses, kept here so the existing batched employeeClient
 * enrichment in the callers doesn't need restructuring.
 */
final class InboxPage {

    private InboxPage() {}

    static <S, T> PageResponse<T> of(List<S> rows, Pageable pageable, Function<S, T> mapper) {
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
