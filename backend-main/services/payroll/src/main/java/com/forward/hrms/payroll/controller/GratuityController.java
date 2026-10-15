package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.response.GratuityDto;
import com.forward.hrms.payroll.service.GratuityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Gratuity calculator",
        description = "Read-only calculator. No stored balance, no caching — math is trivial. Requires `payroll.read`.")
@RestController
@RequestMapping("/gratuity/employees")
@RequiredArgsConstructor
public class GratuityController {

    private final GratuityService service;

    @Operation(summary = "Compute gratuity for an employee",
            description = "Requires `payroll.read`.")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public GratuityDto compute(@PathVariable("id") long employeeId,
                               @Parameter @RequestParam(required = false) LocalDate asOf) {
        return service.compute(employeeId, asOf);
    }
}
