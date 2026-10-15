package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.response.OffCyclePaymentDto;
import com.forward.hrms.payroll.service.OffCyclePaymentService;

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
import java.util.List;

@Tag(name = "Employee off-cycle payments",
        description = "Requires `payroll.read`.")
@RestController
@RequestMapping("/employees/{id}/off-cycle-payments")
@RequiredArgsConstructor
public class EmployeeOffCyclePaymentController {

    private final OffCyclePaymentService service;

    @Operation(summary = "Per-employee off-cycle payment list",
            description = "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public List<OffCyclePaymentDto> list(@PathVariable("id") long employeeId,
                                         @Parameter @RequestParam(required = false) LocalDate from,
                                         @Parameter @RequestParam(required = false) LocalDate to) {
        return service.listForEmployee(employeeId, from, to);
    }
}
