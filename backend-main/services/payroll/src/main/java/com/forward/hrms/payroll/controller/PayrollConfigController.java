package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.CreatePayrollConfigRequest;
import com.forward.hrms.payroll.dto.response.PayrollConfigDto;
import com.forward.hrms.payroll.service.PayrollConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Payroll config",
        description = "EOBI / PESSI / gratuity rates and canteen meal prices. Effective-dated. " +
                "Requires `payroll.read` / `payroll.admin`.")
@RestController
@RequestMapping("/payroll/config")
@RequiredArgsConstructor
public class PayrollConfigController {

    private final PayrollConfigService service;

    @Operation(summary = "Current effective row", description = "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public PayrollConfigDto current() {
        return service.current();
    }

    @Operation(summary = "Full history (most recent first)",
            description = "Requires `payroll.read`.")
    @GetMapping("/history")
    @RequirePermission("payroll.read")
    public List<PayrollConfigDto> history() {
        return service.history();
    }

    @Operation(summary = "Insert a new effective-dated row",
            description = "Closes the previous open-ended row at effectiveFrom-1. Requires `payroll.admin`.")
    @PostMapping
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollConfigDto create(@Valid @RequestBody CreatePayrollConfigRequest req) {
        return service.create(req);
    }
}
