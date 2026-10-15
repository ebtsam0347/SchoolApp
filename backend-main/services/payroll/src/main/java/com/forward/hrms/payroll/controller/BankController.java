package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.CreateBankRequest;
import com.forward.hrms.payroll.dto.request.UpdateBankRequest;
import com.forward.hrms.payroll.dto.response.BankDto;
import com.forward.hrms.payroll.service.BankService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Banks",
        description = "Bank catalog. Read for everyone; CRUD for HR/Accounts. " +
                "Requires `payroll.read` / `payroll.admin`.")
@RestController
@RequestMapping("/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankService banks;

    @Operation(summary = "List banks",
            description = "Active only by default. Pass `includeInactive=true` for the admin catalog UI. " +
                    "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public List<BankDto> list(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return banks.list(includeInactive);
    }

    @Operation(summary = "Get a bank", description = "Requires `payroll.read`.")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public BankDto get(@PathVariable long id) {
        return banks.get(id);
    }

    @Operation(summary = "Create a bank", description = "Requires `payroll.admin`.")
    @ApiResponse(responseCode = "409", description = "BANK_CODE_TAKEN.")
    @PostMapping
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public BankDto create(@Valid @RequestBody CreateBankRequest req) {
        return banks.create(req);
    }

    @Operation(summary = "Patch a bank",
            description = "Null fields stay unchanged. `code` is immutable. Requires `payroll.admin`.")
    @PatchMapping("/{id}")
    @RequirePermission("payroll.admin")
    public BankDto update(@PathVariable long id, @Valid @RequestBody UpdateBankRequest req) {
        return banks.update(id, req);
    }

    @Operation(summary = "Deactivate a bank",
            description = "Soft-delete — flips `active=false`. Existing employee_bank_details rows are " +
                    "preserved. Requires `payroll.admin`.")
    @DeleteMapping("/{id}")
    @RequirePermission("payroll.admin")
    public BankDto deactivate(@PathVariable long id) {
        return banks.deactivate(id);
    }
}
