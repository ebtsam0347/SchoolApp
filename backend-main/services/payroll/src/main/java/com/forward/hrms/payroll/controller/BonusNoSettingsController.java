package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.CreateBonusNoSettingsRequest;
import com.forward.hrms.payroll.dto.request.UpdateBonusNoSettingsRequest;
import com.forward.hrms.payroll.dto.response.BonusNoSettingsDto;
import com.forward.hrms.payroll.dto.response.BonusTransactionDto;
import com.forward.hrms.payroll.enums.BonusNoSettingsStatus;
import com.forward.hrms.payroll.enums.BonusTransactionPaymentMode;
import com.forward.hrms.payroll.enums.BonusTransactionStatus;
import com.forward.hrms.payroll.service.BonusNoSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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

@Tag(name = "Bonus no settings (Bonus Calculation)",
        description = "Defines fiscal-year bonus rules and computes employee bonus transactions.")
@RestController
@RequestMapping("/bonuses/no-settings")
@RequiredArgsConstructor
public class BonusNoSettingsController {

    private final BonusNoSettingsService service;

    @Operation(summary = "Create bonus settings", description = "Requires `payroll.admin`.")
    @PostMapping
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public BonusNoSettingsDto create(@RequestBody CreateBonusNoSettingsRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Get one settings row", description = "Requires `payroll.read`.")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public BonusNoSettingsDto get(@PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "List settings rows", description = "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public List<BonusNoSettingsDto> list(
            @Parameter @RequestParam(required = false, name = "fiscalYear") String fiscalYearCode,
            @Parameter @RequestParam(required = false) BonusNoSettingsStatus status) {
        return service.list(fiscalYearCode, status);
    }

    @Operation(summary = "Patch settings row", description = "Requires `payroll.admin`.")
    @PatchMapping("/{id}")
    @RequirePermission("payroll.admin")
    public BonusNoSettingsDto update(@PathVariable long id,
                                     @RequestBody UpdateBonusNoSettingsRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Delete settings row", description = "Requires `payroll.admin`.")
    @DeleteMapping("/{id}")
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @Operation(summary = "Compute bonus transactions with saga",
            description = "Starts async compute for all employees. Requires `payroll.admin`.")
    @PostMapping("/{id}/compute")
    @RequirePermission("payroll.admin")
    public BonusNoSettingsDto compute(@PathVariable long id) {
        return service.compute(id);
    }

    @Operation(summary = "Reset computed transactions", description = "Requires `payroll.admin`.")
    @PostMapping("/{id}/reset")
    @RequirePermission("payroll.admin")
    public BonusNoSettingsDto reset(@PathVariable long id) {
        return service.reset(id);
    }

    @Operation(summary = "Approve Bonus Transaction", description = "Approve a Computed Bonus Transaction. Requires `payroll.admin`.")
    @PostMapping("/{id}/approve")
    @RequirePermission("payroll.admin")
    public BonusNoSettingsDto approve(@PathVariable long id){
        return service.approve(id);
    }

    @Operation(summary = "List computed transactions for bonus no settings",
            description = "Requires `payroll.read`.")
    @GetMapping("/{id}/transactions")
    @RequirePermission("payroll.read")
    public PageResponse<BonusTransactionDto> transactions(
            @PathVariable long id,
            @Parameter @RequestParam(required = false) BonusTransactionStatus status,
            @Parameter(description = "Filter by payment mode (CASH / BANK_TRANSFER).")
            @RequestParam(required = false) BonusTransactionPaymentMode paymentMode,
            @Parameter @RequestParam(required = false) String employeeCode,
            @Parameter(hidden = true) Pageable pageable) {
        return service.transactions(id, status, paymentMode, employeeCode,
                PageableDefaults.applyAscending(pageable, "employeeCode"));
    }
}
