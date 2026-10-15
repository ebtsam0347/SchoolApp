package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.DecideDisciplinaryRequest;
import com.forward.hrms.employee.dto.request.IssueDisciplinaryRequest;
import com.forward.hrms.employee.dto.response.DisciplinaryActionDto;
import com.forward.hrms.employee.dto.response.DisciplinaryActionListResponse;
import com.forward.hrms.employee.enums.DisciplinaryCategory;
import com.forward.hrms.employee.enums.DisciplinaryLevel;
import com.forward.hrms.employee.enums.DisciplinaryStatus;
import com.forward.hrms.employee.service.DisciplinaryActionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Disciplinary Actions",
        description = "HR records warnings as flat rows; decisions (UPHELD / WITHDRAWN) close them. " +
                "Per-employee list returns computed FY warning counts (UPHELD only). " +
                "Requires `employee.read` / `employee.write`.")
@RestController
@RequiredArgsConstructor
@RequestMapping
public class DisciplinaryActionController {

    private final DisciplinaryActionService service;

    @Operation(summary = "Issue a disciplinary action against an employee", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Action recorded.")
    @ApiResponse(responseCode = "404", description = "Employee not found.")
    @PostMapping("/employees/{id}/disciplinary-actions")
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public DisciplinaryActionDto issue(@PathVariable long id,
                                       @Valid @RequestBody IssueDisciplinaryRequest req) {
        return service.issue(id, req);
    }

    @Operation(summary = "List an employee's disciplinary actions plus FY warning counts",
            description = "Counts in `warningCounts` are UPHELD-only and bounded to the requested fiscal year. Requires `employee.read`.")
    @GetMapping("/employees/{id}/disciplinary-actions")
    @RequirePermission("employee.read")
    public DisciplinaryActionListResponse listForEmployee(
            @PathVariable long id,
            @Parameter(description = "Filter rows by level.")
            @RequestParam(required = false) DisciplinaryLevel level,
            @Parameter(description = "Filter rows by status.")
            @RequestParam(required = false) DisciplinaryStatus status,
            @Parameter(description = "FY ending year. Defaults to the current FY.")
            @RequestParam(required = false) Integer fyCode) {
        return service.listForEmployee(id, level, status, fyCode);
    }

    @Operation(summary = "Decide an issued disciplinary action",
            description = "UPHELD rows count toward warning totals; WITHDRAWN rows do not. Requires `employee.write`.")
    @ApiResponse(responseCode = "404", description = "Action not found.")
    @ApiResponse(responseCode = "409", description = "Already decided.")
    @PostMapping("/disciplinary-actions/{id}/decide")
    @RequirePermission("employee.write")
    public DisciplinaryActionDto decide(@PathVariable long id,
                                        @Valid @RequestBody DecideDisciplinaryRequest req) {
        return service.decide(id, req);
    }

    @Operation(summary = "Global disciplinary list for HR review (paginated)",
            description = "Filters can be combined freely. `employeeId` scopes to a single employee " +
                    "(use `/employees/{id}/disciplinary-actions` instead if you also want UPHELD warning " +
                    "counts). `q` matches the owning employee's full name or employee code as a " +
                    "case-insensitive substring. Requires `employee.read`.")
    @GetMapping("/disciplinary-actions")
    @RequirePermission("employee.read")
    public PageResponse<DisciplinaryActionDto> globalList(
            @RequestParam(required = false) DisciplinaryLevel level,
            @RequestParam(required = false) DisciplinaryCategory category,
            @RequestParam(required = false) DisciplinaryStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "FY ending year — narrows issuedOn to the FY window when from/to are absent.")
            @RequestParam(required = false) Integer fyCode,
            @Parameter(description = "Scope to a single employee.")
            @RequestParam(required = false) Long employeeId,
            @Parameter(description = "Substring match across owning employee's name and code.")
            @RequestParam(required = false) String q,
            @Parameter(hidden = true) Pageable pageable) {
        return service.globalList(level, category, status, from, to, fyCode, employeeId, q,
                PageableDefaults.apply(pageable, "issuedOn"));
    }
}
