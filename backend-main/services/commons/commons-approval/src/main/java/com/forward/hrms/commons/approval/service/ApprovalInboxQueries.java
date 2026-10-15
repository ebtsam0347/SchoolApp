package com.forward.hrms.commons.approval.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.web.page.PageResponse;

import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

/**
 * Shared shape for "pending approvals" queries across request-owning
 * services: pull inbox row ids, bulk-load, preserve inbox order, apply
 * the {@code from}/{@code to} window, sort by id desc as tiebreaker,
 * paginate via {@link InboxPagination}.
 */
public final class ApprovalInboxQueries {

    private ApprovalInboxQueries() {}

    public static <R, T> PageResponse<T> findPending(
            ApprovalInboxService inbox,
            String type,
            CurrentUser user,
            Function<List<Long>, List<R>> bulkLoader,
            ToLongFunction<R> idGetter,
            Function<R, LocalDate> windowFromGetter,
            Function<R, LocalDate> windowToGetter,
            LocalDate from,
            LocalDate to,
            Pageable pageable,
            Function<R, T> mapper) {
        return findPending(inbox.requestIdsInInbox(type, user), bulkLoader, idGetter,
                windowFromGetter, windowToGetter, from, to, pageable, mapper);
    }

    /**
     * Same as above, plus a {@code rowFilter} applied to the bulk-loaded rows
     * before the window/sort/pagination step — so page totals reflect the
     * filter. It receives the whole list, not one row at a time, so callers
     * can resolve display fields for every row in a single batch lookup.
     */
    public static <R, T> PageResponse<T> findPending(
            ApprovalInboxService inbox,
            String type,
            CurrentUser user,
            Function<List<Long>, List<R>> bulkLoader,
            UnaryOperator<List<R>> rowFilter,
            ToLongFunction<R> idGetter,
            Function<R, LocalDate> windowFromGetter,
            Function<R, LocalDate> windowToGetter,
            LocalDate from,
            LocalDate to,
            Pageable pageable,
            Function<R, T> mapper) {
        return findPending(inbox.requestIdsInInbox(type, user),
                ids -> rowFilter.apply(bulkLoader.apply(ids)), idGetter,
                windowFromGetter, windowToGetter, from, to, pageable, mapper);
    }

    /**
     * Same as {@link #findPending(ApprovalInboxService, String, CurrentUser, Function, ToLongFunction,
     * Function, Function, LocalDate, LocalDate, Pageable, Function)} but for callers that own more than
     * one request type per feature (e.g. requisitions split into NEW_HIRE/REPLACEMENT chains) and have
     * already merged the inbox ids across those types themselves.
     */
    public static <R, T> PageResponse<T> findPending(
            List<Long> ids,
            Function<List<Long>, List<R>> bulkLoader,
            ToLongFunction<R> idGetter,
            Function<R, LocalDate> windowFromGetter,
            Function<R, LocalDate> windowToGetter,
            LocalDate from,
            LocalDate to,
            Pageable pageable,
            Function<R, T> mapper) {
        Map<Long, R> byId = new LinkedHashMap<>();
        for (R r : bulkLoader.apply(ids)) byId.put(idGetter.applyAsLong(r), r);
        List<R> all = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(r -> from == null || !windowToGetter.apply(r).isBefore(from))
                .filter(r -> to == null || !windowFromGetter.apply(r).isAfter(to))
                .sorted((a, b) -> Long.compare(idGetter.applyAsLong(b), idGetter.applyAsLong(a)))
                .toList();
        return InboxPagination.apply(all, pageable, mapper);
    }
}
