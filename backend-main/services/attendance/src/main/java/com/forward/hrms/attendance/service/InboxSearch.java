package com.forward.hrms.attendance.service;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Free-text search over an approver's pending-inbox rows, shared by the
 * leave / short-leave / overtime {@code pendingApprovals} filters. A row
 * matches when {@code q} equals its id (numeric {@code q} only), or is a
 * case-insensitive substring of its request code, employee code, or
 * employee full name. Employee fields come from one batch
 * {@link EmployeeDisplayCache#getMany} call per search.
 */
@Component
@RequiredArgsConstructor
public class InboxSearch {

    private final EmployeeDisplayCache displayCache;

    /** Returns {@code rows} unchanged when {@code q} is null or blank. */
    public <R> List<R> filter(List<R> rows, String q,
                              ToLongFunction<R> idGetter,
                              Function<R, String> requestCodeGetter,
                              ToLongFunction<R> employeeIdGetter) {
        if (q == null || q.isBlank() || rows.isEmpty()) return rows;
        String needle = q.trim().toLowerCase(Locale.ROOT);
        Long idNeedle = needle.chars().allMatch(Character::isDigit) && needle.length() <= 18
                ? Long.parseLong(needle) : null;
        Map<Long, EmployeeDisplay> people = displayCache.getMany(
                rows.stream().map(employeeIdGetter::applyAsLong).distinct().toList());
        return rows.stream().filter(r -> {
            if (idNeedle != null && idNeedle == idGetter.applyAsLong(r)) return true;
            if (containsIgnoreCase(requestCodeGetter.apply(r), needle)) return true;
            EmployeeDisplay p = people.get(employeeIdGetter.applyAsLong(r));
            return p != null && (containsIgnoreCase(p.employeeCode(), needle)
                    || containsIgnoreCase(p.fullName(), needle));
        }).toList();
    }

    private static boolean containsIgnoreCase(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }
}
