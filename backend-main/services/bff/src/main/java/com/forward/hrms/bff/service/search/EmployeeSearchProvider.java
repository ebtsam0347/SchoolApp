package com.forward.hrms.bff.service.search;

import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.employee.dto.response.EmployeePickerCandidate;
import com.forward.hrms.employee.dto.response.EmployeePickerResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Command-palette employee provider. Calls the auth-only
 * {@code /employees/picker} so the palette works for every authenticated
 * caller regardless of role — operator, manager, employee — without
 * needing {@code employee.read}. The picker already returns department
 * + designation labels denormalized, so no separate catalog snapshot
 * read is required.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeSearchProvider implements SearchProvider {

    private static final String TYPE = "employee";
    private static final String BADGE = "Person";

    private final EmployeeClient employees;

    @Override public String type() { return TYPE; }

    /**
     * No required subject — {@code /employees/picker} is auth-only at
     * the upstream. Returning {@code null} here also stops the
     * aggregator from silently dropping the provider for callers
     * without {@code employee.read}.
     */
    @Override public String requiredSubject() { return null; }

    @Override
    public List<SearchHit> search(SearchCtx ctx) {
        EmployeePickerResponse resp = employees.picker(ctx.q(), ctx.limit());
        List<EmployeePickerCandidate> rows = resp.items() == null ? List.of() : resp.items();
        if (rows.isEmpty()) return List.of();

        List<SearchHit> hits = new ArrayList<>(rows.size());
        String needle = ctx.q().toLowerCase(Locale.ROOT);
        for (EmployeePickerCandidate e : rows) {
            hits.add(toHit(e, needle));
        }
        return hits;
    }

    private static SearchHit toHit(EmployeePickerCandidate e, String needle) {
        return new SearchHit(
                TYPE + ":" + e.id(),
                TYPE,
                e.fullName(),
                subtitle(e),
                "/people/" + e.id(),
                scoreFor(e, needle),
                BADGE);
    }

    private static String subtitle(EmployeePickerCandidate e) {
        if (e.designationName() != null && e.departmentName() != null) {
            return e.designationName() + " · " + e.departmentName();
        }
        if (e.designationName() != null) return e.designationName();
        if (e.departmentName() != null) return e.departmentName();
        return e.employeeCode();
    }

    private record ParsedQuery(String whole, String namePhrase, String firstCode) {
        static ParsedQuery of(String raw) {
            String whole = raw.toLowerCase(Locale.ROOT).strip();
            String[] tokens = whole.isEmpty() ? new String[0] : whole.split("\\s+");
            StringBuilder name = new StringBuilder();
            String code = null;
            for (String t : tokens) {
                if (t.isEmpty()) continue;
                if (t.matches("\\d+")) {
                    if (code == null) code = t;
                } else {
                    if (name.length() > 0) name.append(' ');
                    name.append(t);
                }
            }
            return new ParsedQuery(whole,
                    name.length() == 0 ? null : name.toString(),
                    code);
        }
    }

    /**
     * Tiered relevance scoring mirroring the picker's SQL ORDER BY so
     * the aggregator's cross-provider re-rank preserves upstream order
     * instead of collapsing every row to 0.50 and re-sorting alpha.
     *
     * <p>The query is split into a name-phrase (alpha tokens joined) and
     * the first numeric token (code candidate) so {@code "muhammad
     * waqas 158"} ranks the row whose name="Muhammad Waqas" AND
     * code="158" at the top, ahead of every "Muhammad ..." row.
     */
    private static double scoreFor(EmployeePickerCandidate e, String rawNeedle) {
        ParsedQuery q = ParsedQuery.of(rawNeedle);
        String name = e.fullName() == null ? "" : e.fullName().toLowerCase(Locale.ROOT);
        String code = e.employeeCode() == null ? "" : e.employeeCode().toLowerCase(Locale.ROOT);

        if (!q.whole().isEmpty() && (name.equals(q.whole()) || code.equals(q.whole()))) return 0.99;
        if (q.namePhrase() != null && q.firstCode() != null
                && name.equals(q.namePhrase()) && code.equals(q.firstCode())) return 0.97;
        if (q.firstCode() != null && code.equals(q.firstCode())) return 0.95;
        if (q.namePhrase() != null && name.equals(q.namePhrase())) return 0.92;
        if (!q.whole().isEmpty() && name.startsWith(q.whole())) return 0.85;
        if (q.namePhrase() != null && name.startsWith(q.namePhrase())) return 0.82;
        if (q.firstCode() != null && code.startsWith(q.firstCode())) return 0.78;
        if (q.namePhrase() != null && name.contains(q.namePhrase())) return 0.65;
        if (q.firstCode() != null && code.contains(q.firstCode())) return 0.55;
        if (!q.whole().isEmpty() && name.contains(q.whole())) return 0.60;
        return 0.50;
    }
}
