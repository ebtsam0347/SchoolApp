package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.EmploymentType;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Specifications for the directory list endpoint. The q-string is split
 * on whitespace and AND-ed token-by-token: each token must match at
 * least one searchable field (substring or trigram). The previous JPQL
 * OR-of-fields shape returned 933 rows for {@code "Muhammad Waqas"}
 * because the trgm similarity branch matches every Muhammad above 0.3
 * independently of the second token.
 *
 * <p>When {@code q} is non-empty the specification also installs a
 * tiered relevance {@code ORDER BY} so exact name / code matches surface
 * first regardless of the caller's default sort. The controller passes
 * {@link org.springframework.data.domain.Sort#unsorted()} as its default
 * in that case so the spec's order isn't overwritten by Spring Data's
 * {@code applySorting}.
 */
public final class EmployeeSpecifications {

    private static final double TRGM_THRESHOLD = 0.3;

    private EmployeeSpecifications() {}

    public static Specification<Employee> filters(EmploymentStatus status,
                                                  EmploymentType employmentType,
                                                  Long departmentId,
                                                  Long sectionId,
                                                  Long managerId,
                                                  Long gradeId,
                                                  Long designationId,
                                                  Boolean hasEss,
                                                  String q,
                                                  boolean includeTerminated) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("employmentStatus"), status));
            } else if (!includeTerminated) {
                predicates.add(cb.notEqual(root.get("employmentStatus"), EmploymentStatus.TERMINATED));
            }
            if (employmentType != null) predicates.add(cb.equal(root.get("employmentType"), employmentType));
            if (departmentId != null)   predicates.add(cb.equal(root.get("departmentId"), departmentId));
            if (sectionId != null)      predicates.add(cb.equal(root.get("sectionId"), sectionId));
            if (managerId != null)      predicates.add(cb.equal(root.get("managerId"), managerId));
            if (gradeId != null)        predicates.add(cb.equal(root.get("gradeId"), gradeId));
            if (designationId != null)  predicates.add(cb.equal(root.get("designationId"), designationId));
            if (hasEss != null) {
                predicates.add(hasEss
                        ? cb.isNotNull(root.get("userId"))
                        : cb.isNull(root.get("userId")));
            }

            if (q != null && !q.isBlank()) {
                String[] tokens = q.trim().split("\\s+");

                Expression<String> fullNameLower = cb.lower(root.get("fullName"));
                Expression<String> employeeCodeLower = cb.lower(root.get("employeeCode"));
                Expression<String> workEmailLower = cb.lower(cb.coalesce(root.get("workEmail"), ""));
                Expression<String> phoneOrEmpty = cb.coalesce(root.get("phone"), "");
                Expression<String> cnicOrEmpty = cb.coalesce(root.get("cnic"), "");

                for (String token : tokens) {
                    if (token.isBlank()) continue;
                    String lower = token.toLowerCase();
                    String like = "%" + lower + "%";

                    Predicate substring = cb.or(
                            cb.like(fullNameLower, like),
                            cb.like(employeeCodeLower, like),
                            cb.like(workEmailLower, like),
                            cb.like(phoneOrEmpty, "%" + token + "%"),
                            cb.like(cnicOrEmpty, "%" + token + "%")
                    );

                    Expression<Double> simName = cb.function("similarity", Double.class, fullNameLower, cb.literal(lower));
                    Expression<Double> simCode = cb.function("similarity", Double.class, employeeCodeLower, cb.literal(lower));
                    Predicate trigram = cb.or(
                            cb.greaterThan(simName, TRGM_THRESHOLD),
                            cb.greaterThan(simCode, TRGM_THRESHOLD)
                    );

                    predicates.add(cb.or(substring, trigram));
                }

                // Relevance ordering: prefer exact whole-q / name-phrase / code
                // matches over substring matches so a query like "158" surfaces
                // employee_code=158 above 10158, and "muhammad waqas 158"
                // surfaces the row whose name="Muhammad Waqas" and code="158"
                // ahead of every "Muhammad ..." also matched by the AND-tokens
                // filter. The spec's orderBy survives only when the caller's
                // Pageable is unsorted — see EmployeeController.list which
                // switches the default to unsorted when q is set.
                String wholeQ = q.trim().toLowerCase();
                StringBuilder namePhraseBuf = new StringBuilder();
                String firstCode = null;
                for (String t : tokens) {
                    if (t.isBlank()) continue;
                    if (t.matches("\\d+")) {
                        if (firstCode == null) firstCode = t;
                    } else {
                        if (namePhraseBuf.length() > 0) namePhraseBuf.append(' ');
                        namePhraseBuf.append(t.toLowerCase());
                    }
                }
                String namePhrase = namePhraseBuf.length() == 0 ? null : namePhraseBuf.toString();

                Expression<Integer> score = cb.<Integer>selectCase()
                        // tier 1 — exact whole-q match on name or code
                        .when(cb.or(cb.equal(fullNameLower, wholeQ),
                                    cb.equal(employeeCodeLower, wholeQ)), 1000)
                        // tier 2 — split match: name-phrase exact AND code exact
                        .when(namePhrase != null && firstCode != null
                                ? cb.and(cb.equal(fullNameLower, namePhrase),
                                         cb.equal(employeeCodeLower, firstCode.toLowerCase()))
                                : cb.disjunction(), 900)
                        // tier 3 — exact code match alone
                        .when(firstCode != null
                                ? cb.equal(employeeCodeLower, firstCode.toLowerCase())
                                : cb.disjunction(), 800)
                        // tier 4 — exact name-phrase match alone
                        .when(namePhrase != null
                                ? cb.equal(fullNameLower, namePhrase)
                                : cb.disjunction(), 700)
                        // tier 5 — prefix on name or code (whole-q or namePhrase)
                        .when(cb.or(
                                cb.like(fullNameLower, wholeQ + "%"),
                                cb.like(employeeCodeLower, wholeQ + "%"),
                                namePhrase != null
                                        ? cb.like(fullNameLower, namePhrase + "%")
                                        : cb.disjunction(),
                                firstCode != null
                                        ? cb.like(employeeCodeLower, firstCode.toLowerCase() + "%")
                                        : cb.disjunction()), 500)
                        // tier 6 — contains on name (whole-q or name-phrase)
                        .when(cb.or(
                                cb.like(fullNameLower, "%" + wholeQ + "%"),
                                namePhrase != null
                                        ? cb.like(fullNameLower, "%" + namePhrase + "%")
                                        : cb.disjunction()), 300)
                        // tier 7 — contains on code
                        .when(firstCode != null
                                ? cb.like(employeeCodeLower, "%" + firstCode.toLowerCase() + "%")
                                : cb.disjunction(), 200)
                        .otherwise(100);

                // Secondary tiebreak — similarity on full_name gives a smooth
                // ordering inside the same tier. Two rows in tier 5 (prefix)
                // get a nudge from their trigram similarity to the query.
                Expression<Double> simFinal = cb.function("similarity", Double.class,
                        fullNameLower, cb.literal(wholeQ));

                query.orderBy(cb.desc(score), cb.desc(simFinal),
                        cb.asc(root.get("employeeCode")));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
