package com.forward.hrms.commons.web.page;

import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/**
 * Validates {@code Sort} against a per-controller whitelist of column
 * names. Stops sort-injection on JPQL/SQL when callers control the sort
 * field via {@code ?sort=...}.
 *
 * <pre>
 * private static final SortWhitelist SORTABLE =
 *     SortWhitelist.of("joiningDate", "lastName", "employeeCode");
 * ...
 * SORTABLE.validate(pageable.getSort());
 * </pre>
 */
public final class SortWhitelist {

    private final Set<String> allowed;

    private SortWhitelist(Set<String> allowed) {
        this.allowed = Set.copyOf(allowed);
    }

    public static SortWhitelist of(String... fields) {
        return new SortWhitelist(Set.of(fields));
    }

    public void validate(Sort sort) {
        if (sort == null || sort.isUnsorted()) return;
        var bad = sort.stream()
                .map(Sort.Order::getProperty)
                .filter(p -> !allowed.contains(p))
                .map(p -> new Violation("sort", "must.be.allowed",
                        "sort field '" + p + "' is not sortable"))
                .toList();
        if (!bad.isEmpty()) {
            throw new ValidationError("Invalid sort field(s).", bad);
        }
    }

    public Set<String> allowed() { return allowed; }
}
