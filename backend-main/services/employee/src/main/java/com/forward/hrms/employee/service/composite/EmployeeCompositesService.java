package com.forward.hrms.employee.service.composite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.employee.dto.response.AggregateDto;
import com.forward.hrms.employee.dto.response.CityDto;
import com.forward.hrms.employee.dto.response.DelegationCandidate;
import com.forward.hrms.employee.dto.response.DelegationCandidatesResponse;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.dto.response.DesignationDto;
import com.forward.hrms.employee.dto.response.EmployeeDirectoryRow;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmployeeHistoryDto;
import com.forward.hrms.employee.dto.response.EmployeeHistoryRow;
import com.forward.hrms.employee.dto.response.EmployeeResolvedLabels;
import com.forward.hrms.employee.dto.response.EmployeeStats;
import com.forward.hrms.employee.dto.response.GradeDto;
import com.forward.hrms.employee.dto.response.ManagerSummary;
import com.forward.hrms.employee.dto.response.OnboardingLookups;
import com.forward.hrms.employee.dto.response.OrgTree;
import com.forward.hrms.employee.dto.response.SectionDto;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.EmploymentType;
import com.forward.hrms.employee.service.AggregateService;
import com.forward.hrms.employee.service.CityService;
import com.forward.hrms.employee.service.DepartmentService;
import com.forward.hrms.employee.service.DesignationService;
import com.forward.hrms.employee.service.EmployeeCertificationService;
import com.forward.hrms.employee.service.EmployeeService;
import com.forward.hrms.employee.service.GradeService;
import com.forward.hrms.employee.service.ProvinceService;
import com.forward.hrms.employee.service.QualificationService;
import com.forward.hrms.employee.service.SectionService;
import com.forward.hrms.employee.service.SkillService;

import lombok.RequiredArgsConstructor;

/**
 * Read-only composite service for FE-facing surfaces previously
 * assembled in the BFF's {@code EmployeeBffService}. Each method joins
 * employee rows with the catalog labels the page needs, in-process.
 *
 * <p>No commons-cache dependency: catalog rows live in this service
 * and the snapshot is built directly from the catalog services
 * (cheap, in-memory, JPA-backed). Cross-service composites that need
 * the shared cache (employee+audit, employee+payroll dashboards)
 * stay in the BFF.
 */
@Service
@RequiredArgsConstructor
public class EmployeeCompositesService {

    private static final int HISTORY_PAGE_DEFAULT_SIZE = 20;
    private static final int HISTORY_PAGE_MAX_SIZE = 200;
    private static final int DIRECTORY_PAGE_DEFAULT_SIZE = 25;
    private static final int DIRECTORY_PAGE_MAX_SIZE = 200;
    private static final int CERTS_EXPIRING_WINDOW_DAYS = 30;
    private static final int PROBATION_DUE_WINDOW_DAYS = 14;
    private static final int ORG_TREE_DEFAULT_DEPTH = 3;
    private static final int ORG_TREE_MAX_DEPTH = 6;
    public static final int DELEGATION_CANDIDATES_DEFAULT = 20;
    public static final int DELEGATION_CANDIDATES_MAX = 100;

    private final EmployeeService employees;
    private final AggregateService aggregates;
    private final EmployeeCertificationService certifications;
    private final DepartmentService departments;
    private final SectionService sections;
    private final DesignationService designations;
    private final GradeService grades;
    private final CityService cities;
    private final ProvinceService provinces;
    private final QualificationService qualifications;
    private final SkillService skills;

    // ===== /employees/directory =====

    @Transactional(readOnly = true)
    public PageResponse<EmployeeDirectoryRow> directory(EmploymentStatus status,
                                                         EmploymentType employmentType,
                                                         Long departmentId, Long sectionId, Long managerId,
                                                         Long gradeId, Long designationId,
                                                         Boolean hasEss, String q,
                                                         Integer probationDueWithin, Boolean probationOverdue,
                                                         boolean includeTerminated,
                                                         Pageable pageable) {
        PageResponse<EmployeeDto> page = employees.list(
                status, employmentType, departmentId, sectionId, managerId, gradeId, designationId,
                hasEss, q, probationDueWithin, probationOverdue, includeTerminated,
                PageableDefaults.apply(pageable, DIRECTORY_PAGE_DEFAULT_SIZE,
                        DIRECTORY_PAGE_MAX_SIZE, Sort.by("employeeCode").ascending()));
        if (page.content().isEmpty()) {
            return new PageResponse<>(List.of(), page.page(), page.size(),
                    page.totalElements(), page.totalPages(), page.sort());
        }
        CatalogIndex bag = catalogSnapshot();
        Map<Long, EmployeeDto> managerLookup = loadManagers(page.content());
        List<EmployeeDirectoryRow> rows = new ArrayList<>(page.content().size());
        for (EmployeeDto e : page.content()) {
            rows.add(new EmployeeDirectoryRow(e, resolveLabels(e, bag, managerLookup)));
        }
        return new PageResponse<>(rows, page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.sort());
    }

    // ===== /employees/stats =====

    @Transactional(readOnly = true)
    public EmployeeStats stats() {
        Map<String, Long> byStatus = bucketsToMap(aggregates.aggregate("status"));
        Map<String, Long> byType = bucketsToMap(aggregates.aggregate("employmentType"));
        long total = byStatus.entrySet().stream()
                .filter(en -> !"TERMINATED".equals(en.getKey()))
                .mapToLong(Map.Entry::getValue).sum();

        long dueWithin = countTotal(employees.list(null, null, null, null, null, null, null,
                null, null, PROBATION_DUE_WINDOW_DAYS, null, false, sizeOne()));
        long overdue = countTotal(employees.list(null, null, null, null, null, null, null,
                null, null, null, true, false, sizeOne()));
        long activeProb = countTotal(employees.list(null, null, null, null, null, null, null,
                null, null, 3650, null, false, sizeOne()));

        long granted = countTotal(employees.list(null, null, null, null, null, null, null,
                true, null, null, null, false, sizeOne()));
        long ungranted = countTotal(employees.list(null, null, null, null, null, null, null,
                false, null, null, null, false, sizeOne()));

        long certsExpiring = countTotal(certifications.expiringWithin(
                CERTS_EXPIRING_WINDOW_DAYS, null, null, null, sizeOne()));
        long certsExpired = countTotal(certifications.expiringWithin(
                0, null, null, true, sizeOne()));

        double coverage = (granted + ungranted) == 0
                ? 0.0
                : Math.round((10000.0 * granted) / (granted + ungranted)) / 100.0;

        return new EmployeeStats(
                total, byStatus, byType,
                new EmployeeStats.Probation(activeProb, dueWithin, overdue),
                new EmployeeStats.Certifications(certsExpiring, certsExpired),
                new EmployeeStats.Ess(granted, ungranted, coverage));
    }

    // ===== /employees/{id}/history/resolved =====

    @Transactional(readOnly = true)
    public PageResponse<EmployeeHistoryRow> historyResolved(long employeeId, Integer size) {
        int n = size == null || size <= 0
                ? HISTORY_PAGE_DEFAULT_SIZE
                : Math.min(size, HISTORY_PAGE_MAX_SIZE);
        PageResponse<EmployeeHistoryDto> page = employees.history(employeeId, PageRequest.of(0, n));
        if (page.content().isEmpty()) {
            return new PageResponse<>(List.of(), page.page(), page.size(),
                    page.totalElements(), page.totalPages(), page.sort());
        }
        CatalogIndex bag = catalogSnapshot();
        Map<Long, EmployeeDto> managerLookup = loadHistoryManagers(page.content());
        List<EmployeeHistoryRow> rows = new ArrayList<>(page.content().size());
        for (EmployeeHistoryDto h : page.content()) {
            rows.add(toHistoryRow(h, bag, managerLookup));
        }
        return new PageResponse<>(rows, page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.sort());
    }

    // ===== /employees/org-tree =====

    @Transactional(readOnly = true)
    public OrgTree orgTree(Long rootId, Integer depth) {
        int d = depth == null || depth <= 0 ? ORG_TREE_DEFAULT_DEPTH : Math.min(depth, ORG_TREE_MAX_DEPTH);
        EmployeeDto root = rootId == null ? topOfOrg() : employees.get(rootId);
        return new OrgTree(buildNode(root, d), d);
    }

    private EmployeeDto topOfOrg() {
        // "Top of org" = an employee with no manager. There may be more
        // than one (e.g. CEO / chairman); pick the lowest id deterministically.
        PageResponse<EmployeeDto> page = employees.list(null, null, null, null, null, null, null,
                null, null, null, null, false, PageRequest.of(0, 50));
        return page.content().stream()
                .filter(e -> e.managerId() == null)
                .min((a, b) -> Long.compare(a.id(), b.id()))
                .orElseThrow(() -> new IllegalStateException("no top-of-org employee found"));
    }

    private OrgTree.Node buildNode(EmployeeDto e, int depthRemaining) {
        List<OrgTree.Node> children = List.of();
        int reportsCount = e.directReportsCount() == null ? 0 : e.directReportsCount();
        if (depthRemaining > 0 && reportsCount > 0) {
            // Use page size 200 to mirror BFF behaviour — covers the wide trees
            // without forcing the FE to paginate at the tree level.
            PageResponse<EmployeeDto> reports = employees.team(e.id(), null, PageRequest.of(0, 200));
            children = reports.content().stream()
                    .map(r -> buildNode(r, depthRemaining - 1))
                    .toList();
        }
        return new OrgTree.Node(
                e.id(), e.fullName(), e.employeeCode(),
                null,
                e.profilePictureUrl(),
                reportsCount,
                children);
    }

    // ===== /employees/delegation-candidates =====

    @Transactional(readOnly = true)
    public DelegationCandidatesResponse delegationCandidates(long callerEmployeeId, String q, Integer limit) {
        int n = limit == null || limit <= 0
                ? DELEGATION_CANDIDATES_DEFAULT
                : Math.min(limit, DELEGATION_CANDIDATES_MAX);
        PageResponse<EmployeeDto> page = employees.list(
                EmploymentStatus.ACTIVE,
                null, null, null, null, null, null,
                true, q, null, null, false,
                PageRequest.of(0, n + 1, Sort.by("employeeCode").ascending()));
        List<DelegationCandidate> rows = new ArrayList<>(n);
        for (EmployeeDto e : page.content()) {
            if (rows.size() >= n) break;
            if (e.id() == callerEmployeeId) continue;
            if (e.userId() == null) continue;
            rows.add(new DelegationCandidate(
                    e.userId(), e.fullName(), e.employeeCode(), null));
        }
        return new DelegationCandidatesResponse(rows);
    }

    // ===== /employees/onboarding-lookups =====

    @Transactional(readOnly = true)
    public OnboardingLookups onboardingLookups() {
        return new OnboardingLookups(
                departments.list(null),
                sections.list(null, null),
                designations.list(null),
                grades.list(null),
                provinces.list(null),
                cities.list(null, null),
                qualifications.list(null, null),
                skills.list(null, null));
    }

    // ===== shared helpers =====

    private CatalogIndex catalogSnapshot() {
        return new CatalogIndex(
                indexBy(departments.list(null), DepartmentDto::id),
                indexBy(sections.list(null, null), SectionDto::id),
                indexBy(designations.list(null), DesignationDto::id),
                indexBy(grades.list(null), GradeDto::id),
                indexBy(cities.list(null, null), CityDto::id));
    }

    private record CatalogIndex(
            Map<Long, DepartmentDto> departments,
            Map<Long, SectionDto> sections,
            Map<Long, DesignationDto> designations,
            Map<Long, GradeDto> grades,
            Map<Long, CityDto> cities) {}

    private static <T> Map<Long, T> indexBy(List<T> rows, java.util.function.ToLongFunction<T> idOf) {
        if (rows == null || rows.isEmpty()) return Map.of();
        Map<Long, T> out = new HashMap<>(rows.size() * 2);
        for (T r : rows) out.put(idOf.applyAsLong(r), r);
        return out;
    }

    private Map<Long, EmployeeDto> loadManagers(List<EmployeeDto> rows) {
        Set<Long> managerIds = new HashSet<>();
        for (EmployeeDto r : rows) if (r.managerId() != null) managerIds.add(r.managerId());
        if (managerIds.isEmpty()) return Map.of();
        List<EmployeeDto> rowsList = employees.byIds(managerIds);
        Map<Long, EmployeeDto> out = new HashMap<>(rowsList.size() * 2);
        for (EmployeeDto e : rowsList) out.put(e.id(), e);
        return out;
    }

    private Map<Long, EmployeeDto> loadHistoryManagers(List<EmployeeHistoryDto> rows) {
        Set<Long> ids = new HashSet<>();
        for (EmployeeHistoryDto h : rows) if (h.managerId() != null) ids.add(h.managerId());
        if (ids.isEmpty()) return Map.of();
        List<EmployeeDto> rowsList = employees.byIds(ids);
        Map<Long, EmployeeDto> out = new HashMap<>(rowsList.size() * 2);
        for (EmployeeDto e : rowsList) out.put(e.id(), e);
        return out;
    }

    private static EmployeeResolvedLabels resolveLabels(EmployeeDto e, CatalogIndex bag,
                                                         Map<Long, EmployeeDto> managerById) {
        EmployeeDto mgr = e.managerId() == null ? null : managerById.get(e.managerId());
        return new EmployeeResolvedLabels(
                e.departmentId() == null ? null : bag.departments().get(e.departmentId()),
                e.sectionId() == null ? null : bag.sections().get(e.sectionId()),
                e.designationId() == null ? null : bag.designations().get(e.designationId()),
                e.gradeId() == null ? null : bag.grades().get(e.gradeId()),
                e.cityId() == null ? null : bag.cities().get(e.cityId()),
                ManagerSummary.from(mgr));
    }

    private static EmployeeHistoryRow toHistoryRow(EmployeeHistoryDto h, CatalogIndex bag,
                                                    Map<Long, EmployeeDto> managerById) {
        EmployeeDto mgr = h.managerId() == null ? null : managerById.get(h.managerId());
        return new EmployeeHistoryRow(
                h.id(), h.effectiveFrom(), h.effectiveTo(),
                h.changeReason() == null ? null : h.changeReason().name(),
                h.notes(),
                h.departmentId() == null ? null : bag.departments().get(h.departmentId()),
                h.sectionId() == null ? null : bag.sections().get(h.sectionId()),
                h.designationId() == null ? null : bag.designations().get(h.designationId()),
                h.gradeId() == null ? null : bag.grades().get(h.gradeId()),
                ManagerSummary.from(mgr),
                h);
    }

    private static Map<String, Long> bucketsToMap(AggregateDto agg) {
        Map<String, Long> out = new HashMap<>();
        if (agg == null || agg.buckets() == null) return out;
        for (AggregateDto.Bucket b : agg.buckets()) {
            out.put(b.key() == null ? "UNSPECIFIED" : b.key(), b.count());
        }
        return out;
    }

    private static long countTotal(PageResponse<?> p) {
        return p.totalElements();
    }

    private static Pageable sizeOne() {
        return PageRequest.of(0, 1);
    }

}
