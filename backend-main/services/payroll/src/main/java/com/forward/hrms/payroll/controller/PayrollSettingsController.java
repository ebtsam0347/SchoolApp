package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.UpdateEmployeePayrollSettingsRequest;
import com.forward.hrms.payroll.dto.request.UpsertBankDetailsRequest;
import com.forward.hrms.payroll.dto.response.EmployeeBankDetailsDto;
import com.forward.hrms.payroll.dto.response.EmployeePayrollSettingsDto;
import com.forward.hrms.payroll.service.EmployeeBankDetailsService;
import com.forward.hrms.payroll.service.EmployeePayrollSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Per-employee payroll settings + bank details",
        description = "Requires `payroll.read` / `payroll.write`.")
@RestController
@RequestMapping("/employees/{id}")
@RequiredArgsConstructor
public class PayrollSettingsController {

    private final EmployeePayrollSettingsService settings;
    private final EmployeeBankDetailsService bankDetails;

    @Operation(summary = "Get payroll settings", description = "Requires `payroll.read`.")
    @GetMapping("/payroll-settings")
    @RequirePermission("payroll.read")
    public EmployeePayrollSettingsDto getSettings(@PathVariable("id") long employeeId) {
        return settings.get(employeeId);
    }

    @Operation(summary = "Update payroll settings (eobi enrollment, payment mode)",
            description = "Requires `payroll.write`.")
    @PatchMapping("/payroll-settings")
    @RequirePermission("payroll.write")
    public EmployeePayrollSettingsDto updateSettings(
            @PathVariable("id") long employeeId,
            @Valid @RequestBody UpdateEmployeePayrollSettingsRequest req) {
        return settings.update(employeeId, req);
    }

    @Operation(summary = "Get bank details (404 if none)", description = "Requires `payroll.read`.")
    @GetMapping("/bank-details")
    @RequirePermission("payroll.read")
    public EmployeeBankDetailsDto getBankDetails(@PathVariable("id") long employeeId) {
        return bankDetails.get(employeeId);
    }

    @Operation(summary = "Upsert bank details. Account number encrypted at rest.",
            description = "Requires `payroll.write`.")
    @PutMapping("/bank-details")
    @RequirePermission("payroll.write")
    public EmployeeBankDetailsDto putBankDetails(@PathVariable("id") long employeeId,
                                                 @Valid @RequestBody UpsertBankDetailsRequest req) {
        return bankDetails.upsert(employeeId, req);
    }

    @Operation(summary = "Delete bank details. Only allowed when payment_mode=CASH.",
            description = "Requires `payroll.write`.")
    @DeleteMapping("/bank-details")
    @RequirePermission("payroll.write")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBankDetails(@PathVariable("id") long employeeId) {
        bankDetails.remove(employeeId);
    }
}
