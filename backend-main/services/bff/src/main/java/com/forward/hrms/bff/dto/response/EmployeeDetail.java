package com.forward.hrms.bff.dto.response;

import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.employee.dto.response.DisciplinaryActionListResponse;
import com.forward.hrms.employee.dto.response.EmployeeDto;
import com.forward.hrms.employee.dto.response.EmployeeHistoryDto;
import com.forward.hrms.employee.dto.response.EmployeeHistoryRow;
import com.forward.hrms.employee.dto.response.EmployeeResolvedLabels;
import com.forward.hrms.employee.dto.response.EmploymentContractDto;
import com.forward.hrms.employee.dto.response.ExitDto;
import com.forward.hrms.employee.dto.response.ManagerSummary;
import com.forward.hrms.employee.dto.response.SuspensionDto;
import com.forward.hrms.audit.dto.response.TimelineEntry;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(name = "EmployeeDetail",
        description = "Composite payload for the /people/{id} detail page: employee + resolved labels " +
                "+ active contract + suspension + exit + warning counts + direct reports preview + recent timeline " +
                "+ recent assignment history.")
public record EmployeeDetail(
        EmployeeDto employee,
        @Schema(description = "Resolved id→label rows for the catalogs the page renders.")
        EmployeeResolvedLabels resolved,
        @Schema(description = "Active contract row (most-recent ACTIVE).", nullable = true)
        EmploymentContractDto activeContract,
        @Schema(description = "Currently active suspension, if any.", nullable = true)
        SuspensionDto activeSuspension,
        @Schema(description = "Most recent exit row (PENDING / COMPLETED / WITHDRAWN).", nullable = true)
        ExitDto exit,
        @Schema(description = "UPHELD warning counts for the current FY by level " +
                "(FIRST/SECOND/FINAL → count).")
        Map<String, Long> upheldWarningsThisFy,
        @Schema(description = "Open (ISSUED) warning rows total.")
        long openWarningsThisFy,
        @Schema(description = "First 8 direct reports + total count.")
        DirectReportsPreview directReportsPreview,
        @Schema(description = "Last 20 user-visible timeline events for this employee.")
        List<TimelineEntry> recentTimeline,
        @Schema(description = "Last 10 assignment-history rows (newest first), with catalog + manager " +
                "labels resolved. Use GET /employees/{id}/history/resolved for the paginated full list.")
        List<EmployeeHistoryRow> recentHistory,
        @Schema(description = "Days from joining_date through today (Asia/Karachi).")
        long tenureDays
) {

    /** Resolve catalog labels + manager summary against the shared cache snapshot. */
    public static EmployeeResolvedLabels resolve(EmployeeDto e, CatalogCache.CatalogIndex idx,
                                                  Map<Long, EmployeeDto> managerById) {
        return new EmployeeResolvedLabels(
                e.departmentId() == null ? null : idx.departments().get(e.departmentId()),
                e.sectionId() == null ? null : idx.sections().get(e.sectionId()),
                e.designationId() == null ? null : idx.designations().get(e.designationId()),
                e.gradeId() == null ? null : idx.grades().get(e.gradeId()),
                e.cityId() == null ? null : idx.cities().get(e.cityId()),
                managerById == null || e.managerId() == null
                        ? null
                        : ManagerSummary.from(managerById.get(e.managerId())));
    }

    /** Build a resolved history row from the shared cache snapshot. */
    public static EmployeeHistoryRow historyRow(EmployeeHistoryDto h, CatalogCache.CatalogIndex idx,
                                                 Map<Long, EmployeeDto> managerById) {
        EmployeeDto m = h.managerId() == null || managerById == null
                ? null : managerById.get(h.managerId());
        return new EmployeeHistoryRow(
                h.id(), h.effectiveFrom(), h.effectiveTo(),
                h.changeReason() == null ? null : h.changeReason().name(),
                h.notes(),
                h.departmentId() == null ? null : idx.departments().get(h.departmentId()),
                h.sectionId() == null ? null : idx.sections().get(h.sectionId()),
                h.designationId() == null ? null : idx.designations().get(h.designationId()),
                h.gradeId() == null ? null : idx.grades().get(h.gradeId()),
                ManagerSummary.from(m),
                h);
    }

    @Schema(name = "EmployeeDetail.DirectReportsPreview")
    public record DirectReportsPreview(
            List<ReportSummary> items,
            int total
    ) {}

    @Schema(name = "EmployeeDetail.ReportSummary")
    public record ReportSummary(
            long id,
            String fullName,
            String employeeCode,
            String profilePictureUrl,
            int directReportsCount
    ) {}

    @Schema(name = "EmployeeDetail.Disciplinary")
    public record Disciplinary(
            int fyCode,
            Map<String, Long> warningCounts,
            int openIssued
    ) {
        public static Disciplinary from(DisciplinaryActionListResponse r) {
            int open = r == null || r.actions() == null ? 0
                    : (int) r.actions().stream()
                            .filter(a -> a.status() == com.forward.hrms.employee.enums.DisciplinaryStatus.ISSUED)
                            .count();
            Map<String, Long> warningCounts = (r == null || r.warningCounts() == null)
                    ? Map.of()
                    : r.warningCounts().entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    e -> e.getKey().name(), Map.Entry::getValue));
            return new Disciplinary(
                    r == null ? 0 : r.fyCode(),
                    warningCounts,
                    open);
        }
    }
}
