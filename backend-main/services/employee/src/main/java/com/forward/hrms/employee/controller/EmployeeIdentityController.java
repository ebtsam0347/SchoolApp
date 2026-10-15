package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.employee.dto.response.EmployeeIdentitySummary;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.service.StorageUrlService;
import com.forward.hrms.employee.dto.response.EmployeeIdentitySummaryMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Tag(name = "Employees — Identity Resolver",
        description = "Authenticated-only batch lookup keyed by user id. Returns profile-picture URL " +
                "+ display name only, so the BFF can render chat / mention chips without granting " +
                "`employee.read` to every viewer.")
@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeIdentityController {

    public static final int BY_USER_IDS_MAX = 200;

    private final EmployeeRepository employees;
    private final StorageUrlService urls;

    @Operation(summary = "Resolve userIds to employee identity",
            description = "Returns one row per linked employee, keyed by user_id. Users without a " +
                    "linked employee row (admin-only accounts) are silently dropped — caller " +
                    "falls back to the auth resolver's fullName. Cap: " + BY_USER_IDS_MAX + " ids per call.")
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "422", description = "Too many ids in a single request.")
    @GetMapping("/by-user-ids")
    public List<EmployeeIdentitySummary> byUserIds(
            @Parameter(description = "Comma-separated user ids; max " + BY_USER_IDS_MAX + " per call.")
            @RequestParam("userIds") List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        if (userIds.size() > BY_USER_IDS_MAX) {
            throw new UnprocessableError(CommonErrorCode.UNPROCESSABLE,
                    "too many userIds (max " + BY_USER_IDS_MAX + ")");
        }
        Set<Long> distinct = new HashSet<>(userIds);
        List<Employee> rows = employees.findAllByUserIdIn(distinct);
        return rows.stream()
                .map(e -> EmployeeIdentitySummaryMapper.of(e, urls.signAvatarGet(e.getProfilePictureKey())))
                .toList();
    }
}
