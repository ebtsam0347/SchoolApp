package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.CreateFiscalYearRequest;
import com.forward.hrms.payroll.dto.request.UpdateFiscalYearRequest;
import com.forward.hrms.payroll.dto.response.FiscalYearDto;
import com.forward.hrms.payroll.service.FiscalYearService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Fiscal year",
        description = "Requires `payroll.read` / `payroll.admin`.")
@RestController
@RequestMapping("/fiscal-years")
@RequiredArgsConstructor
public class FiscalYearController {

    private final FiscalYearService service;

    @Operation(summary = "List all fiscal years (most recent first)",
            description = "Requires `nothing`.")
    @GetMapping
    public List<FiscalYearDto> list() {
        return service.list();
    }

    @Operation(summary = "Get the current fiscal year", description = "Requires `nothing`.")
    @GetMapping("/current")
    public FiscalYearDto current() {
        return service.current();
    }

    @Operation(summary = "Create a fiscal year", description = "Requires `payroll.admin`.")
    @PostMapping
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public FiscalYearDto create(@Valid @RequestBody CreateFiscalYearRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Mark closed / current / future", description = "Requires `payroll.admin`.")
    @PatchMapping("/{code}")
    @RequirePermission("payroll.admin")
    public FiscalYearDto setStatus(@PathVariable String code,
                                   @Valid @RequestBody UpdateFiscalYearRequest req) {
        return service.setStatus(code, req);
    }
}
